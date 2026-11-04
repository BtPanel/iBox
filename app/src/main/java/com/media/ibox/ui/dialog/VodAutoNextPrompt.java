package com.media.ibox.ui.dialog;

import android.app.Activity;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.media.ibox.R;
import com.media.ibox.bean.Episode;

/**
 * 自动连播提示。
 * <p>
 * 以**非模态**浮层的形式显示在屏幕中下方（与 {@code Notify} 的提示同高），
 * 不抢占焦点、不打断遥控器或手势操作，倒计时结束后自动播放下一集。
 * 参考主流视频软件的做法：用户无需任何操作即可连播，仅在被明确取消时才停止。
 * <p>
 * 与旧实现（模态 AlertDialog）的区别：不再需要用户点“确定”，
 * 也不会因为对话框持有焦点而打断观看。
 * <p>
 * 注意：倒计时**先于**浮层启动 —— 自动连播是核心行为，即使浮层因为
 * 窗口令牌等原因没能显示，到点依然会切下一集。
 */
public final class VodAutoNextPrompt {

    /** 倒计时秒数：到点自动播放下一集。 */
    private static final int COUNTDOWN_SECONDS = 3;

    private final Activity activity;
    private final Runnable confirm;
    private final Runnable cancel;
    private PopupWindow popup;
    private CountDownTimer timer;
    /** 每次 show 递增，用于丢弃被取代的旧倒计时回调。 */
    private int generation;

    public VodAutoNextPrompt(Activity activity, Runnable confirm, Runnable cancel) {
        this.activity = activity;
        this.confirm = confirm;
        this.cancel = cancel;
    }

    public void show(Episode episode) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        final int generation = ++this.generation;

        View content = LayoutInflater.from(activity).inflate(R.layout.view_auto_next_tip, null, false);
        TextView message = content.findViewById(R.id.message);
        // 点提示本体 = 立即播放，省去等待
        content.findViewById(R.id.body).setOnClickListener(view -> {
            dismiss();
            confirm.run();
        });
        content.findViewById(R.id.cancel).setOnClickListener(view -> {
            cancel.run();
            dismiss();
        });
        message.setText(activity.getString(R.string.vod_auto_next_message, episode.getName(), COUNTDOWN_SECONDS));

        // 先起倒计时：自动播放是核心行为，不能因为浮层显示失败而一起丢掉。
        timer = new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (generation != VodAutoNextPrompt.this.generation) return;
                message.setText(activity.getString(R.string.vod_auto_next_message,
                        episode.getName(), (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
                if (generation != VodAutoNextPrompt.this.generation) return;
                dismiss();
                confirm.run();
            }
        }.start();

        // 再尝试显示浮层；失败只影响提示本身，上面的倒计时照常走。
        PopupWindow window = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawable(null);
        // 不抢焦点：遥控器/按键继续交给播放器处理，避免打断观看
        window.setFocusable(false);
        window.setOutsideTouchable(false);
        // 仍接收触摸，手机端可以直接点“取消”
        window.setTouchable(true);
        popup = window;
        try {
            window.showAtLocation(activity.findViewById(android.R.id.content),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, bottomMargin());
        } catch (Exception ignored) {
            popup = null;
        }
    }


    /**
     * 距屏幕底边的高度：复用 {@code ibox_notification_bottom_offset}，
     * 与 {@code Notify} 的"准备播放：xxx"提示落在同一高度，
     * 同时避开播放器底部的控制栏与进度条。
     */
    private int bottomMargin() {
        return activity.getResources().getDimensionPixelSize(R.dimen.ibox_notification_bottom_offset);
    }

    public void dismiss() {
        generation++;
        if (timer != null) timer.cancel();
        timer = null;
        PopupWindow previous = popup;
        popup = null;
        if (previous != null) previous.dismiss();
    }

    /**
     * 提示是否正在显示。
     * <p>
     * 浮层不抢焦点（否则会打断遥控器操作），所以按键由宿主 Activity 转发过来，
     * 转发前先问这里。
     */
    public boolean isShowing() {
        return popup != null;
    }

    /**
     * 处理遥控器/按键事件，交给宿主 Activity 的 {@code dispatchKeyEvent} 调用。
     * <ul>
     *   <li>返回键：取消本次连播</li>
     *   <li>确定/回车：不等倒计时，立即播放下一集</li>
     * </ul>
     *
     * @return true 表示事件已被消费，宿主不应再处理
     */
    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing() || event.getAction() != KeyEvent.ACTION_UP) return false;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                cancel.run();
                dismiss();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                dismiss();
                confirm.run();
                return true;
            default:
                return false;
        }
    }

    /**
     * 提示显示中则取消连播，供没有 KeyEvent 的返回入口（如手机的
     * {@code onBackInvoked}）调用。
     *
     * @return true 表示已消费这次返回
     */
    public boolean cancelIfShowing() {
        if (!isShowing()) return false;
        cancel.run();
        dismiss();
        return true;
    }
}
