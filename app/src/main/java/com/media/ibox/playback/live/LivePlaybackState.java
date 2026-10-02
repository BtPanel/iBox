package com.media.ibox.playback.live;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;

import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Group;

import java.util.LinkedHashSet;
import java.util.Set;

public class LivePlaybackState {

    private LivePlayRequest pendingRequest;
    private LivePlayRequest playingRequest;
    private LivePlayRequest failedCatchupRequest;
    private MediaMetadata playbackMetadata;
    private String playbackKey;
    private Channel channel;
    private Channel previousChannel;
    private Group previousGroup;
    private Group group;
    private long connectStartedAtMs;
    private long startupMs = -1;
    private boolean stableSuccessRecorded;
    private final Set<Integer> attemptedLines = new LinkedHashSet<>();
    private boolean retriedCurrent;
    private long requestSequence;
    private Recovery pendingRecovery;
    private LiveConnectionState connectionState;

    record Recovery(LivePlayRequest request, long deadlineMs) { }

    Set<Integer> getAttemptedLines() { return attemptedLines; }

    boolean hasRetriedCurrent() { return retriedCurrent; }

    void setRetriedCurrent(boolean retried) { retriedCurrent = retried; }

    long getRequestSequence() { return requestSequence; }

    Recovery getPendingRecovery() { return pendingRecovery; }

    void setPendingRecovery(Recovery recovery) { pendingRecovery = recovery; }

    LiveConnectionState getConnectionState() { return connectionState; }

    void setConnectionState(LiveConnectionState connection) { connectionState = connection; }

    void startConnecting(long nowMs) {
        connectStartedAtMs = nowMs;
        startupMs = -1;
        stableSuccessRecorded = false;
    }

    void captureReady(long nowMs) {
        if (startupMs < 0) startupMs = Math.max(0, nowMs - connectStartedAtMs);
    }

    long getStartupMs() { return startupMs; }

    boolean isStableSuccessRecorded() { return stableSuccessRecorded; }

    void recordStableSuccess() { stableSuccessRecorded = true; }

    public void reset() {
        clearPlayback();
        channel = null;
        previousChannel = null;
        previousGroup = null;
        group = null;
    }

    void clearPlayback() {
        failedCatchupRequest = null;
        pendingRecovery = null;
        connectionState = null;
        pendingRequest = null;
        playingRequest = null;
        playbackMetadata = null;
        playbackKey = null;
    }

    Group getGroup() {
        return group;
    }

    void setGroup(Group group) {
        this.group = group;
    }

    Channel getChannel() {
        return channel;
    }

    @Nullable
    Channel getPreviousChannel() {
        return previousChannel;
    }

    @Nullable
    Group getPreviousGroup() {
        return previousGroup;
    }

    void rememberPrevious(Channel selected) {
        if (channel == null || channel.getGroup() == selected.getGroup()
                && channel.getName().equals(selected.getName())
                && channel.getUrls().equals(selected.getUrls())) return;
        previousChannel = channel;
        previousGroup = channel.getGroup();
    }

    void setChannel(Channel channel) {
        this.channel = channel;
        this.group = channel != null ? channel.getGroup() : group;
    }

    @Nullable
    LivePlayRequest getPendingRequest() {
        return pendingRequest;
    }

    @Nullable
    LivePlayRequest getActiveRequest() {
        if (pendingRequest != null) return pendingRequest;
        if (playingRequest != null) return playingRequest;
        if (failedCatchupRequest != null) return failedCatchupRequest;
        return pendingRecovery == null ? null : pendingRecovery.request();
    }

    LivePlayRequest getFailedCatchupRequest() { return failedCatchupRequest; }

    void retainFailedCatchup(LivePlayRequest request, long positionMs) {
        failedCatchupRequest = LivePlayRequest.catchup(request.getChannel(), request.getCatchupData(),
                positionMs, request.getRequestId());
    }

    @Nullable
    LivePlayRequest getPlayingRequest() {
        return playingRequest;
    }

    boolean isCatchup() {
        LivePlayRequest request = getActiveRequest();
        return request != null && request.isCatchup();
    }

    void setPendingRequest(LivePlayRequest pendingRequest) {
        this.failedCatchupRequest = null;
        this.pendingRecovery = null;
        this.requestSequence = Math.max(requestSequence, pendingRequest.getRequestId());
        this.pendingRequest = pendingRequest;
        this.playingRequest = null;
        this.playbackKey = null;
    }

    void setPlayingRequest(LivePlayRequest playingRequest, String playbackKey) {
        this.playingRequest = playingRequest;
        this.playbackKey = playbackKey;
        this.pendingRequest = null;
    }

    @Nullable
    String getPlaybackKey() {
        return playbackKey;
    }

    @Nullable
    MediaMetadata getPlaybackMetadata() {
        return playbackMetadata;
    }

    void setPlaybackMetadata(MediaMetadata playbackMetadata) {
        this.playbackMetadata = playbackMetadata;
    }

    void clearPendingRequest() {
        this.pendingRequest = null;
    }
}
