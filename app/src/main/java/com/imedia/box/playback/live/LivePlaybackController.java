package com.imedia.box.playback.live;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.R;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Result;
import com.imedia.box.live.LiveChannelStore;
import com.imedia.box.live.LiveLineHealthStore;
import com.imedia.box.playback.PlaybackResult;
import com.imedia.box.player.PlaybackFailure;
import com.imedia.box.player.media.MediaItemFactory;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.ResUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class LivePlaybackController {

    private static final long STABLE_PLAYBACK_DURATION_MS = 30_000;

    private final LivePlaybackState state;
    private final LivePlaybackHost host;
    private final Supplier<LiveLineHealthStore> healthStoreFactory;
    private LiveLineHealthStore healthStore;
    private String healthSource;
    private final LiveRecoveryPolicy recoveryPolicy;
    private final LivePlaybackScheduler scheduler;
    private final LongSupplier clock;
    private final Set<Integer> attemptedLines = new LinkedHashSet<>();
    private long requestSequence;
    private long recoveryGeneration;
    private long connectStartedAtMs;
    private boolean retriedCurrent;
    private Runnable pendingRecovery;
    private Runnable stablePlayback;

    public LivePlaybackController(LivePlaybackHost host, LivePlaybackState state) {
        this(host, state, LiveLineHealthStore::new, new LiveRecoveryPolicy(),
                LivePlaybackScheduler.app(), System::currentTimeMillis);
    }

    LivePlaybackController(LivePlaybackHost host, LivePlaybackState state,
                           LiveLineHealthStore healthStore, LiveRecoveryPolicy recoveryPolicy,
                           LivePlaybackScheduler scheduler, LongSupplier clock) {
        this(host, state, () -> healthStore, recoveryPolicy, scheduler, clock);
    }

    LivePlaybackController(LivePlaybackHost host, LivePlaybackState state,
                           Supplier<LiveLineHealthStore> healthStoreFactory, LiveRecoveryPolicy recoveryPolicy,
                           LivePlaybackScheduler scheduler, LongSupplier clock) {
        this.state = state;
        this.host = host;
        this.healthStoreFactory = healthStoreFactory;
        this.recoveryPolicy = recoveryPolicy;
        this.scheduler = scheduler;
        this.clock = clock;
        this.healthSource = LiveLineHealthStore.currentSourceKey();
        this.healthStore = healthStoreFactory.get();
        this.connectStartedAtMs = clock.getAsLong();
        LivePlayRequest active = state.getActiveRequest();
        this.requestSequence = active == null ? 0 : active.getRequestId();
    }

    public void reset() {
        beginCycle(false, false);
        state.reset();
    }

    public void detach() {
        cancelPendingRecovery();
    }

    public boolean isCatchup() {
        return state.isCatchup();
    }

    public void selectGroup(Group group) {
        state.setGroup(group);
        host.renderGroupSelection(group);
        host.renderGroupChannels(group);
    }

    public void selectChannel(Channel channel) {
        if (channel == null) return;
        bindHealthStore();
        LivePlayRequest activeRequest = state.getActiveRequest();
        if (activeRequest != null && activeRequest == state.getPlayingRequest()
                && activeRequest.matches(channel) && host.hasPlaybackSession()) {
            state.setChannel(channel);
            host.renderChannelSelection(channel);
            return;
        }
        state.setChannel(channel);
        beginCycle(false, true);
        boolean restore = activeRequest != null && activeRequest.matches(channel);
        host.renderChannelSelection(channel);
        if (restore) restorePlaybackIfNeeded();
        else {
            state.clearPlayback();
            requestLiveInCycle();
        }
    }

    public boolean selectEpg(EpgData data) {
        return selectEpg(data, C.TIME_UNSET);
    }

    public boolean selectEpg(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null || data == null) return false;
        if (!channel.hasCatchup() && !channel.isRtsp()) return false;
        if (isActiveCatchup(state.getActiveRequest(), data)) return requestCatchup(data, startPositionMs);
        host.showCatchupReady(data);
        host.renderEpgSelection(data);
        return requestCatchup(data, C.TIME_UNSET);
    }

    public void onPlaybackResult(PlaybackResult<LivePlayRequest> playback) {
        if (playback == null || cannotApply(playback)) return;
        applyPlaybackResult(playback.result(), playback.request());
    }

    private void applyPlaybackResult(Result result, LivePlayRequest request) {
        String realUrl = result.getRealUrl();
        if (result.hasMsg() || realUrl == null || realUrl.isEmpty()) handleFailure(PlaybackFailure.source(result.getMsg()), request);
        else startResolvedPlayback(result, request, realUrl);
    }

    private void startResolvedPlayback(Result result, LivePlayRequest request, String realUrl) {
        long position = result.hasPosition() ? result.getPosition() : request.getPosition();
        state.setPlayingRequest(request, realUrl);
        host.startPlayback(result, position, publishPlaybackMetadata(getEpgData(request)), request);
    }

    public void playbackError(String msg) {
        playbackError(PlaybackFailure.source(msg));
    }

    public void playbackError(PlaybackFailure failure) {
        handleFailure(failure, state.getActiveRequest());
    }

    private void handleFailure(PlaybackFailure failure, LivePlayRequest request) {
        if (request == null || !request.matches(state.getChannel()) || pendingRecovery != null) return;
        if (!Objects.equals(healthSource, LiveLineHealthStore.currentSourceKey())) return;
        if (failure == null) failure = new PlaybackFailure(PlaybackFailure.Kind.PLAYBACK, "", 0);
        if (request.isCatchup()) applyDecision(LiveRecoveryDecision.fail(), failure, request);
        else handleLiveFailure(failure, request);
    }

    public void playbackEnded() {
        LivePlayRequest request = state.getActiveRequest();
        if (request != null && request.isCatchup()) {
            if (LiveSetting.getTimeshiftEndMode() == 1) requestLive();
            else if (!playNextCatchupProgram()) requestLive();
            return;
        }
        if (host.isPlayerLive()) playNextProgram();
        else nextChannel();
    }

    public void onEpgChanged(EpgData data) {
        if (state.getChannel() != null) publishPlaybackMetadata(data);
    }

    public void refresh() {
        beginCycle(true, false);
        refreshCurrent();
    }

    private void refreshCurrent() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LivePlayRequest request = state.getActiveRequest();
        if (request != null && request.isCatchup() && request.matches(channel)) {
            long startPositionMs = host.hasPlaybackSession() ? host.getPlayerPosition() : request.getPosition();
            requestPlayback(LivePlayRequest.catchup(channel, request.getCatchupData(), startPositionMs, ++requestSequence), true);
        } else {
            requestLiveInCycle();
        }
    }

    public void onPlaybackServiceReady() {
        host.restorePlaybackKey(state.getPlaybackKey());
        syncPlaybackMetadata();
        if (restorePlaybackIfNeeded()) return;
        if (state.getChannel() != null && state.getActiveRequest() == null) requestLive();
    }

    private void syncPlaybackMetadata() {
        MediaMetadata metadata = state.getPlaybackMetadata();
        if (metadata != null) host.renderPlaybackMetadata(metadata);
    }

    private boolean restorePlaybackIfNeeded() {
        LivePlayRequest request = state.getPlayingRequest();
        if (request == null || !request.matches(state.getChannel()) || host.hasPlaybackSession()) return false;
        refresh();
        return true;
    }

    public void prevChannel() {
        moveChannel(-1);
    }

    public void nextChannel() {
        moveChannel(1);
    }

    public void prevLine() {
        switchLine(false, true);
    }

    public void nextLine(boolean show) {
        switchLine(true, show);
    }

    public void selectLine(int position) {
        Channel channel = state.getChannel();
        if (channel == null || position < 0 || position >= channel.getUrls().size()) return;
        channel.setIndex(position);
        beginCycle(true, false);
        host.renderLineSelection(channel, true);
        requestLiveInCycle();
    }

    private void switchLine(boolean next, boolean show) {
        Channel channel = state.getChannel();
        if (channel == null || channel.isOnly()) return;
        channel.switchLine(next);
        beginCycle(true, false);
        host.renderLineSelection(channel, show);
        requestLiveInCycle();
    }

    private void moveChannel(int delta) {
        Group group = state.getGroup();
        if (group == null || group.isEmpty()) return;
        int size = group.getChannel().size();
        int position = group.getPosition() + delta;
        boolean limit = position < 0 || position >= size;
        if (LiveSetting.isAcross() && limit) moveGroup(delta);
        else group.setPosition(limit ? wrap(position, size) : position);
        group = state.getGroup();
        if (group != null && !group.isEmpty()) selectChannel(group.current());
    }

    private void moveGroup(int delta) {
        int count = host.getGroupCount();
        if (count <= 1) return;
        Group current = state.getGroup();
        int position = host.getGroupPosition();
        for (int i = 0; i < count; i++) {
            position = wrap(position + delta, count);
            Group target = host.getGroup(position);
            if (target.equals(current)) return;
            if (target.skip() || target.isEmpty()) continue;
            state.setGroup(target);
            host.renderGroupSelection(target);
            host.renderGroupChannels(target);
            target.setPosition(delta > 0 ? 0 : target.getChannel().size() - 1);
            return;
        }
    }

    private int wrap(int position, int size) {
        return ((position % size) + size) % size;
    }

    private void beginCycle(boolean manual, boolean chooseHealthyLine) {
        cancelPendingRecovery();
        attemptedLines.clear();
        retriedCurrent = false;
        Channel channel = state.getChannel();
        if (channel == null || manual || !chooseHealthyLine) return;
        List<Integer> candidates = healthStore.rank(channel, attemptedLines);
        if (!candidates.isEmpty()) channel.setIndex(candidates.get(0));
    }

    public void playbackError(PlaybackFailure failure, LivePlayRequest request) {
        if (isCurrentPlayback(request)) handleFailure(failure, request);
    }

    public void onPlaybackReady(LivePlayRequest request) {
        if (isCurrentPlayback(request)) scheduleStablePlayback(request);
    }

    public boolean isCurrentPlayback(LivePlayRequest request) {
        return request != null && request == state.getPlayingRequest() && pendingRecovery == null
                && request.getRequestId() == requestSequence && request.matches(state.getChannel())
                && Objects.equals(healthSource, LiveLineHealthStore.currentSourceKey());
    }

    private void bindHealthStore() {
        String source = LiveLineHealthStore.currentSourceKey();
        if (healthStore != null && Objects.equals(source, healthSource)) return;
        cancelPendingRecovery();
        state.clearPlayback();
        attemptedLines.clear();
        retriedCurrent = false;
        healthSource = source;
        healthStore = healthStoreFactory.get();
    }

    private void cancelPendingRecovery() {
        recoveryGeneration++;
        if (pendingRecovery != null) scheduler.remove(pendingRecovery);
        if (stablePlayback != null) scheduler.remove(stablePlayback);
        pendingRecovery = null;
        stablePlayback = null;
    }

    private void handleLiveFailure(PlaybackFailure failure, LivePlayRequest request) {
        LiveFailureKind kind = LiveFailureClassifier.from(failure);
        Set<Integer> excluded = new LinkedHashSet<>(attemptedLines);
        excluded.add(request.getLine());
        LiveRecoveryDecision decision = recoveryPolicy.decide(kind, request.getLine(), retriedCurrent,
                healthStore.rank(request.getChannel(), excluded), LiveSetting.isReconnect(),
                LiveSetting.isChange(), LiveSetting.getReconnectDelaySeconds() * 1000L);
        applyDecision(decision, failure, request);
    }

    private void applyDecision(LiveRecoveryDecision decision, PlaybackFailure failure, LivePlayRequest request) {
        cancelPendingRecovery();
        state.clearPendingRequest();
        Channel channel = request.getChannel();
        int line = request.getLine();
        int total = channel.getUrls().size();
        switch (decision.action()) {
            case RETRY_CURRENT -> {
                retriedCurrent = true;
                host.renderConnectionState(LiveConnectionState.reconnecting(line, total, (int) ((decision.delayMs() + 999) / 1000)));
                long generation = recoveryGeneration;
                long requestId = request.getRequestId();
                pendingRecovery = () -> {
                    if (!isCurrent(generation, requestId) || !request.matches(state.getChannel())) return;
                    pendingRecovery = null;
                    requestLiveInCycle();
                };
                scheduler.post(pendingRecovery, decision.delayMs());
            }
            case SWITCH_LINE -> {
                healthStore.recordFailure(channel, line, LiveFailureClassifier.from(failure));
                attemptedLines.add(line);
                retriedCurrent = false;
                channel.setIndex(decision.lineIndex());
                host.renderConnectionState(LiveConnectionState.switching(line, decision.lineIndex(), total));
                host.renderLineSelection(channel, true);
                requestLiveInCycle();
            }
            case FAIL -> {
                if (!request.isCatchup()) {
                    healthStore.recordFailure(channel, line, LiveFailureClassifier.from(failure));
                    LiveChannelStore.markFailed(channel);
                    host.renderChannelStatus(channel);
                }
                state.clearPlayback();
                String message = failure.message().isEmpty() ? ResUtil.getString(R.string.live_unavailable) : failure.message();
                host.renderConnectionState(LiveConnectionState.failed(message));
                host.resetPlaybackForError(message);
            }
        }
    }

    private boolean isCurrent(long generation, long requestId) {
        return generation == recoveryGeneration && requestId == requestSequence && state.getChannel() != null
                && Objects.equals(healthSource, LiveLineHealthStore.currentSourceKey());
    }

    public void onPlaybackReady() {
        onPlaybackReady(state.getPlayingRequest());
    }

    private void scheduleStablePlayback(LivePlayRequest request) {
        if (stablePlayback != null) return;
        host.renderConnectionState(LiveConnectionState.playing(request.getLine(), request.getChannel().getUrls().size()));
        if (request.isCatchup()) return;
        long generation = recoveryGeneration;
        long requestId = request.getRequestId();
        long startupMs = Math.max(0, clock.getAsLong() - connectStartedAtMs);
        stablePlayback = () -> {
            if (!isCurrent(generation, requestId) || !request.matches(state.getChannel())) return;
            stablePlayback = null;
            healthStore.recordSuccess(request.getChannel(), request.getLine(), startupMs);
            LiveChannelStore.clearFailed(request.getChannel());
            host.renderChannelStatus(request.getChannel());
            attemptedLines.clear();
            retriedCurrent = false;
        };
        scheduler.post(stablePlayback, STABLE_PLAYBACK_DURATION_MS);
    }

    private void playNextProgram() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        EpgData data = getNextEpgData(channel);
        if (data != null && selectEpg(data)) return;
        data = getCurrentEpgData(channel);
        if (data != null) host.renderEpgSelection(data);
        refresh();
    }

    private boolean playNextCatchupProgram() {
        EpgData data = getNextEpgData(state.getChannel());
        return data != null && selectEpg(data);
    }

    static boolean isActiveCatchup(@Nullable LivePlayRequest request, EpgData data) {
        return request != null && request.isCatchup() && request.getCatchupData() == data;
    }

    private void requestLive() {
        beginCycle(false, false);
        requestLiveInCycle();
    }

    private void requestLiveInCycle() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        bindHealthStore();
        LiveConfig.get().setKeep(channel);
        requestPlayback(LivePlayRequest.live(channel, C.TIME_UNSET, ++requestSequence), true);
    }

    private boolean requestCatchup(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        beginCycle(true, false);
        requestPlayback(LivePlayRequest.catchup(channel, data, startPositionMs, ++requestSequence), false);
        return true;
    }

    private void requestPlayback(LivePlayRequest request, boolean showProgress) {
        cancelPendingRecovery();
        if (!host.isPlaybackServiceReady()) return;
        state.setPendingRequest(request);
        connectStartedAtMs = clock.getAsLong();
        host.renderConnectionState(LiveConnectionState.connecting(request.getLine(), request.getChannel().getUrls().size()));
        host.stopPlaybackForRefresh();
        publishPlaybackMetadata(getEpgData(request));
        host.requestUrl(request);
        if (showProgress) host.showProgress();
    }

    private EpgData getEpgData(LivePlayRequest request) {
        return request.isCatchup() ? request.getCatchupData() : getCurrentEpgData(request.getChannel());
    }

    @Nullable
    private EpgData getNextEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId());
        int current = epg.getInRange();
        int position = epg.getSelected() + 1;
        return position <= current && position > 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    @Nullable
    private EpgData getCurrentEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId()).selected();
        int position = epg.getSelected();
        return position >= 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    private MediaMetadata publishPlaybackMetadata(@Nullable EpgData liveData) {
        LivePlayRequest request = state.getActiveRequest();
        Channel channel = request == null ? state.getChannel() : request.getChannel();
        EpgData data = request != null && request.isCatchup() ? request.getCatchupData() : liveData;
        MediaMetadata metadata = buildPlaybackMetadata(channel, data);
        state.setPlaybackMetadata(metadata);
        host.renderPlaybackMetadata(metadata);
        return metadata;
    }

    private MediaMetadata buildPlaybackMetadata(Channel channel, EpgData data) {
        String title = channel == null ? "" : channel.getShow();
        String logo = channel == null ? "" : channel.getLogo();
        String artist = data == null ? "" : data.format();
        String name = data == null ? "" : data.getTitle();
        return MediaItemFactory.buildMetadata(title, artist, logo, name);
    }

    private boolean cannotApply(PlaybackResult<LivePlayRequest> playback) {
        LivePlayRequest pending = state.getPendingRequest();
        LivePlayRequest request = playback.request();
        return pending == null || !pending.matches(request) || !request.matches(state.getChannel());
    }
}
