package com.imedia.box.playback.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
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
import com.imedia.box.live.LiveLineHealthStore;
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
    @Test public void unresolvedAutoSwitchRemainsTheFinalVisibleStateAcrossRecreation() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            assertEquals(LiveConnectionState.Type.CONNECTING, h.host.lastState().type());
            h.controller.playbackError(PlaybackFailure.source("bad source"));
            assertEquals(LiveConnectionState.Type.SWITCHING, h.host.lastState().type());
            assertEquals(0, h.host.lastState().fromLine());
            assertEquals(1, h.host.lastState().line());
            h.controller.detach();
            LivePlaybackController restored = new LivePlaybackController(h.host, h.state, h.health.store,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);
            restored.selectChannel(h.channel);
            assertEquals(LiveConnectionState.Type.SWITCHING, h.host.lastState().type());
            restored.playbackError(PlaybackFailure.timeout("timeout"));
            assertEquals(LiveConnectionState.Type.RECONNECTING, h.host.lastState().type());
            h.now += 3_000;
            h.scheduler.runNext();
            assertEquals(LiveConnectionState.Type.CONNECTING, h.host.lastState().type());
            assertEquals(1, h.host.lastState().line());
        }
    }

    @Test public void terminalCatchupFailureKeepsProgramAndPositionForRefreshWithoutAcceptingOldEvents() {
        for (boolean resolved : List.of(false, true)) {
            try (Harness h = new Harness()) {
                h.controller.selectChannel(h.channel);
                h.controller.selectEpg(h.program);
                h.controller.selectEpg(h.program, 42_000);
                LivePlayRequest failed = h.host.latestRequest();
                if (resolved) {
                    h.resolveLatest();
                    h.host.hasSession = true;
                    h.host.position = 64_000;
                }
                h.controller.playbackError(PlaybackFailure.timeout("catchup timeout"));
                assertTrue(h.controller.isCatchup());
                assertFalse(h.controller.isCurrentPlayback(failed));
                h.controller.onPlaybackReady(failed);
                h.controller.playbackError(PlaybackFailure.source("old error"), failed);
                h.controller.onPlaybackResult(new PlaybackResult<>(failed, h.result));
                assertEquals(LiveConnectionState.Type.FAILED, h.host.lastState().type());
                h.controller.refresh();
                LivePlayRequest retried = h.host.latestRequest();
                assertTrue(retried.isCatchup());
                assertEquals(h.program, retried.getCatchupData());
                assertEquals(resolved ? 64_000 : 42_000, retried.getPosition());
                assertNotEquals(failed.getRequestId(), retried.getRequestId());
                assertTrue(h.health.store.current().isEmpty());
                assertTrue(h.scheduler.pending.isEmpty());
            }
        }
    }

    @Test public void recreationResumesRemainingRetryDelayAndPreservesExhaustedLineBudget() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.playbackError(PlaybackFailure.source("line zero failed"));
            h.resolveLatest();
            LivePlayRequest failed = h.host.latestRequest();
            h.host.hasSession = true; // A stopped player can still retain its media item.
            h.controller.playbackError(PlaybackFailure.timeout("line one timeout"), failed);
            Runnable obsoleteRetry = h.scheduler.pending.get(0);
            h.now += 1_000;
            h.controller.detach();
            LivePlaybackController restored = new LivePlaybackController(h.host, h.state, h.health.store,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);
            assertFalse(restored.isCurrentPlayback(failed));
            restored.selectChannel(h.channel);
            restored.onPlaybackServiceReady();
            assertEquals(2, h.host.requests.size());
            assertEquals(LiveConnectionState.Type.RECONNECTING, h.host.lastState().type());
            assertEquals(2, h.host.lastState().delaySeconds());
            assertEquals(List.of(2_000L), h.scheduler.delays);
            obsoleteRetry.run();
            h.controller.onPlaybackReady(failed);
            h.controller.playbackError(PlaybackFailure.source("detached error"), failed);
            assertEquals(2, h.host.requests.size());
            h.now += 2_000;
            h.scheduler.runNext();
            assertEquals(1, h.channel.getIndex());
            restored.onPlaybackResult(new PlaybackResult<>(h.host.latestRequest(), h.result));
            restored.playbackError(PlaybackFailure.timeout("retry exhausted"), h.host.latestRequest());
            assertEquals(2, h.channel.getIndex());
            assertTrue(h.scheduler.pending.isEmpty());
            restored.playbackError(PlaybackFailure.source("last line failed"));
            assertEquals(LiveConnectionState.Type.FAILED, h.host.lastState().type());
            assertEquals(4, h.host.requests.size());
            assertEquals(1, h.lineHealth(0).failureCount());
            assertEquals(1, h.lineHealth(1).failureCount());
        }
    }

    @Test public void recreationAfterRetryDeadlineSchedulesImmediateRemainingAttempt() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.playbackError(PlaybackFailure.timeout("timeout"));
            h.controller.detach();
            h.now += 4_000;
            LivePlaybackController restored = new LivePlaybackController(h.host, h.state, h.health.store,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);
            restored.onPlaybackServiceReady();
            assertEquals(List.of(0L), h.scheduler.delays);
            h.scheduler.runNext();
            assertEquals(2, h.host.requests.size());
            restored.playbackError(PlaybackFailure.timeout("retry exhausted"));
            assertEquals(1, h.channel.getIndex());
        }
    }

    @Test public void bufferingPauseAndBackgroundRequireANewContinuousStableWindow() {
        for (int interruption = 0; interruption < 3; interruption++) {
            try (Harness h = new Harness()) {
                h.controller.selectChannel(h.channel);
                h.resolveLatest();
                LivePlayRequest request = h.host.latestRequest();
                h.now += 800;
                h.controller.onPlaybackStateChanged(request, Player.STATE_READY, true);
                Runnable oldWindow = h.scheduler.pending.get(0);
                h.now += 29_000;
                if (interruption == 0) h.controller.onPlaybackStateChanged(request, Player.STATE_BUFFERING, false);
                else if (interruption == 1) h.controller.onPlaybackPlayingChanged(request, false);
                else h.controller.onPlaybackBackgrounded();
                assertTrue(h.scheduler.pending.isEmpty());
                h.now += 60_000;
                oldWindow.run();
                assertEquals(0, h.lineHealth(0).successCount());
                h.controller.onPlaybackForegrounded();
                h.controller.onPlaybackStateChanged(request, Player.STATE_READY, false);
                assertTrue(h.scheduler.pending.isEmpty());
                h.controller.onPlaybackPlayingChanged(request, true);
                assertEquals(30_000L, h.scheduler.delays.get(0).longValue());
                h.now += 30_000;
                h.scheduler.runNext();
                assertEquals(1, h.lineHealth(0).successCount());
                assertEquals(800, h.lineHealth(0).averageStartupMs());
            }
        }
    }

    @Test public void stalePlaybackStateCannotCancelTheCurrentStableWindow() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            LivePlayRequest old = h.host.latestRequest();
            h.controller.refresh();
            h.resolveLatest();
            h.controller.onPlaybackStateChanged(h.host.latestRequest(), Player.STATE_READY, true);
            Runnable currentWindow = h.scheduler.pending.get(0);
            h.controller.onPlaybackStateChanged(old, Player.STATE_BUFFERING, false);
            h.controller.onPlaybackPlayingChanged(old, false);
            assertEquals(List.of(currentWindow), h.scheduler.pending);
        }
    }

    @Test public void repeatedReadyRecordsOneSuccessWithFirstStartupSampleAcrossRecreation() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            h.now += 800;
            h.controller.onPlaybackReady();
            h.now += 30_000;
            h.scheduler.runNext();
            h.now += 180_000;
            h.controller.onPlaybackReady();
            assertTrue(h.scheduler.pending.isEmpty());
            h.controller.detach();
            h.host.hasSession = true;
            LivePlaybackController restored = new LivePlaybackController(h.host, h.state, h.health.store,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);
            restored.selectChannel(h.channel);
            restored.onPlaybackReady(h.host.latestRequest());
            assertTrue(h.scheduler.pending.isEmpty());
            assertEquals(1, h.lineHealth(0).successCount());
            assertEquals(800, h.lineHealth(0).averageStartupMs());
        }
    }


    @Test public void stalePlayerErrorAndReadyCannotAffectRefreshedPlayback() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            LivePlayRequest old = h.host.latestRequest();
            h.controller.refresh();
            h.controller.playbackError(PlaybackFailure.timeout("old error"), old);
            h.controller.onPlaybackReady(old);
            assertTrue(h.scheduler.pending.isEmpty());
            h.resolveLatest();
            h.controller.playbackError(PlaybackFailure.source("old source"), old);
            h.controller.onPlaybackReady(old);
            assertEquals(0, h.channel.getIndex());
            assertTrue(h.scheduler.pending.isEmpty());
            assertTrue(h.health.store.current().isEmpty());
            h.controller.onPlaybackReady(h.host.latestRequest());
            assertEquals(1, h.scheduler.pending.size());
        }
    }

    @Test public void stalePlayerEventsAfterAutoSwitchAndOldResolverFailureAreRejected() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.resolveLatest();
            LivePlayRequest old = h.host.latestRequest();
            h.controller.playbackError(PlaybackFailure.source("bad source"), old);
            assertEquals(1, h.channel.getIndex());
            h.resolveLatest();
            h.controller.playbackError(PlaybackFailure.source("late failure"), old);
            h.controller.onPlaybackReady(old);
            Result badResult = mock(Result.class);
            when(badResult.hasMsg()).thenReturn(true);
            when(badResult.getMsg()).thenReturn("old resolver");
            h.controller.onPlaybackResult(new PlaybackResult<>(old, badResult));
            assertEquals(1, h.channel.getIndex());
            assertEquals(2, h.host.requests.size());
            assertTrue(h.scheduler.pending.isEmpty());
            assertEquals(0, h.lineHealth(1).failureCount());
        }
    }

    @Test public void sourceChangeReloadsRankingAndWritesOnlyTheNewSourcesHealth() {
        try (Harness h = new Harness()) {
            LiveLineHealthStore sourceB = h.health.storeForSource("source-b", () -> h.now);
            h.health.store.recordSuccess(h.channel, 2, 100);
            sourceB.recordSuccess(h.channel, 1, 200);
            LivePlaybackController controller = new LivePlaybackController(h.host, new LivePlaybackState(),
                    () -> h.source.getUrl().equals("https://source") ? h.health.store : sourceB,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);
            controller.selectChannel(h.channel);
            assertEquals(2, h.channel.getIndex());
            controller.onPlaybackResult(new PlaybackResult<>(h.host.latestRequest(), h.result));
            controller.onPlaybackReady();
            Runnable oldSuccess = h.scheduler.pending.get(0);
            h.host.hasSession = true;

            when(h.source.getUrl()).thenReturn("https://source-b");
            controller.selectChannel(h.channel);
            oldSuccess.run();

            assertEquals(1, h.channel.getIndex());
            controller.playbackError("source failure");
            controller.onPlaybackResult(new PlaybackResult<>(h.host.latestRequest(), h.result));
            controller.onPlaybackReady();
            h.now += 30_000;
            h.scheduler.runNext();
            assertEquals(1, h.health.store.current().size());
            assertEquals(1, h.lineHealth(2).successCount());
            assertEquals(0, h.lineHealth(1).failureCount());
            assertEquals(1, sourceB.current().get(LiveLineKey.create("source-b", "News", "News", "https://one")).failureCount());
            assertEquals(1, sourceB.current().get(LiveLineKey.create("source-b", "News", "News", "https://zero")).successCount());
        }
    }

    @Test public void retainingExistingSessionPreservesManualLineAndStableSuccess() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.selectLine(2);
            h.resolveLatest();
            h.controller.onPlaybackReady();
            h.host.hasSession = true;
            LivePlayRequest playing = h.host.latestRequest();
            Runnable success = h.scheduler.pending.get(0);

            h.controller.selectChannel(h.channel);

            assertEquals(2, h.channel.getIndex());
            assertEquals(playing, h.host.latestRequest());
            assertEquals(List.of(success), h.scheduler.pending);
            h.now += 30_000;
            h.scheduler.runNext();
            assertEquals(1, h.lineHealth(2).successCount());
        }
    }

    @Test public void retainingExistingSessionPreservesCatchupOnManualLine() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.selectLine(2);
            h.controller.selectEpg(h.program);
            h.resolveLatest();
            h.host.hasSession = true;
            LivePlayRequest playing = h.host.latestRequest();

            h.controller.selectChannel(h.channel);

            assertEquals(2, h.channel.getIndex());
            assertEquals(playing, h.host.latestRequest());
            assertTrue(h.controller.isCatchup());
            assertTrue(h.scheduler.pending.isEmpty());
        }
    }

    @Test public void newControllerRetainsSavedPlayingRequestAndAcceptsItsReadyEvent() {
        try (Harness h = new Harness()) {
            h.controller.selectChannel(h.channel);
            h.controller.selectLine(2);
            h.resolveLatest();
            h.controller.onPlaybackReady();
            Runnable oldSuccess = h.scheduler.pending.get(0);
            h.controller.detach();
            assertTrue(h.scheduler.pending.isEmpty());
            h.host.hasSession = true;
            LivePlayRequest playing = h.host.latestRequest();
            LivePlaybackController restored = new LivePlaybackController(h.host, h.state, h.health.store,
                    new LiveRecoveryPolicy(), h.scheduler, () -> h.now);

            restored.selectChannel(h.channel);
            restored.onPlaybackReady(playing);
            oldSuccess.run();

            assertEquals(2, h.channel.getIndex());
            assertEquals(playing, h.host.latestRequest());
            assertEquals(1, h.scheduler.pending.size());
            assertEquals(0, h.lineHealth(2).successCount());
        }
    }

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
        final Config source = mock(Config.class);
        final LivePlaybackState state = new LivePlaybackState();
        final LivePlaybackController controller;

        Harness() {
            LiveConfig config = mock(LiveConfig.class);
            Live home = mock(Live.class);
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
            controller = new LivePlaybackController(host, state, health.store,
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
        boolean hasSession;
        long position;
        LiveConnectionState lastState() { return states.get(states.size() - 1); }
        LivePlayRequest latestRequest() { return requests.get(requests.size() - 1); }
        @Override public void renderConnectionState(LiveConnectionState state) { states.add(state); events.add(state.type().name()); }
        @Override public void requestUrl(LivePlayRequest request) { requests.add(request); }
        @Override public int getGroupCount() { return groups.size(); }
        @Override public int getGroupPosition() { return 0; }
        @Override public Group getGroup(int position) { return groups.get(position); }
        @Override public boolean isPlayerLive() { return true; }
        @Override public boolean hasPlaybackSession() { return hasSession; }
        @Override public boolean isPlaybackServiceReady() { return true; }
        @Override public void restorePlaybackKey(String key) { }
        @Override public long getPlayerPosition() { return position; }
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
