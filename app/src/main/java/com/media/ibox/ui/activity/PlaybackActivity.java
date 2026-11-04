package com.media.ibox.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.media.ibox.R;
import com.media.ibox.bean.Result;
import com.media.ibox.playback.PlaybackIntent;
import com.media.ibox.player.PlaybackFailure;
import com.media.ibox.player.PlayerManager;
import com.media.ibox.player.media.PlaySpec;
import com.media.ibox.service.PlaybackService;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.setting.SubtitleSetting;
import com.media.ibox.setting.SubtitleStyleOverride;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.overlay.FloatingPlaybackPolicy;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class PlaybackActivity extends BaseActivity implements MediaController.Listener, Player.Listener, ServiceConnection {

    private static final String TAG = "DanmakuLoader";

    private final List<ServiceReadyObserver<?>> serviceReadyObservers = new ArrayList<>();
    private final List<Runnable> foreverObserverRemovers = new ArrayList<>();
    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mController;
    private PlaybackService mService;
    private boolean initialized;
    private boolean audioOnly;
    private boolean scrubbing;
    private boolean redirect;
    private boolean bound;
    private boolean stop;
    private boolean lock;

    protected MediaController controller() {
        return mController;
    }

    protected PlaybackService service() {
        return mService;
    }

    /**
     * 悬浮小窗是否正在显示。
     * <p>
     * 小窗播放时 Activity 会被 {@code onStop}，但用户仍在小窗里观看，
     * 所以判断"用户是否还在看"不能只看生命周期状态
     * （见 {@code VideoActivity#isAutoNextEnabled}）。
     */
    protected boolean isFloatingWindowShowing() {
        return service() != null && service().isFloatingWindowShowing();
    }

    protected PlayerManager player() {
        return mService.player();
    }

    protected boolean isRedirect() {
        return redirect;
    }

    protected void setRedirect(boolean redirect) {
        this.redirect = redirect;
        if (mService != null) mService.setNavigationCallback(redirect ? null : getNavigationCallback(), getPlaybackKey());
    }

    protected void updateNavigationKey() {
        updateNavigationKey(getPlaybackKey());
    }

    protected void updateNavigationKey(String key) {
        if (mService != null) mService.setNavigationCallback(getNavigationCallback(), key);
    }

    protected boolean isAudioOnly() {
        return audioOnly;
    }

    protected void setAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    protected boolean isStop() {
        return stop;
    }

    protected void setStop(boolean stop) {
        this.stop = stop;
    }

    protected boolean isLock() {
        return lock;
    }

    protected void setLock(boolean lock) {
        this.lock = lock;
    }

    protected abstract PlaybackService.NavigationCallback getNavigationCallback();

    protected abstract PlayerSeekView getSeekView();

    protected abstract PlayerView getPlayerView();

    protected abstract String getPlaybackKey();

    protected boolean isOwner() {
        String key = getPlaybackKey();
        return key == null || (mService != null && key.equals(player().getKey()));
    }

    protected boolean isBindingOwner() {
        return mService != null && mService.ownsBinding(getNavigationCallback());
    }

    protected boolean hasPlaybackSource() {
        return mService != null && isOwner() && !player().isEmpty();
    }

    protected <T> void observeForever(LiveData<T> liveData, Observer<T> observer) {
        liveData.observeForever(observer);
        foreverObserverRemovers.add(() -> liveData.removeObserver(observer));
    }

    protected <T> void observeWhenServiceReady(LiveData<T> liveData, Observer<T> observer) {
        ServiceReadyObserver<T> serviceObserver = new ServiceReadyObserver<>(observer);
        serviceReadyObservers.add(serviceObserver);
        observeForever(liveData, serviceObserver);
    }

    public void toggleDebugView() {
        getPlayerView().toggleDebugView();
        PlayerSetting.putDebug(getPlayerView().isDebugViewVisible());
    }

    public void onChoose() {
        if (!hasPlaybackSource()) return;
        PlayerManager player = player();
        PlaybackIntent.choose(this, player.getUrl(), player.getHeaders(), player.isVod(), player.getPosition(), player.getMediaTitle());
        setRedirect(true);
    }

    public void onShare(CharSequence title, String url, Map<String, String> headers) {
        PlaybackIntent.share(this, url, headers, title);
        setRedirect(true);
    }

    protected void setSeekNextFocusDown(int id) {
        View timeBar = getSeekFocusView();
        if (timeBar != null) timeBar.setNextFocusDownId(id);
    }

    protected View getSeekFocusView() {
        View timeBar = getSeekView().findViewById(androidx.media3.ui.R.id.exo_progress);
        return timeBar == null ? getSeekView() : timeBar;
    }

    protected boolean canSeekCurrentMediaItem() {
        return mController != null
                && mController.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                && mController.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                && mController.isCurrentMediaItemSeekable()
                && mController.getDuration() > 0;
    }

    protected void setActionFocusBoundary(View view) {
        if (view == null) return;
        if (view.isFocusable() && view.getId() != View.NO_ID) view.setNextFocusDownId(view.getId());
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) setActionFocusBoundary(group.getChildAt(i));
    }

    protected boolean isIdle() {
        return isPlaybackState(Player.STATE_IDLE);
    }

    protected boolean isEnded() {
        return isPlaybackState(Player.STATE_ENDED);
    }

    protected boolean isBuffering() {
        return isPlaybackState(Player.STATE_BUFFERING);
    }

    protected boolean isPaused() {
        return mController != null && !isBuffering() && !isIdle();
    }

    private boolean isPlaybackState(int state) {
        return mController != null && mController.getPlaybackState() == state;
    }

    protected void onServiceConnected() {
    }

    protected void onPrepare() {
    }

    protected void onTracksChanged() {
    }

    protected void onDecodeChanged() {
    }

    protected void onMediaOptionsChanged() {
    }

    protected void onError(String msg) {
    }

    protected void onError(PlaybackFailure failure) {
        onError(failure.message());
    }

    protected void onError(Object request, PlaybackFailure failure) {
        onError(failure);
    }

    protected boolean usesPlaybackRequestEvents() {
        return false;
    }

    protected void onPlaybackRequestStateChanged(Object request, int state) { }

    protected void onPlaybackRequestPlayingChanged(Object request, boolean isPlaying) { }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
    }

    protected void onPlaybackStateSynchronized(int state) {
    }

    protected void onSizeChanged(VideoSize size) {
    }

    protected void onReclaim() {
    }

    protected long startPositionMs() {
        return C.TIME_UNSET;
    }

    protected boolean seekTo(long deltaMs) {
        PlayerManager player = player();
        long targetMs = Math.max(0, player.getPosition() + deltaMs);
        long durationMs = player.getDuration();
        boolean seekToEnd = durationMs > 0 && targetMs >= durationMs;
        mController.seekTo(seekToEnd ? durationMs : targetMs);
        if (!seekToEnd) mController.play();
        return seekToEnd;
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, MediaMetadata metadata) {
        startPlayer(key, result, useParse, timeout, startPositionMs(), metadata);
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, long startPositionMs, MediaMetadata metadata) {
        startPlayer(key, result, useParse, timeout, startPositionMs, metadata, null);
    }

    protected void startPlayer(String key, Result result, boolean useParse, long timeout, long startPositionMs, MediaMetadata metadata, Object request) {
        PlaybackFailure failure = getPlaybackFailure(result);
        if (failure != null) onError(request, failure);
        else startPlayerInternal(key, result, useParse, timeout, startPositionMs, metadata, request);
    }

    @Nullable
    private PlaybackFailure getPlaybackFailure(Result result) {
        if (result.hasMsg()) return PlaybackFailure.source(result.getMsg());
        if (result.getRealUrl().isEmpty()) return PlaybackFailure.source(ResUtil.getString(R.string.error_play_url));
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) return PlaybackFailure.source(ResUtil.getString(R.string.error_play_drm));
        return null;
    }

    private void startPlayerInternal(String key, Result result, boolean useParse, long timeout, long startPositionMs, MediaMetadata metadata, Object request) {
        attachPlayerView();
        updateNavigationKey(key);
        if (result.needParse() || useParse) player().parse(key, result, useParse, metadata, startPositionMs, request);
        else player().start(PlaySpec.from(result, key, metadata), timeout, startPositionMs, request);
    }

    private void bindPlaybackService() {
        startService(new Intent(this, PlaybackService.class));
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
        buildControllerAsync();
        bound = true;
    }

    private void buildControllerAsync() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, token).setListener(this).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected, ContextCompat.getMainExecutor(this));
    }

    private void onControllerConnected() {
        try {
            mController = mControllerFuture.get();
            getSeekView().setPlayer(mController);
            mController.addListener(this);
            updateKeyIncrement();
        } catch (Exception ignored) {
        }
    }

    private void addSeekListener() {
        getSeekView().getTimeBar().addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar timeBar, long position) {
                PlaybackActivity.this.setScrubbing(true);
            }

            @Override
            public void onScrubMove(@NonNull TimeBar timeBar, long position) {
                PlaybackActivity.this.setScrubbing(true);
            }

            @Override
            public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
                PlaybackActivity.this.onScrubStop(canceled);
            }
        });
    }

    protected boolean isScrubbing() {
        return scrubbing;
    }

    protected void onScrubStop(boolean canceled) {
        if (!canceled && mController != null && mController.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) mController.play();
        setScrubbing(false);
    }

    private void setScrubbing(boolean scrubbing) {
        if (this.scrubbing == scrubbing) return;
        this.scrubbing = scrubbing;
        onScrubbingChanged(scrubbing);
    }

    protected void onScrubbingChanged(boolean scrubbing) {
    }

    private void updateKeyIncrement() {
        long durationMs = mController == null ? C.TIME_UNSET : mController.getDuration();
        long incrementMs = getKeyTimeIncrementMs(durationMs);
        TimeBar timeBar = getSeekView().getTimeBar();
        timeBar.setKeyTimeIncrement(incrementMs);
    }

    private long getKeyTimeIncrementMs(long durationMs) {
        if (durationMs > TimeUnit.HOURS.toMillis(3)) {
            return TimeUnit.MINUTES.toMillis(5);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(30)) {
            return TimeUnit.MINUTES.toMillis(1);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(15)) {
            return TimeUnit.SECONDS.toMillis(30);
        } else if (durationMs > TimeUnit.MINUTES.toMillis(10)) {
            return TimeUnit.SECONDS.toMillis(15);
        } else {
            return TimeUnit.SECONDS.toMillis(10);
        }
    }

    private PendingIntent buildSessionIntent() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean shouldReclaim() {
        return mService != null && !isOwner();
    }

    private void resumePlayback() {
        if (mService != null && mService.isFloatingWindowShowing()) mService.hideFloatingWindow();
        if (shouldReclaim()) reclaimPlayback();
        else attachPlayerView();
    }

    protected boolean showFloatingWindow(PendingIntent restoreIntent, PendingIntent settingsIntent) {
        if (mService == null) return false;
        int resizeMode = getPlayerView().getResizeMode();
        detachPlayerView();
        int displayId = getWindowManager().getDefaultDisplay().getDisplayId();
        int accentColor = MaterialColors.getColor(this,
                androidx.appcompat.R.attr.colorPrimary,
                getColor(R.color.ibox_primary));
        boolean shown = mService.showFloatingWindow(restoreIntent, settingsIntent, resizeMode,
                displayId, accentColor);
        if (!shown) attachPlayerView();
        return shown;
    }

    private void reclaimPlayback() {
        detachPlayerView();
        onReclaim();
    }

    private void claimBinding() {
        if (mService == null) return;
        mService.claimBinding(getNavigationCallback(), this::closePiP);
        mService.setSessionActivity(buildSessionIntent());
    }

    private boolean canActivate() {
        return mService != null && !isFinishing() && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private boolean canDispatch() {
        return !isFinishing() && isBindingOwner();
    }

    private void activateService() {
        if (!canActivate()) return;
        claimBinding();
        if (!isRedirect()) updateNavigationKey();
        dispatchPendingObservers();
        initService();
    }

    private void initService() {
        if (initialized) return;
        initialized = true;
        onServiceConnected();
    }

    private void closePiP() {
        if (!isInPictureInPictureMode()) return;
        detach();
        finish();
    }

    private void attachPlayerView() {
        if (mService != null) syncPlayerView(player().getPlayer());
    }

    private void detachPlayerView() {
        getPlayerView().setPlayer(null);
    }

    private void syncPlayerView(Player player) {
        if (mService != null && !FloatingPlaybackPolicy.shouldAttachPlayerView(
                mService.isFloatingWindowShowing())) return;
        getPlayerView().setPlayer(player);
        applySubtitleStyle();
        syncDanmakuSource();
        if (isOwner()) onPlaybackStateSynchronized(player.getPlaybackState());
    }

    protected void applySubtitleStyle() {
        SubtitleStyleOverride override = mService == null ? null : player().getSubtitleStyleOverride();
        SubtitleSetting.applyStyle(this, getPlayerView().getSubtitleView(), override);
    }

    private void configurePlayerView() {
        PlayerView playerView = getPlayerView();
        playerView.setRender(PlayerSetting.getRender());
        playerView.setDanmakuOkHttpClient(OkHttp.player());
        playerView.setDanmakuEnabled(DanmakuSetting.isShow());
        playerView.setDanmakuConfig(DanmakuSetting.getConfig());
        playerView.getDanmakuController().setListener(new DanmakuController.Listener() {
            @Override
            public void onLoadCompleted(Uri uri, int count) {
                Log.i(TAG, "Loaded " + count + " items from " + uri);
                if (count == 0 && mService != null && isOwner() && player().retryDanmakuSource(uri)) return;
                if (count == 0 && "wexautodanmu".equals(uri.getQueryParameter("do"))) return;
                if (count > 0) Notify.showCompact(ResUtil.getString(R.string.danmaku_loaded, count));
                else if (Setting.isConfigNoticeEnabled()) Notify.showCompact(R.string.danmaku_empty);
            }

            @Override
            public void onLoadError(Uri uri, java.io.IOException error) {
                Log.e(TAG, "Failed to load " + uri, error);
                if (mService != null && isOwner() && player().retryDanmakuSource(uri)) return;
                if (Setting.isConfigNoticeEnabled()) Notify.showCompact(R.string.danmaku_load_failed);
            }
        });
        SubtitleSetting.applyStyle(this, playerView.getSubtitleView());
    }

    protected void onDanmakuSourceReady(Uri uri) {
    }

    protected void onDanmakuEnabledUiChanged(boolean enabled) {
    }

    private void syncDanmakuSource() {
        if (mService == null || !isOwner()) return;
        getPlayerView().setDanmakuSource(player().getSelectedDanmakuUri());
    }

    private void releasePlaybackService() {
        if (mService != null) releaseService(isOwner());
        detach();
    }

    private void releaseService(boolean playbackOwner) {
        mService.removePlayerCallback(mPlayerCallback);
        if (!mService.releaseBinding(getNavigationCallback())) return;
        if (shouldKeepServiceAlive()) keepServiceAlive(playbackOwner);
        else mService.shutdown();
    }

    private boolean shouldKeepServiceAlive() {
        // 背景播放开启，或「悬浮返回播放」按钮开启（保留会话入口供返回续播）时保活。
        return FloatingPlaybackPolicy.shouldKeepServiceAlive(mService.hasMediaClient(),
                mService.hasPlayerCallback(), mService.isFloatingWindowShowing(),
                isOwner() && (!PlayerSetting.isBackgroundOff() || Setting.isResumeButtonEnabled()));
    }

    private void keepServiceAlive(boolean playbackOwner) {
        if (mService.isFloatingWindowShowing()) return;
        // 暂停并保留会话入口（悬浮返回按钮可续播）：
        // 「后台播放」关闭，或「悬浮返回播放」开关开启（该开关语义 = 离开页面即暂停、靠按钮返回续播）时 suspend；
        // 仅「后台播放」开启且「悬浮返回播放」关闭时，才继续后台播放。
        if (playbackOwner && (PlayerSetting.isBackgroundOff() || Setting.isResumeButtonEnabled())) {
            mService.suspend();
        }
        mService.resetSessionActivity();
    }

    private void detach() {
        releaseController();
        releaseBinding();
    }

    private void pausePlayback() {
        if (mController != null) mController.pause();
        else if (mService != null) player().pause();
    }

    private void releaseController() {
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        if (mController != null) mController.removeListener(this);
        if (mController != null) getSeekView().setPlayer(null);
        mControllerFuture = null;
        mController = null;
    }

    private void releaseBinding() {
        if (!bound) return;
        bound = false;
        if (mService != null) mService.removePlayerCallback(mPlayerCallback);
        unbindService(this);
        mService = null;
    }

    private void clearObservers() {
        foreverObserverRemovers.forEach(Runnable::run);
        foreverObserverRemovers.clear();
        serviceReadyObservers.clear();
    }

    private void dispatchPendingObservers() {
        if (!canDispatch()) return;
        serviceReadyObservers.forEach(ServiceReadyObserver::dispatch);
    }

    private final PlaybackService.PlayerCallback mPlayerCallback = new PlaybackService.PlayerCallback() {

        @Override
        public void onPrepare() {
            if (isOwner()) PlaybackActivity.this.onPrepare();
        }

        @Override
        public void onTracksChanged() {
            if (isOwner()) PlaybackActivity.this.onTracksChanged();
            if (PlayerSetting.isDebug() && !getPlayerView().isDebugViewVisible()) getPlayerView().toggleDebugView();
        }

        @Override
        public void onDecodeChanged() {
            if (isOwner()) PlaybackActivity.this.onDecodeChanged();
        }

        @Override
        public void onMediaOptionsChanged() {
            if (isOwner()) PlaybackActivity.this.onMediaOptionsChanged();
        }

        @Override
        public void onError(PlaybackFailure failure) {
            if (isOwner()) PlaybackActivity.this.onError(failure);
        }

        @Override
        public void onError(Object request, PlaybackFailure failure) {
            if (isBindingOwner()) PlaybackActivity.this.onError(request, failure);
        }

        @Override
        public void onPlaybackStateChanged(Object request, int state) {
            if (isBindingOwner()) PlaybackActivity.this.onPlaybackRequestStateChanged(request, state);
        }

        @Override
        public void onIsPlayingChanged(Object request, boolean isPlaying) {
            if (isBindingOwner()) PlaybackActivity.this.onPlaybackRequestPlayingChanged(request, isPlaying);
        }

        @Override
        public void onPlayerRebuild(Player player) {
            if (isOwner()) syncPlayerView(player);
        }

        @Override
        public void onDanmakuSourceChanged(Uri uri) {
            if (!isOwner()) return;
            getPlayerView().setDanmakuSource(uri);
            PlaybackActivity.this.onDanmakuSourceReady(uri);
        }

        @Override
        public void onDanmakuConfigChanged(DanmakuConfig config) {
            if (isOwner()) getPlayerView().setDanmakuConfig(config);
        }

        @Override
        public void onDanmakuEnabledChanged(boolean enabled) {
            if (!isOwner()) return;
            getPlayerView().setDanmakuEnabled(enabled);
            PlaybackActivity.this.onDanmakuEnabledUiChanged(enabled);
        }

        @Override
        public void onDanmakuSent(String text) {
            if (isOwner()) getPlayerView().sendDanmaku(text);
        }

        @Override
        public void onSubtitleStyleChanged(SubtitleStyleOverride override) {
            if (isOwner()) SubtitleSetting.applyStyle(PlaybackActivity.this, getPlayerView().getSubtitleView(), override);
        }
    };

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        configurePlayerView();
        bindPlaybackService();
        addSeekListener();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) updateKeyIncrement();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isOwner()) return;
        if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else if (!isBuffering()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        onPlayingChanged(isPlaying);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (isOwner() && !usesPlaybackRequestEvents()) onStateChanged(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        if (isOwner()) onSizeChanged(size);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = ((PlaybackService.LocalBinder) binder).getService();
        mService.addPlayerCallback(mPlayerCallback);
        activateService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        initialized = false;
        mService = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        activateService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        claimBinding();
        setRedirect(false);
        dispatchPendingObservers();
        resumePlayback();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRedirect()) pausePlayback();
    }

    @Override
    protected void onStop() {
        super.onStop();
        boolean floatingWindowShowing = isFloatingWindowShowing();
        if (FloatingPlaybackPolicy.shouldPauseOnStop(isOwner(), floatingWindowShowing)) pausePlayback();
        if (!isInPictureInPictureMode()) detachPlayerView();
    }

    @Override
    protected void onDestroy() {
        clearObservers();
        detachPlayerView();
        super.onDestroy();
        releasePlaybackService();
    }

    private final class ServiceReadyObserver<T> implements Observer<T> {

        private final Observer<T> observer;
        private T pendingValue;
        private boolean pending;

        private ServiceReadyObserver(Observer<T> observer) {
            this.observer = observer;
        }

        @Override
        public void onChanged(T value) {
            if (!canDispatch()) {
                pendingValue = value;
                pending = true;
            } else {
                deliver(value);
            }
        }

        private void deliver(T value) {
            pendingValue = null;
            pending = false;
            observer.onChanged(value);
        }

        private void dispatch() {
            if (pending) deliver(pendingValue);
        }
    }
}
