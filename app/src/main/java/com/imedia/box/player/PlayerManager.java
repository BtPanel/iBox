package com.imedia.box.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.ui.danmaku.DanmakuConfig;

import com.imedia.box.App;
import com.imedia.box.Constant;
import com.imedia.box.R;
import com.imedia.box.bean.Danmaku;
import com.imedia.box.bean.Result;
import com.imedia.box.bean.Sub;
import com.imedia.box.bean.Track;
import com.imedia.box.impl.ParseCallback;
import com.imedia.box.player.effect.PlayerEffectManager;
import com.imedia.box.player.effect.audio.AudioEffectBands;
import com.imedia.box.player.engine.PlayerEngine;
import com.imedia.box.player.engine.PlayerEngineFactory;
import com.imedia.box.player.media.PlaySpec;
import com.imedia.box.player.parse.ParseJob;
import com.imedia.box.player.track.TrackUtil;
import com.imedia.box.setting.DanmakuSetting;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.PreloadSetting;
import com.imedia.box.setting.SpeedSetting;
import com.imedia.box.setting.SubtitleStyleOverride;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerManager implements ParseCallback {

    private final PlayerEffectManager effects;
    private final Runnable runnable;
    private final Callback callback;
    private PendingPreload pendingPreload;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private final Set<String> failedDanmakuSources = new HashSet<>();
    private final Deque<String> diagnosticEvents = new ArrayDeque<>();
    private String lastError = "";
    private String parseFrom = "";
    private String diagnosticTitle = "";

    private DanmakuConfig danmakuConfig;
    private long pendingStartPositionMs;
    private boolean danmakuEnabled;
    private boolean initTrack;
    private int retry;
    private int decode;
    private int preferredEngine;
    private SubtitleStyleOverride subtitleStyleOverride;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.decode = PlayerEngine.HARD;
        this.preferredEngine = PlayerSetting.getEngine();
        this.runnable = this::onPlayTimeout;
        this.pendingStartPositionMs = C.TIME_UNSET;
        this.engine = PlayerEngineFactory.create(decode, preferredEngine, listener);
        this.effects = new PlayerEffectManager(() -> engine);
        this.danmakuConfig = DanmakuSetting.getConfig();
        this.danmakuEnabled = DanmakuSetting.isShow();
        this.player = engine.getPlayer();
    }

    public void release() {
        App.removeCallbacks(runnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    private void setPlayer(Player player) {
        this.player = player;
        callback.onPlayerRebuild(player);
    }

    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    public int getAudioChannelCount() {
        return engine == null ? Format.NO_VALUE : engine.getAudioChannelCount();
    }

    public List<MediaChapter> getCurrentMediaChapters() {
        return player.getCurrentMediaChapters();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return player.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : List.of();
    }

    private void notifyDanmakuSourceChanged() {
        callback.onDanmakuSourceChanged(getSelectedDanmakuUri());
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public String getMediaTitle() {
        MediaMetadata metadata = getMetadata();
        if (metadata == null) return "";
        CharSequence title = !TextUtils.isEmpty(metadata.displayTitle) ? metadata.displayTitle : metadata.title;
        if (TextUtils.isEmpty(title)) title = metadata.artist;
        return TextUtils.isEmpty(title) ? "" : title.toString();
    }

    public void setMetadata(@NonNull MediaMetadata metadata) {
        if (spec == null || metadata.equals(spec.getMetadata())) return;
        spec.setMetadata(metadata);
        if (TextUtils.isEmpty(spec.getUrl())) return;
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(metadata).build());
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean hasPlaySpec() {
        return spec != null;
    }

    public boolean canPreloadNext() {
        return PreloadSetting.isEnabled() && PreloadSetting.isNextEpisodeEnabled() && engine != null && engine.getType() == PlayerEngine.Type.EXO;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return player.getCurrentMediaItem() != null && player.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return player.getCurrentMediaItem() != null && !player.isCurrentMediaItemLive();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    public boolean haveEdition() {
        return !getCurrentMediaEditions().isEmpty();
    }

    public boolean haveChapter() {
        return !getCurrentMediaChapters().isEmpty();
    }

    public boolean haveDanmaku() {
        return spec != null && spec.getSelectedDanmaku() != null;
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public PlaybackDiagnostics getDiagnostics() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("engine", engine == null ? "-" : engine.getType().name());
        values.put("decode", getDecodeText());
        values.put("state", getStateText());
        values.put("resolution", getSizeText().isEmpty() ? "-" : getSizeText());
        values.put("buffer", formatDuration(player == null ? 0 : player.getTotalBufferedDuration()));
        values.put("speed", String.format(java.util.Locale.ROOT, "%.2fx", getSpeed()));
        values.put("video", getSelectedTrackText(C.TRACK_TYPE_VIDEO));
        values.put("audio", getSelectedTrackText(C.TRACK_TYPE_AUDIO));
        values.put("subtitle", getSelectedTrackText(C.TRACK_TYPE_TEXT));
        values.put("parser", parseFrom.isEmpty() ? "-" : parseFrom);
        values.put("error", lastError.isEmpty() ? "-" : lastError);
        return new PlaybackDiagnostics(getMediaTitle(), getUrl() == null ? "" : getUrl(), getHeaders(), values, new ArrayList<>(diagnosticEvents));
    }

    public void recordDiagnosticEvent(String event) {
        if (TextUtils.isEmpty(event)) return;
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        diagnosticEvents.addFirst(time + "  " + event);
        while (diagnosticEvents.size() > 8) diagnosticEvents.removeLast();
    }

    private String getStateText() {
        if (player == null) return "Idle";
        return switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING -> "Buffering";
            case Player.STATE_READY -> player.isPlaying() ? "Playing" : "Paused";
            case Player.STATE_ENDED -> "Ended";
            default -> "Idle";
        };
    }

    private String getSelectedTrackText(int type) {
        if (player == null) return "-";
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) return formatTrack(group.getTrackFormat(i), type);
            }
        }
        return "-";
    }

    private String formatTrack(Format format, int type) {
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(format.codecs)) parts.add(format.codecs);
        else if (!TextUtils.isEmpty(format.sampleMimeType)) parts.add(format.sampleMimeType.replace("video/", "").replace("audio/", "").replace("text/", ""));
        if (type == C.TRACK_TYPE_VIDEO && format.width > 0 && format.height > 0) parts.add(format.width + "x" + format.height);
        if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) parts.add(format.channelCount + "ch");
        if (type == C.TRACK_TYPE_AUDIO && format.sampleRate > 0) parts.add(format.sampleRate + "Hz");
        int bitrate = format.averageBitrate > 0 ? format.averageBitrate : format.bitrate;
        if (bitrate > 0) parts.add(formatBitrate(bitrate));
        if (!TextUtils.isEmpty(format.language)) parts.add(format.language);
        return parts.isEmpty() ? "-" : TextUtils.join("  ", parts);
    }

    private String formatBitrate(int bitrate) {
        return bitrate >= 1_000_000
                ? String.format(java.util.Locale.ROOT, "%.1f Mbps", bitrate / 1_000_000f)
                : Math.max(1, bitrate / 1000) + " kbps";
    }

    private String formatDuration(long durationMs) {
        return durationMs <= 0 ? "0 s" : String.format(java.util.Locale.ROOT, "%.1f s", durationMs / 1000f);
    }

    public boolean canSetAudioSetting() {
        return effects.canSetAudioSetting();
    }

    public AudioEffectBands getAudioSettingBands() {
        return effects.getAudioSettingBands();
    }

    public int getAudioSettingError() {
        return effects.getAudioSettingError();
    }

    public boolean canSetVideoSetting() {
        return effects.canSetVideoSetting();
    }

    public int getVideoSettingError() {
        return effects.getVideoSettingError();
    }

    public boolean supportsVideoSharpness() {
        return effects.supportsVideoSharpness();
    }

    public int getEngine() {
        return isMpvEngine() ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
    }

    public void setEngine(int targetEngine) {
        setEngine(targetEngine, true);
    }

    public void setEngine(int targetEngine, boolean saveGlobal) {
        preferredEngine = Math.clamp(targetEngine, PlayerSetting.ENGINE_EXO, PlayerSetting.ENGINE_MPV);
        if (saveGlobal) PlayerSetting.putEngine(preferredEngine);
        if (isEmpty() || PlayerEngineFactory.matches(engine, spec, preferredEngine)) return;
        PlaybackSnapshot snapshot = PlaybackSnapshot.capture(player);
        startCurrent(snapshot.positionMs());
        snapshot.restore(player);
    }

    public String getPositionTime(long delta) {
        return Util.timeMs(Math.clamp(getPosition() + delta, 0, Math.max(0, getDuration())));
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        if (engine.addSubtitle(sub)) play();
        else startCurrent();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        startCurrent();
    }

    public void selectChapter(MediaChapter chapter) {
        player.selectChapter(chapter);
    }

    public void selectEdition(MediaEdition edition) {
        player.selectEdition(edition);
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuConfig = config;
        callback.onDanmakuConfigChanged(danmakuConfig);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuEnabled == enabled) return;
        danmakuEnabled = enabled;
        callback.onDanmakuEnabledChanged(danmakuEnabled);
    }

    public void setSubtitleSettingStyle() {
        if (engine != null) engine.setSubtitleStyle(subtitleStyleOverride);
        callback.onSubtitleStyleChanged(subtitleStyleOverride);
    }

    public SubtitleStyleOverride getSubtitleStyleOverride() {
        return subtitleStyleOverride;
    }

    public void setSubtitleStyleOverride(SubtitleStyleOverride override) {
        subtitleStyleOverride = override;
        setSubtitleSettingStyle();
    }

    public void sendDanmaku(String text) {
        callback.onDanmakuSent(text);
    }

    public float setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeed();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(SpeedSetting.clamp(speed)));
        return getSpeed();
    }

    public float toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? SpeedSetting.getLongPress() : 1);
    }

    public boolean supportsSkipSilence() {
        return effects.supportsSkipSilence();
    }

    public boolean isSkipSilence() {
        return effects.isSkipSilence();
    }

    public void setSkipSilenceEnabled(boolean enabled) {
        effects.setSkipSilenceEnabled(enabled);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) TrackUtil.setTrackSelection(player, tracks);
    }

    public void setVideoSetting(int preset) {
        effects.setVideoSetting(preset);
    }

    public void refreshVideoSetting() {
        effects.refreshVideoSetting();
    }

    public void previewVideoSetting(boolean original) {
        effects.previewVideoSetting(original);
    }

    public void setAudioSetting(int preset) {
        effects.setAudioSetting(preset);
    }

    public void refreshAudioSetting() {
        effects.refreshAudioSetting();
    }

    public void previewAudioSetting(boolean original) {
        effects.previewAudioSetting(original);
    }

    private boolean isMpvEngine() {
        return engine != null && engine.getType() == PlayerEngine.Type.MPV;
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void replay(long positionMs) {
        if (positionMs == C.TIME_UNSET) player.seekToDefaultPosition();
        else player.seekTo(positionMs);
        player.play();
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET) ? player.getTextOffsetMs() : 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET) ? player.getAudioOffsetMs() : 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
    }

    public void clear() {
        spec = null;
        failedDanmakuSources.clear();
    }

    public boolean preload(PlaySpec spec, long startPositionMs) {
        if (!canPreloadNext() || spec == null || !PlayerEngineFactory.matches(engine, spec, preferredEngine)) return false;
        pendingPreload = new PendingPreload(spec.checkUa(), Math.max(0, startPositionMs));
        startPreloadIfReady();
        return true;
    }

    public void clearPreload() {
        pendingPreload = null;
        if (engine != null) engine.clearPreload();
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    public void toggleDecode() {
        setDecode(isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
    }

    public int getDecode() {
        return decode;
    }

    public void setDecodePreference(int decode) {
        setDecode(decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD);
    }

    private void handleDecodeError(PlaybackException e) {
        recordDiagnosticEvent(ResUtil.getString(R.string.diagnostics_event_decode, isHard() ? "software" : "hardware"));
        if (++retry > 1) callback.onError(engine.getErrorMessage(e));
        else retryDecode(isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD, isStuckPlaying(e));
    }

    private void retryDecode(int decode, boolean forceSoftwareDecode) {
        setDecode(decode, forceSoftwareDecode);
        PlaybackSnapshot snapshot = PlaybackSnapshot.capture(player);
        startCurrent(snapshot.positionMs());
        snapshot.restore(player);
    }

    private void setDecode(int decode) {
        setDecode(decode, false);
    }

    private void setDecode(int decode, boolean forceSoftwareDecode) {
        this.decode = decode;
        engine.setDecode(decode, forceSoftwareDecode);
        callback.onDecodeChanged();
    }

    private boolean isStuckPlaying(PlaybackException error) {
        return error.getCause() instanceof StuckPlayerException stuck
                && stuck.stuckType == StuckPlayerException.STUCK_PLAYING_NO_PROGRESS;
    }

    private boolean isHard() {
        return decode == PlayerEngine.HARD;
    }

    private void onPlayTimeout() {
        lastError = ResUtil.getString(R.string.error_play_timeout);
        recordDiagnosticEvent(lastError);
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
        stop();
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, spec, preferredEngine)) return;
        PlayerEngine old = engine;
        player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, spec, preferredEngine, listener);
        engine.setSubtitleStyle(subtitleStyleOverride);
        setPlayer(engine.getPlayer());
        old.release();
    }

    public void browse(PlaySpec spec, long startPositionMs) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY, startPositionMs);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, long startPositionMs) {
        prepareDiagnosticSession(spec == null ? null : spec.getMetadata());
        parseFrom = ResUtil.getString(R.string.diagnostics_direct);
        recordDiagnosticEvent(ResUtil.getString(R.string.diagnostics_event_direct));
        this.spec = spec;
        failedDanmakuSources.clear();
        setMediaItem(timeout, startPositionMs);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, long startPositionMs) {
        stopParse();
        prepareDiagnosticSession(metadata);
        parseFrom = useParse ? ResUtil.getString(R.string.diagnostics_parsing) : ResUtil.getString(R.string.diagnostics_direct);
        recordDiagnosticEvent(useParse ? ResUtil.getString(R.string.diagnostics_event_parse) : ResUtil.getString(R.string.diagnostics_event_direct));
        pendingStartPositionMs = startPositionMs;
        spec = PlaySpec.fromParse(result, key, metadata);
        failedDanmakuSources.clear();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
        pendingStartPositionMs = C.TIME_UNSET;
    }

    private void setMediaItem(long timeout, long startPositionMs) {
        if (spec == null || spec.getUrl() == null) return;
        ensureEngine(spec.checkUa());
        pendingPreload = null;
        initTrack = false;
        engine.start(spec, startPositionMs);
        notifyDanmakuSourceChanged();
        App.post(runnable, timeout);
        callback.onPrepare();
    }

    private void startCurrent() {
        startCurrent(getPosition());
    }

    private void startCurrent(long startPositionMs) {
        setMediaItem(Constant.TIMEOUT_PLAY, startPositionMs);
    }

    private void startPreloadIfReady() {
        PendingPreload preload = pendingPreload;
        if (preload == null || player.getPlaybackState() != Player.STATE_READY) return;
        pendingPreload = null;
        engine.preload(preload.spec(), preload.startPositionMs());
    }

    @Nullable
    public Uri getSelectedDanmakuUri() {
        Danmaku item = spec != null ? spec.getSelectedDanmaku() : null;
        return item == null ? null : item.getUri();
    }

    public void setDanmaku(Danmaku item) {
        if (spec == null) return;
        if (item != null) failedDanmakuSources.remove(item.getUrl());
        spec.selectDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public boolean retryDanmakuSource(Uri failedUri) {
        if (spec == null || failedUri == null) return false;
        Uri selectedUri = getSelectedDanmakuUri();
        if (selectedUri == null) return false;
        if (!selectedUri.equals(failedUri)) return true;

        Danmaku selected = spec.getSelectedDanmaku();
        List<Danmaku> items = spec.getDanmakus();
        failedDanmakuSources.add(selected.getUrl());
        int selectedIndex = items.indexOf(selected);
        for (int offset = 1; offset < items.size(); offset++) {
            Danmaku candidate = items.get((selectedIndex + offset) % items.size());
            if (candidate.isEmpty() || failedDanmakuSources.contains(candidate.getUrl())) continue;
            spec.selectDanmaku(candidate);
            notifyDanmakuSourceChanged();
            return true;
        }
        return false;
    }

    public void markDanmakuSourceUnavailable(Uri uri) {
        if (spec == null || uri == null) return;
        Danmaku selected = spec.getSelectedDanmaku();
        Uri selectedUri = selected == null ? null : selected.getUri();
        if (uri.equals(selectedUri)) failedDanmakuSources.add(selected.getUrl());
    }

    public boolean hasAutomaticDanmakuSource() {
        if (spec == null) return false;
        return spec.getDanmakus().stream().anyMatch(this::isAutomaticDanmakuSource);
    }

    public boolean searchDanmakuFromSource() {
        if (spec == null) return false;
        Danmaku source = spec.getDanmakus().stream().filter(this::isAutomaticDanmakuSource).findFirst().orElse(null);
        if (source == null) return false;
        failedDanmakuSources.remove(source.getUrl());
        spec.selectDanmaku(source);
        notifyDanmakuSourceChanged();
        return true;
    }

    private boolean isAutomaticDanmakuSource(Danmaku item) {
        Uri uri = item == null ? null : item.getUri();
        String action = uri == null ? null : uri.getQueryParameter("do");
        return action != null && action.toLowerCase(java.util.Locale.ROOT).contains("autodanmu");
    }

    public void toggleDanmaku(Danmaku item) {
        if (spec == null) return;
        if (item != null) failedDanmakuSources.remove(item.getUrl());
        spec.toggleDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public void addDanmaku(Danmaku item) {
        if (spec == null || item == null || item.isEmpty() || failedDanmakuSources.contains(item.getUrl())) return;
        Danmaku selected = spec.getSelectedDanmaku();
        boolean replaceFailed = selected == null || failedDanmakuSources.contains(selected.getUrl());
        spec.addDanmaku(item);
        if (!replaceFailed) return;
        spec.selectDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        parseFrom = TextUtils.isEmpty(from) ? ResUtil.getString(R.string.diagnostics_parsed) : from;
        recordDiagnosticEvent(ResUtil.getString(R.string.diagnostics_event_parse_success, parseFrom));
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        startCurrent(pendingStartPositionMs);
        pendingStartPositionMs = C.TIME_UNSET;
    }

    @Override
    public void onParseError() {
        lastError = ResUtil.getString(R.string.error_play_parse);
        recordDiagnosticEvent(lastError);
        pendingStartPositionMs = C.TIME_UNSET;
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private void prepareDiagnosticSession(MediaMetadata metadata) {
        CharSequence title = metadata == null ? null : (!TextUtils.isEmpty(metadata.displayTitle) ? metadata.displayTitle : metadata.title);
        String nextTitle = title == null ? "" : title.toString();
        if (TextUtils.isEmpty(nextTitle) || nextTitle.equals(diagnosticTitle)) return;
        diagnosticTitle = nextTitle;
        diagnosticEvents.clear();
        lastError = "";
        parseFrom = "";
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);

        void onDanmakuSourceChanged(Uri uri);

        void onDanmakuConfigChanged(DanmakuConfig config);

        void onDanmakuEnabledChanged(boolean enabled);

        void onDanmakuSent(String text);

        void onSubtitleStyleChanged(SubtitleStyleOverride override);
    }

    private record PendingPreload(PlaySpec spec, long startPositionMs) {
    }

    private record PlaybackSnapshot(long positionMs, boolean playWhenReady, PlaybackParameters playbackParameters, int repeatMode, float volume, long audioOffsetMs, long textOffsetMs) {

        private static PlaybackSnapshot capture(Player player) {
            float volume = player.isCommandAvailable(Player.COMMAND_GET_VOLUME) ? player.getVolume() : Float.NaN;
            long audioOffsetMs = player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET) ? player.getAudioOffsetMs() : C.TIME_UNSET;
            long textOffsetMs = player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET) ? player.getTextOffsetMs() : C.TIME_UNSET;
            return new PlaybackSnapshot(player.getCurrentPosition(), player.getPlayWhenReady(), player.getPlaybackParameters(), player.getRepeatMode(), volume, audioOffsetMs, textOffsetMs);
        }

        private void restore(Player player) {
            if (player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) player.setPlaybackParameters(playbackParameters);
            if (player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) player.setRepeatMode(repeatMode);
            if (!Float.isNaN(volume) && player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) player.setVolume(volume);
            if (audioOffsetMs != C.TIME_UNSET && player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(audioOffsetMs);
            if (textOffsetMs != C.TIME_UNSET && player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(textOffsetMs);
            if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.setPlayWhenReady(playWhenReady);
        }
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) App.removeCallbacks(runnable);
            if (state == Player.STATE_READY) startPreloadIfReady();
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onMediaChaptersChanged(@NonNull List<MediaChapter> chapters) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (spec == null) return;
            PlayerEngine.ErrorAction action = engine.handleError(e);
            lastError = engine.getErrorMessage(e) + " (" + e.errorCode + ")";
            recordDiagnosticEvent(lastError + " / " + action.name());
            if (action != PlayerEngine.ErrorAction.RECOVERED) App.removeCallbacks(runnable);
            switch (action) {
                case DECODE -> handleDecodeError(e);
                case RECOVERED -> notifyDanmakuSourceChanged();
                case FATAL -> callback.onError(engine.getErrorMessage(e));
            }
        }
    };
}
