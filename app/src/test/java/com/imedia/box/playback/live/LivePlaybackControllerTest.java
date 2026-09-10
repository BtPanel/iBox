package com.imedia.box.playback.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

import androidx.media3.common.MediaMetadata;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Result;
import com.imedia.box.live.LiveChannelStore;
import com.imedia.box.live.LiveHealthTestFixture;
import com.imedia.box.live.LiveLineHealth;
import com.imedia.box.live.LiveLineKey;
import com.imedia.box.playback.PlaybackResult;
import com.imedia.box.player.PlaybackFailure;
import com.imedia.box.player.media.MediaItemFactory;
import com.imedia.box.setting.LiveSetting;
import org.mockito.MockedStatic;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;

import org.junit.Test;

public class LivePlaybackControllerTest {

    @Test public void initiallyChoosesBestHealthyLine() {
        try (Harness h = new Harness()) {
            h.health.store.recordSuccess(h.channel, 2, 500);
            h.controller.selectChannel(h.channel);
            assertEquals(2, h.channel.getIndex());
            assertEquals(LiveConnectionState.Type.CONNECTING, h.host.lastState().type());
            assertEquals(2, h.host.lastState().line());
        }
    }

    @Test public void manualSelectionOverridesCooldownAndRefreshKeepsCurrentLine() {
        try (Harness h = new Harness()) {
            h.health.store.recordFailure(h.channel, 1, LiveFailureKind.NETWORK);
            h.controller.selectChannel(h.channel);
            h.controller.selectLine(1);
            h.controller.refresh();
            assertEquals(1, h.channel.getIndex());
            assertEquals(3, h.host.requests.size());
        }
    }

    @Test public void retriesOnceThenSwitchesToBestRemainingLine() {
        try (Harness h = new Harness()) {
            h.health.store.recordSuccess(h.channel, 2, 500);
            h.controller.selectChannel(h.channel);
            h.controller.selectLine(0);
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            assertEquals(LiveConnectionState.Type.RECONNECTING, h.host.lastState().type());
            assertEquals(3, h.host.lastState().delaySeconds());
            assertEquals(0, h.lineHealth(0).failureCount());
            assertEquals(3_000L, h.scheduler.delays.get(0).longValue());
            h.scheduler.runNext();
            assertEquals(0, h.channel.getIndex());
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            assertEquals(2, h.channel.getIndex());
            assertEquals(1, h.lineHealth(0).failureCount());
            assertTrue(h.host.states.stream().anyMatch(s -> s.type() == LiveConnectionState.Type.SWITCHING && s.fromLine() == 0 && s.line() == 2));
        }
    }

    @Test public void exhaustsLinesWithoutRevisitsAndMarksChannelOnlyAtEnd() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.playbackError("bad source");
            assertEquals(1, h.channel.getIndex());
            assertFalse(LiveChannelStore.isFailed(h.channel));
            h.controller.playbackError("bad source");
            assertEquals(2, h.channel.getIndex());
            h.controller.playbackError("bad source");
            assertEquals(3, h.host.requests.size());
            assertTrue(LiveChannelStore.isFailed(h.channel));
            assertEquals(LiveConnectionState.Type.FAILED, h.host.lastState().type());
            assertEquals(List.of("FAILED", "reset"), h.host.events.subList(h.host.events.size() - 2, h.host.events.size()));
            assertEquals(1, h.lineHealth(0).failureCount());
            assertEquals(1, h.lineHealth(1).failureCount());
            assertEquals(1, h.lineHealth(2).failureCount());
            assertTrue(h.scheduler.pending.isEmpty());
        }
    }

    @Test public void stablePlaybackClearsFailureOnlyAfterThirtySecondsAndRecordsStartupTime() {
        try (Harness h = new Harness()) {
            LiveChannelStore.markFailed(h.channel);
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            h.now += 800;
            h.controller.onPlaybackReady();
            assertEquals(LiveConnectionState.Type.PLAYING, h.host.lastState().type());
            assertEquals(30_000L, h.scheduler.delays.get(0).longValue());
            assertTrue(LiveChannelStore.isFailed(h.channel));
            assertEquals(0, h.lineHealth(0).successCount());
            h.now += 30_000;
            h.scheduler.runNext();
            assertFalse(LiveChannelStore.isFailed(h.channel));
            assertEquals(1, h.lineHealth(0).successCount());
            assertEquals(800, h.lineHealth(0).averageStartupMs());
            assertEquals(1, h.host.statusRenders);
        }
    }

    @Test public void shortReadyDoesNotClearFailureOrResetRetryBudget() {
        try (Harness h = new Harness()) {
            LiveChannelStore.markFailed(h.channel);
            h.controller.selectChannel(h.channel);
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            h.scheduler.runNext();
            h.resolveLatest();
            h.controller.onPlaybackReady();
            Runnable obsoleteSuccess = h.scheduler.pending.get(0);
            h.now += 29_999;
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            obsoleteSuccess.run();
            assertEquals(1, h.channel.getIndex());
            assertEquals(0, h.lineHealth(0).successCount());
            assertTrue(LiveChannelStore.isFailed(h.channel));
            assertTrue(h.scheduler.pending.isEmpty());
        }
    }

    @Test public void refreshCancelsRetryAndRejectsItsAlreadyDequeuedRunnable() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            Runnable obsolete = h.scheduler.pending.get(0);
            h.controller.refresh();
            obsolete.run();
            assertEquals(2, h.host.requests.size());
            assertTrue(h.scheduler.pending.isEmpty());
        }
    }

    @Test public void channelLineAndResetCancelBothKindsOfDelayedWork() {
        for (int action = 0; action < 3; action++) {
            for (boolean ready : List.of(false, true)) {
                try (Harness h = new Harness()) {
                    h.controller.selectChannel(h.channel);
                    if (ready) { h.resolveLatest(); h.controller.onPlaybackReady(); }
                    else h.controller.playbackError(PlaybackFailure.timeout("timeout"));
                    Runnable obsolete = h.scheduler.pending.get(0);
                    if (action == 0) h.controller.selectChannel(h.other);
                    else if (action == 1) h.controller.selectLine(1);
                    else h.controller.reset();
                    int requests = h.host.requests.size();
                    obsolete.run();
                    assertEquals(requests, h.host.requests.size());
                    assertTrue(h.scheduler.pending.isEmpty());
                    assertEquals(0, h.lineHealth(0).successCount());
                }
            }
        }
    }

    @Test public void rejectsOldResultForSameChannelAndLineAfterRefresh() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            LivePlayRequest old = h.host.latestRequest();
            h.controller.refresh();
            assertNotEquals(old.getRequestId(), h.host.latestRequest().getRequestId());
            assertFalse(old.matches(h.host.latestRequest()));
            h.controller.onPlaybackResult(new PlaybackResult<>(old, h.result));
            assertEquals(0, h.host.starts);
            h.resolveLatest();
            assertEquals(1, h.host.starts);
        }
    }

    @Test public void rejectsOldResultAfterChannelAndLineChanges() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            LivePlayRequest old = h.host.latestRequest();
            h.controller.selectLine(1);
            h.controller.onPlaybackResult(new PlaybackResult<>(old, h.result));
            h.controller.selectChannel(h.other);
            h.controller.onPlaybackResult(new PlaybackResult<>(old, h.result));
            assertEquals(0, h.host.starts);
        }
    }

    @Test public void catchupFailureNeverSwitchesOrWritesLiveHealthOrChannelFailure() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.selectEpg(h.program);
            h.controller.playbackError(PlaybackFailure.timeout("catchup timeout"));
            assertEquals(0, h.channel.getIndex());
            assertEquals(2, h.host.requests.size());
            assertTrue(h.health.store.current().isEmpty());
            assertFalse(LiveChannelStore.isFailed(h.channel));
            assertTrue(h.scheduler.pending.isEmpty());
            assertEquals(LiveConnectionState.Type.FAILED, h.host.lastState().type());
        }
    }

    @Test public void catchupReadyAndRefreshStayIsolatedAndCancelLiveSuccess() {
        try (Harness h = new Harness()) {
            LiveChannelStore.markFailed(h.channel);
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            h.controller.onPlaybackReady();
            Runnable obsolete = h.scheduler.pending.get(0);
            h.controller.selectEpg(h.program);
            obsolete.run();
            h.resolveLatest();
            h.controller.onPlaybackReady();
            h.controller.refresh();
            assertTrue(h.host.latestRequest().isCatchup());
            assertTrue(h.scheduler.pending.isEmpty());
            assertTrue(h.health.store.current().isEmpty());
            assertTrue(LiveChannelStore.isFailed(h.channel));
        }
    }

    @Test public void ambiguousChannelStillPlaysAndRecoversWithoutHealthData() {
        try (Harness h = new Harness()) {
            Group recent = mock(Group.class);
            when(recent.getName()).thenReturn("Recent");
            when(recent.isRecent()).thenReturn(true);
            Group duplicate = h.group("Duplicate");
            Channel copy = h.channel("News", duplicate);
            when(duplicate.getChannel()).thenReturn(List.of(copy));
            h.groups.add(duplicate);
            h.channel.setGroup(recent);
            h.controller.selectChannel(h.channel);
            h.controller.playbackError("source failure");
            assertEquals(1, h.channel.getIndex());
            assertTrue(h.health.store.current().isEmpty());
        }
    }

    private static final class Harness implements AutoCloseable {
        long now = 8_640_000_000L;
        final FakeScheduler scheduler = new FakeScheduler();
        final FakeHost host = new FakeHost();
        final MockedStatic<LiveConfig> configMock = mockStatic(LiveConfig.class);
        final MockedStatic<LiveSetting> settings = mockStatic(LiveSetting.class);
        final MockedStatic<MediaItemFactory> metadata = mockStatic(MediaItemFactory.class);
        final LiveHealthTestFixture health = new LiveHealthTestFixture(() -> now);
        final List<Group> groups = new ArrayList<>();
        final Group group = group("News");
        final Channel channel = channel("News", group);
        final Channel other = channel("Other", group);
        final EpgData program = mock(EpgData.class);
        final Result result = mock(Result.class);
        final LivePlaybackController controller;

        Harness() {
            LiveConfig config = mock(LiveConfig.class);
            Live home = mock(Live.class);
            Config source = mock(Config.class);
            configMock.when(LiveConfig::get).thenReturn(config);
            when(config.getHome()).thenReturn(home);
            when(config.getConfig()).thenReturn(source);
            when(home.getName()).thenReturn("Source");
            when(source.getUrl()).thenReturn("https://source");
            groups.add(group);
            when(home.getGroups()).thenReturn(groups);
            when(group.getChannel()).thenReturn(List.of(channel, other));
            settings.when(LiveSetting::isReconnect).thenReturn(true);
            settings.when(LiveSetting::isChange).thenReturn(true);
            settings.when(LiveSetting::getReconnectDelaySeconds).thenReturn(3);
            when(program.format()).thenReturn("");
            when(program.getTitle()).thenReturn("");
            when(result.getRealUrl()).thenReturn("https://resolved");
            host.groups = groups;
            controller = new LivePlaybackController(host, new LivePlaybackState(), health.store,
                    new LiveRecoveryPolicy(), scheduler, () -> now);
        }

        Group group(String name) {
            Group value = mock(Group.class);
            when(value.getName()).thenReturn(name);
            return value;
        }

        Channel channel(String name, Group group) {
            Channel value = spy(new Channel());
            doReturn(name).when(value).getName();
            doReturn(name).when(value).getShow();
            doReturn("").when(value).getNumber();
            doReturn("").when(value).getLogo();
            doReturn(true).when(value).hasCatchup();
            doReturn(new Epg()).when(value).getData(any(ZoneId.class));
            value.setGroup(group);
            value.setUrls(List.of("https://zero", "https://one", "https://two"));
            return value;
        }

        void resolveLatest() { controller.onPlaybackResult(new PlaybackResult<>(host.latestRequest(), result)); }
        LiveLineHealth lineHealth(int line) {
            return health.store.current().getOrDefault(LiveLineKey.create("test", "News", "News",
                    channel.getUrls().get(line)), LiveLineHealth.empty());
        }
        @Override public void close() {
            health.close();
            metadata.close();
            settings.close();
            configMock.close();
        }
    }

    private static final class FakeScheduler implements LivePlaybackScheduler {
        final List<Runnable> pending = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        @Override public void post(Runnable runnable, long delayMs) { pending.add(runnable); delays.add(delayMs); }
        @Override public void remove(Runnable runnable) {
            int index = pending.indexOf(runnable);
            if (index >= 0) { pending.remove(index); delays.remove(index); }
        }
        void runNext() { delays.remove(0); pending.remove(0).run(); }
    }

    private static final class FakeHost implements LivePlaybackHost {
        final List<LiveConnectionState> states = new ArrayList<>();
        final List<LivePlayRequest> requests = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        List<Group> groups;
        int starts;
        int statusRenders;
        LiveConnectionState lastState() { return states.get(states.size() - 1); }
        LivePlayRequest latestRequest() { return requests.get(requests.size() - 1); }
        @Override public void renderConnectionState(LiveConnectionState state) { states.add(state); events.add(state.type().name()); }
        @Override public void requestUrl(LivePlayRequest request) { requests.add(request); }
        @Override public int getGroupCount() { return groups.size(); }
        @Override public int getGroupPosition() { return 0; }
        @Override public Group getGroup(int position) { return groups.get(position); }
        @Override public boolean isPlayerLive() { return true; }
        @Override public boolean hasPlaybackSession() { return false; }
        @Override public boolean isPlaybackServiceReady() { return true; }
        @Override public void restorePlaybackKey(String key) { }
        @Override public long getPlayerPosition() { return 0; }
        @Override public ZoneId getZoneId() { return ZoneId.of("UTC"); }
        @Override public void stopPlaybackForRefresh() { }
        @Override public void startPlayback(Result result, long position, MediaMetadata metadata) { starts++; }
        @Override public void resetPlaybackForError(String msg) { events.add("reset"); }
        @Override public void renderGroupSelection(Group group) { }
        @Override public void renderGroupChannels(Group group) { }
        @Override public void renderChannelSelection(Channel channel) { }
        @Override public void renderChannelStatus(Channel channel) { statusRenders++; }
        @Override public void renderLineSelection(Channel channel, boolean show) { }
        @Override public void renderEpgSelection(EpgData data) { }
        @Override public void renderPlaybackMetadata(MediaMetadata metadata) { }
        @Override public void showCatchupReady(EpgData data) { }
        @Override public void showProgress() { }
    }

    @Test
    public void resumesOnlyTheSameActiveCatchupRequest() {
        Channel channel = new Channel();
        EpgData active = new EpgData();
        EpgData other = new EpgData();
        LivePlayRequest live = LivePlayRequest.live(channel, 160_000);
        LivePlayRequest catchup = LivePlayRequest.catchup(channel, active, 0);

        assertFalse(LivePlaybackController.isActiveCatchup(live, active));
        assertFalse(LivePlaybackController.isActiveCatchup(catchup, other));
        assertTrue(LivePlaybackController.isActiveCatchup(catchup, active));
    }

    @Test
    public void stateTracksPendingAndPlayingCatchupRequests() {
        LivePlaybackState state = new LivePlaybackState();
        Channel channel = new Channel();
        LivePlayRequest live = LivePlayRequest.live(channel, 0);
        LivePlayRequest catchup = LivePlayRequest.catchup(channel, new EpgData(), 0);

        assertFalse(state.isCatchup());
        state.setPendingRequest(live);
        assertFalse(state.isCatchup());
        state.setPendingRequest(catchup);
        assertTrue(state.isCatchup());
        state.setPlayingRequest(catchup, "catchup");
        assertTrue(state.isCatchup());
        state.clearPlayback();
        assertFalse(state.isCatchup());
    }
}
