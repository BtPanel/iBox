package com.media.ibox.ui.overlay;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;

import com.media.ibox.R;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.SubtitleSetting;

public final class FloatingPlayerWindow implements Player.Listener {

    private static final long CONTROL_HIDE_DELAY_MS = 3_000;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500;

    private final Context context;
    private final int displayId;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onPrevious;
    private final Runnable onNext;
    private final Runnable onClose;
    private final Runnable updateProgress = this::updateProgressAndSchedule;
    private final Runnable hideControls = this::hideControls;

    private PendingIntent restoreIntent;
    private PendingIntent settingsIntent;
    private WindowManager.LayoutParams layoutParams;
    private View root;
    private View controls;
    private PlayerView playerView;
    private ImageButton playPause;
    private SeekBar seekBar;
    private Player player;
    private boolean scrubbing;
    private float videoAspectRatio = 16f / 9f;

    public FloatingPlayerWindow(Context context, int displayId, Runnable onPrevious, Runnable onNext,
                                Runnable onClose) {
        this.context = context;
        this.displayId = displayId;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.onPrevious = onPrevious;
        this.onNext = onNext;
        this.onClose = onClose;
    }

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public boolean isForDisplay(int displayId) {
        return this.displayId == displayId;
    }

    public boolean show(Player player, PendingIntent restoreIntent, PendingIntent settingsIntent,
                        int resizeMode, int accentColor) {
        if (!canDrawOverlays(context) || player == null) return false;
        if (isShowing()) {
            this.restoreIntent = restoreIntent;
            this.settingsIntent = settingsIntent;
            rebindPlayer(player);
            return true;
        }
        videoAspectRatio = sourceAspectRatio(player.getVideoSize());
        this.restoreIntent = restoreIntent;
        this.settingsIntent = settingsIntent;
        try {
            createView(resizeMode, accentColor);
            windowManager.addView(root, layoutParams);
        } catch (RuntimeException e) {
            releaseView();
            return false;
        }
        rebindPlayer(player);
        updatePlayPause();
        updateProgressAndSchedule();
        showControls();
        return true;
    }

    public boolean isShowing() {
        return root != null && root.isAttachedToWindow();
    }

    public void hide() {
        handler.removeCallbacksAndMessages(null);
        if (playerView != null) player = FloatingPlayerBinding.rebind(
                player, null, this, playerView::setPlayer);
        else if (player != null) player.removeListener(this);
        if (root != null && root.isAttachedToWindow()) {
            try {
                windowManager.removeViewImmediate(root);
            } catch (RuntimeException ignored) {
            }
        }
        releaseView();
    }

    private void createView(int resizeMode, int accentColor) {
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_App);
        root = LayoutInflater.from(themedContext).inflate(R.layout.view_floating_player, null);
        controls = root.findViewById(R.id.floatingControls);
        playerView = root.findViewById(R.id.floatingPlayer);
        playPause = root.findViewById(R.id.floatingPlayPause);
        seekBar = root.findViewById(R.id.floatingSeek);
        ColorStateList accent = ColorStateList.valueOf(accentColor);
        seekBar.setProgressTintList(accent);
        seekBar.setThumbTintList(accent);
        playerView.setRender(PlayerSetting.RENDER_TEXTURE);
        playerView.setResizeMode(resizeMode);
        SubtitleSetting.applyStyle(context, playerView.getSubtitleView());

        root.findViewById(R.id.floatingPrevious).setOnClickListener(view -> interact(onPrevious));
        root.findViewById(R.id.floatingNext).setOnClickListener(view -> interact(onNext));
        playPause.setOnClickListener(view -> interact(this::togglePlayback));
        root.findViewById(R.id.floatingClose).setOnClickListener(view -> close());
        root.findViewById(R.id.floatingFullscreen).setOnClickListener(view -> restoreFullscreen());
        root.findViewById(R.id.floatingSettings).setOnClickListener(view -> restoreSettings());
        seekBar.setOnSeekBarChangeListener(seekListener);
        View.OnTouchListener dragListener = new DragTouchListener();
        controls.setOnTouchListener(dragListener);
        playerView.setOnTouchListener(dragListener);
        root.findViewById(R.id.floatingSettings).setOnTouchListener(dragListener);
        root.findViewById(R.id.floatingClose).setOnTouchListener(dragListener);
        layoutParams = createLayoutParams();
    }

    private WindowManager.LayoutParams createLayoutParams() {
        Rect bounds = screenBounds();
        int margin = dp(12);
        int maxWidth = FloatingWindowGeometry.maxWidth(
                bounds.width(), bounds.height(), margin, videoAspectRatio);
        int minWidth = Math.min(dp(240), maxWidth);
        int width = Math.clamp(dp(360), minWidth, maxWidth);
        int height = FloatingWindowGeometry.heightForWidth(width, videoAspectRatio);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(margin, bounds.width() - width - margin);
        params.y = Math.max(margin, bounds.height() - height - dp(88));
        return params;
    }

    private Rect screenBounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.getCurrentWindowMetrics().getBounds();
        }
        return new Rect(0, 0, context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
    }

    private void interact(Runnable action) {
        action.run();
        showControls();
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void close() {
        hide();
        onClose.run();
    }

    private void restoreFullscreen() {
        restore(restoreIntent);
    }

    private void restoreSettings() {
        restore(settingsIntent);
    }

    private void restore(PendingIntent intent) {
        if (intent == null) return;
        try {
            intent.send();
            hide();
        } catch (PendingIntent.CanceledException ignored) {
            showControls();
        }
    }

    private void toggleControls() {
        if (controls.getVisibility() == View.VISIBLE) hideControls();
        else showControls();
    }

    private void showControls() {
        if (controls == null) return;
        controls.setVisibility(View.VISIBLE);
        scheduleControlHide();
    }

    private void hideControls() {
        if (controls != null && !scrubbing) controls.setVisibility(View.INVISIBLE);
    }

    private void scheduleControlHide() {
        handler.removeCallbacks(hideControls);
        if (player != null && FloatingWindowControlState.shouldAutoHide(player.isPlaying(), scrubbing)) {
            handler.postDelayed(hideControls, CONTROL_HIDE_DELAY_MS);
        }
    }

    private void updateProgressAndSchedule() {
        handler.removeCallbacks(updateProgress);
        if (player == null || seekBar == null) return;
        long duration = player.getDuration();
        seekBar.setEnabled(duration != C.TIME_UNSET && duration > 0);
        if (!scrubbing) {
            seekBar.setProgress(FloatingWindowControlState.progress(
                    player.getCurrentPosition(), duration, seekBar.getMax()));
        }
        handler.postDelayed(updateProgress, PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void updatePlayPause() {
        if (player == null || playPause == null) return;
        boolean playing = player.isPlaying();
        playPause.setImageResource(playing ? R.drawable.ic_msr_pause : R.drawable.ic_msr_play_arrow);
        playPause.setContentDescription(context.getString(playing ? R.string.pause : R.string.play));
        scheduleControlHide();
    }

    private void releaseView() {
        player = null;
        playerView = null;
        playPause = null;
        seekBar = null;
        controls = null;
        root = null;
        layoutParams = null;
        restoreIntent = null;
        settingsIntent = null;
        scrubbing = false;
        videoAspectRatio = 16f / 9f;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPause();
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        updateProgressAndSchedule();
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        float aspectRatio = updatedAspectRatio(videoSize);
        if (Math.abs(videoAspectRatio - aspectRatio) < 0.001f) return;
        videoAspectRatio = aspectRatio;
        resizeBy(1f);
    }

    public void rebindPlayer(Player newPlayer) {
        if (newPlayer == null || playerView == null) return;
        player = FloatingPlayerBinding.rebind(player, newPlayer, this, playerView::setPlayer);
        videoAspectRatio = updatedAspectRatio(newPlayer.getVideoSize());
        updatePlayPause();
        updateProgressAndSchedule();
        resizeBy(1f);
    }

    private float sourceAspectRatio(VideoSize videoSize) {
        return FloatingWindowGeometry.sourceAspectRatio(
                videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio);
    }

    private float updatedAspectRatio(VideoSize videoSize) {
        return FloatingWindowGeometry.updatedAspectRatio(videoAspectRatio,
                videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio);
    }

    private void resizeBy(float scaleFactor) {
        if (layoutParams == null || root == null) return;
        Rect bounds = screenBounds();
        int margin = dp(12);
        int maxWidth = FloatingWindowGeometry.maxWidth(
                bounds.width(), bounds.height(), margin, videoAspectRatio);
        int minWidth = Math.min(dp(240), maxWidth);
        int oldWidth = layoutParams.width;
        int oldHeight = layoutParams.height;
        int newWidth = FloatingWindowGeometry.scaleWidth(
                oldWidth, scaleFactor, minWidth, maxWidth);
        int newHeight = FloatingWindowGeometry.heightForWidth(newWidth, videoAspectRatio);
        if (newWidth == oldWidth && newHeight == oldHeight) return;
        layoutParams.x = FloatingWindowGeometry.centeredPosition(
                layoutParams.x, oldWidth, newWidth, bounds.width());
        layoutParams.y = FloatingWindowGeometry.centeredPosition(
                layoutParams.y, oldHeight, newHeight, bounds.height());
        layoutParams.width = newWidth;
        layoutParams.height = newHeight;
        updateLayout();
    }

    private void updateLayout() {
        try {
            windowManager.updateViewLayout(root, layoutParams);
            root.invalidateOutline();
        } catch (RuntimeException ignored) {
            hide();
        }
    }

    private final SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            scrubbing = true;
            showControls();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (player != null) {
                player.seekTo(FloatingWindowControlState.seekPosition(
                        seekBar.getProgress(), seekBar.getMax(), player.getDuration()));
            }
            scrubbing = false;
            updateProgressAndSchedule();
            scheduleControlHide();
        }
    };

    private final class DragTouchListener implements View.OnTouchListener {

        private final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        private final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        scaling = true;
                        dragging = false;
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        FloatingPlayerWindow.this.resizeBy(detector.getScaleFactor());
                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                        showControls();
                    }
                });
        private float downRawX;
        private float downRawY;
        private int downWindowX;
        private int downWindowY;
        private int downWindowWidth;
        private int downWindowHeight;
        private int resizeCorner;
        private boolean dragging;
        private boolean scaling;

        @Override
        public boolean onTouch(View view, @NonNull MotionEvent event) {
            if (layoutParams == null) return false;
            scaleDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                scaling = true;
                dragging = false;
                resizeCorner = FloatingWindowGeometry.CORNER_NONE;
            }
            if (scaling || scaleDetector.isInProgress() || event.getPointerCount() > 1) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) scaling = false;
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downWindowX = layoutParams.x;
                    downWindowY = layoutParams.y;
                    downWindowWidth = layoutParams.width;
                    downWindowHeight = layoutParams.height;
                    float windowX = event.getRawX() - layoutParams.x;
                    float windowY = event.getRawY() - layoutParams.y;
                    resizeCorner = FloatingWindowGeometry.cornerAt(windowX, windowY,
                            layoutParams.width, layoutParams.height, dp(24));
                    dragging = false;
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    if (!dragging && Math.hypot(dx, dy) >= touchSlop) dragging = true;
                    if (dragging && resizeCorner != FloatingWindowGeometry.CORNER_NONE) {
                        resizeFromCorner(dx, dy);
                    } else if (dragging) {
                        moveTo(downWindowX + dx, downWindowY + dy);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP -> {
                    if (!dragging && (view.getId() == R.id.floatingSettings
                            || view.getId() == R.id.floatingClose)) view.performClick();
                    else if (!dragging) toggleControls();
                    resizeCorner = FloatingWindowGeometry.CORNER_NONE;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL -> {
                    dragging = false;
                    resizeCorner = FloatingWindowGeometry.CORNER_NONE;
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private void resizeFromCorner(int dx, int dy) {
            boolean fromLeft = resizeCorner == FloatingWindowGeometry.CORNER_TOP_LEFT
                    || resizeCorner == FloatingWindowGeometry.CORNER_BOTTOM_LEFT;
            boolean fromTop = resizeCorner == FloatingWindowGeometry.CORNER_TOP_LEFT
                    || resizeCorner == FloatingWindowGeometry.CORNER_TOP_RIGHT;
            int requestedWidth = downWindowWidth + Math.round(
                    FloatingWindowGeometry.projectWidthDelta(dx, dy, videoAspectRatio,
                            !fromLeft, !fromTop));
            Rect bounds = screenBounds();
            int margin = dp(12);
            int maxWidth = FloatingWindowGeometry.maxWidth(
                    bounds.width(), bounds.height(), margin, videoAspectRatio);
            int minWidth = Math.min(dp(240), maxWidth);
            int newWidth = Math.clamp(requestedWidth, minWidth, maxWidth);
            int newHeight = FloatingWindowGeometry.heightForWidth(newWidth, videoAspectRatio);
            layoutParams.x = FloatingWindowGeometry.anchoredPosition(
                    downWindowX, downWindowWidth, newWidth, fromLeft, bounds.width());
            layoutParams.y = FloatingWindowGeometry.anchoredPosition(
                    downWindowY, downWindowHeight, newHeight, fromTop, bounds.height());
            layoutParams.width = newWidth;
            layoutParams.height = newHeight;
            FloatingPlayerWindow.this.updateLayout();
        }

        private void moveTo(int x, int y) {
            Rect bounds = screenBounds();
            layoutParams.x = Math.clamp(x, 0, Math.max(0, bounds.width() - layoutParams.width));
            layoutParams.y = Math.clamp(y, 0, Math.max(0, bounds.height() - layoutParams.height));
            FloatingPlayerWindow.this.updateLayout();
        }
    }
}
