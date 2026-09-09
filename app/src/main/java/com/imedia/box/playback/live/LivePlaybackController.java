package com.imedia.box.playback.live;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Result;
import com.imedia.box.live.LiveChannelStore;
import com.imedia.box.playback.PlaybackResult;
import com.imedia.box.player.media.MediaItemFactory;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.ResUtil;

public class LivePlaybackController {

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long STABLE_PLAYBACK_DURATION_MS = 30_000;

    private final LivePlaybackState state;
    private final LivePlaybackHost host;
    private final Runnable reconnect = this::runReconnect;
    private final Runnable stablePlayback = () -> reconnectAttempts = 0;
    private int reconnectAttempts;

    public LivePlaybackController(LivePlaybackHost host, LivePlaybackState state) {
        this.state = state;
        this.host = host;
    }

    public void reset() {
        resetReconnectAttempts();
        state.reset();
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
        resetReconnectAttempts();
        LivePlayRequest activeRequest = state.getActiveRequest();
        boolean restore = activeRequest != null && activeRequest.matches(channel);
        state.setChannel(channel);
        host.renderChannelSelection(channel);
        if (restore) restorePlaybackIfNeeded();
        else {
            state.clearPlayback();
            requestLive();
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
        if (result.hasMsg() || TextUtils.isEmpty(realUrl)) failPlayback(result.getMsg());
        else startResolvedPlayback(result, request, realUrl);
    }

    private void startResolvedPlayback(Result result, LivePlayRequest request, String realUrl) {
        long position = result.hasPosition() ? result.getPosition() : request.getPosition();
        state.setPlayingRequest(request, realUrl);
        host.startPlayback(result, position, publishPlaybackMetadata(getEpgData(request)));
    }

    private void failPlayback(String msg) {
        LivePlayRequest request = state.getActiveRequest();
        state.clearPendingRequest();
        playbackError(msg, request == null || !request.isCatchup());
    }

    public void playbackError(String msg) {
        LivePlayRequest request = state.getActiveRequest();
        playbackError(msg, request == null || !request.isCatchup());
    }

    private void playbackError(String msg, boolean markChannel) {
        host.resetPlaybackForError(TextUtils.isEmpty(msg) ? ResUtil.getString(R.string.live_unavailable) : msg);
        if (reconnectAfterError()) return;
        if (!fallbackAfterError() && markChannel) {
            LiveChannelStore.markFailed(state.getChannel());
            host.renderChannelStatus(state.getChannel());
        }
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
        resetReconnectAttempts();
        refreshCurrent();
    }

    private void refreshCurrent() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LivePlayRequest request = state.getActiveRequest();
        if (request != null && request.isCatchup() && request.matches(channel)) {
            long startPositionMs = host.hasPlaybackSession() ? host.getPlayerPosition() : request.getPosition();
            requestPlayback(LivePlayRequest.catchup(channel, request.getCatchupData(), startPositionMs), true);
        } else {
            requestLive();
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
        resetReconnectAttempts();
        host.renderLineSelection(channel, true);
        requestLive();
    }

    private void switchLine(boolean next, boolean show) {
        Channel channel = state.getChannel();
        if (channel == null || channel.isOnly()) return;
        channel.switchLine(next);
        resetReconnectAttempts();
        host.renderLineSelection(channel, show);
        requestLive();
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

    private boolean fallbackAfterError() {
        Channel channel = state.getChannel();
        if (!LiveSetting.isChange() || channel == null || channel.isLast()) return false;
        nextLine(true);
        return true;
    }

    private boolean reconnectAfterError() {
        App.removeCallbacks(stablePlayback);
        int delay = LiveSetting.getReconnectDelaySeconds();
        if (delay <= 0 || state.getChannel() == null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return false;
        reconnectAttempts++;
        App.post(reconnect, delay * 1000L);
        return true;
    }

    private void runReconnect() {
        if (LiveSetting.isReconnect() && state.getChannel() != null) refreshCurrent();
    }

    public void onPlaybackReady() {
        App.removeCallbacks(stablePlayback);
        App.post(stablePlayback, STABLE_PLAYBACK_DURATION_MS);
    }

    private void resetReconnectAttempts() {
        App.removeCallbacks(reconnect);
        App.removeCallbacks(stablePlayback);
        reconnectAttempts = 0;
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
        Channel channel = state.getChannel();
        if (channel == null) return;
        LiveConfig.get().setKeep(channel);
        requestPlayback(LivePlayRequest.live(channel, C.TIME_UNSET), true);
    }

    private boolean requestCatchup(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        requestPlayback(LivePlayRequest.catchup(channel, data, startPositionMs), false);
        return true;
    }

    private void requestPlayback(LivePlayRequest request, boolean showProgress) {
        if (!host.isPlaybackServiceReady()) return;
        state.setPendingRequest(request);
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
