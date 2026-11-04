package com.media.ibox.ui.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.media.ibox.R;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.player.PlayerManager;
import com.media.ibox.server.Server;
import com.media.ibox.service.PlaybackService;
import com.media.ibox.ui.activity.LiveActivity;
import com.media.ibox.ui.activity.PlaybackActivity;
import com.media.ibox.ui.activity.VideoActivity;

/**
 * 悬浮「返回播放」按钮：离开播放页后（媒体已暂停、保留会话）在所有非播放页显示，
 * 可拖动并吸附到屏幕左右边缘（位置持久化），点击一键返回对应播放页继续。
 */
public final class PlaybackResumeButton {

    private static final String PREFS = "playback_resume_button";
    private static final String KEY_EDGE = "edge";          // 0=左, 1=右
    private static final String KEY_OFFSET_Y = "offset_y";  // 相对顶部的像素偏移

    private PlaybackResumeButton() {
    }

    public static void attach(Activity activity) {
        View existing = activity.findViewById(R.id.playbackResumeFab);
        if (existing != null) {
            refresh(activity);
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        ImageView fab = createFab(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = initialLeft(activity, lp.width);
        lp.topMargin = initialTop(activity);
        content.addView(fab, lp);
        refresh(activity);
    }

    public static void detach(Activity activity) {
        View fab = activity.findViewById(R.id.playbackResumeFab);
        if (fab != null && fab.getParent() instanceof ViewGroup parent) parent.removeView(fab);
    }

    public static void refresh(Activity activity) {
        View fab = activity.findViewById(R.id.playbackResumeFab);
        if (fab == null) return;
        boolean onPlaybackPage = activity instanceof PlaybackActivity;
        fab.setVisibility(!onPlaybackPage && hasResumablePlayback() ? View.VISIBLE : View.GONE);
    }

    private static ImageView createFab(Activity activity) {
        ImageView fab = new ImageView(activity);
        fab.setId(R.id.playbackResumeFab);
        fab.setContentDescription(activity.getString(R.string.nav_resume));
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setImageResource(R.drawable.ic_resume_play);
        // 主色容器 + 深色图标：对比度与整体主题一致，视觉更精致。
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(activity.getColor(R.color.ibox_primary));
        fab.setBackground(bg);
        fab.setElevation(dp(activity, 8));
        fab.setStateListAnimator(null);
        fab.setClipToOutline(true);
        fab.setOutlineProvider(new ViewOutlineProvider());
        fab.setOnClickListener(view -> openResume(activity));
        fab.setOnTouchListener(new DragTouchListener(activity, fab));
        return fab;
    }

    private static final class ViewOutlineProvider extends android.view.ViewOutlineProvider {
        @Override
        public void getOutline(View view, android.graphics.Outline outline) {
            int d = Math.min(view.getWidth(), view.getHeight());
            outline.setOval(0, 0, d, d);
        }
    }

    private static void openResume(Activity activity) {
        PlaybackService service = Server.get().getService();
        if (service == null) return;
        PlayerManager player = service.player();
        if (player == null || !player.hasPlaySpec() || player.isEmpty()) return;
        if (player.isLive()) {
            LiveActivity.start(activity);
        } else {
            startVod(activity, player);
        }
    }

    private static void startVod(Activity activity, PlayerManager player) {
        String[] parts = splitVodKey(player.getKey());
        String key = parts.length > 0 ? parts[0] : "";
        String id = parts.length > 1 ? parts[1] : "";
        String name = player.getMediaTitle();
        if (key.isEmpty() && id.isEmpty()) return;
        // 恢复已有播放：关闭 initialDetailSearch，避免详情异常时误跳搜索页。
        VideoActivity.startCached(activity, key, id, name, "", null, false, false);
    }

    private static String[] splitVodKey(String key) {
        if (key == null || key.isEmpty()) return new String[]{"", ""};
        String[] parts = key.split("\\Q" + AppDatabase.SYMBOL + "\\E");
        return parts.length == 0 ? new String[]{"", ""} : parts;
    }

    /**
     * 后台是否有可恢复的播放会话：开关开启 + 服务在跑 + 有可恢复媒体。
     * 离开播放页即暂停，暂停状态下同样显示悬浮按钮，点击返回播放页继续。
     */
    private static boolean hasResumablePlayback() {
        if (!com.media.ibox.setting.Setting.isResumeButtonEnabled()) return false;
        PlaybackService service = Server.get().getService();
        if (service == null) return false;
        PlayerManager player = service.player();
        return player != null && player.hasPlaySpec() && !player.isEmpty();
    }

    private static int initialLeft(Activity activity, int width) {
        int edge = prefs(activity).getInt(KEY_EDGE, 0);
        int margin = edgeMargin(activity);
        if (edge == 0) return margin;
        return screenWidth(activity) - width - margin;
    }

    private static int edgeMargin(Context context) {
        return dp(context, 6);
    }

    private static int initialTop(Activity activity) {
        int saved = prefs(activity).getInt(KEY_OFFSET_Y, -1);
        if (saved >= 0) return saved;
        // 默认：左边靠下约 1/3 处（自屏幕底部往上 1/3 高度）。
        return screenHeight(activity) * 2 / 3;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    private static int screenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class DragTouchListener implements View.OnTouchListener {

        private final Activity activity;
        private final View fab;
        private final int touchSlop;
        private float downRawX;
        private float downRawY;
        private int downLeft;
        private int downTop;
        private boolean dragging;

        DragTouchListener(Activity activity, View fab) {
            this.activity = activity;
            this.fab = fab;
            this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    FrameLayout.LayoutParams lp = lp();
                    downLeft = lp.leftMargin;
                    downTop = lp.topMargin;
                    dragging = false;
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    if (!dragging && Math.hypot(dx, dy) >= touchSlop) {
                        dragging = true;
                        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
                    }
                    if (dragging) moveTo(downLeft + dx, downTop + dy);
                    return true;
                }
                case MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge();
                        view.animate().scaleX(1f).scaleY(1f)
                                .setInterpolator(new OvershootInterpolator(1.6f))
                                .setDuration(220).start();
                    } else {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        view.performClick();
                    }
                    dragging = false;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL -> {
                    dragging = false;
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private FrameLayout.LayoutParams lp() {
            return (FrameLayout.LayoutParams) fab.getLayoutParams();
        }

        private void moveTo(int left, int top) {
            FrameLayout.LayoutParams lp = lp();
            int maxX = Math.max(0, screenWidth(activity) - fab.getWidth());
            int maxY = Math.max(0, screenHeight(activity) - fab.getHeight());
            lp.leftMargin = Math.clamp(left, 0, maxX);
            lp.topMargin = Math.clamp(top, 0, maxY);
            fab.setLayoutParams(lp);
        }

        private void snapToEdge() {
            FrameLayout.LayoutParams lp = lp();
            int centerX = lp.leftMargin + fab.getWidth() / 2;
            int half = screenWidth(activity) / 2;
            int margin = edgeMargin(activity);
            int edge = centerX < half ? 0 : 1;
            lp.leftMargin = edge == 0 ? margin : screenWidth(activity) - fab.getWidth() - margin;
            fab.setLayoutParams(lp);
            prefs(activity).edit()
                    .putInt(KEY_EDGE, edge)
                    .putInt(KEY_OFFSET_Y, lp.topMargin)
                    .apply();
        }
    }
}
