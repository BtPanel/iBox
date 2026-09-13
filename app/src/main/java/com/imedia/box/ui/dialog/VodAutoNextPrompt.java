package com.imedia.box.ui.dialog;

import android.app.Activity;
import android.os.CountDownTimer;

import androidx.appcompat.app.AlertDialog;

import com.imedia.box.R;
import com.imedia.box.bean.Episode;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;

public final class VodAutoNextPrompt {

    private static final int COUNTDOWN_SECONDS = 8;

    private final Activity activity;
    private final Runnable confirm;
    private final Runnable cancel;
    private AlertDialog dialog;
    private CountDownTimer timer;

    public VodAutoNextPrompt(Activity activity, Runnable confirm, Runnable cancel) {
        this.activity = activity;
        this.confirm = confirm;
        this.cancel = cancel;
    }

    public void show(Episode episode) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog next = new IBoxAlertDialogBuilder(activity)
                .setTitle(R.string.vod_auto_next_title)
                .setPositiveButton(R.string.vod_auto_next_play_now, (dialog, which) -> confirm.run())
                .setNegativeButton(R.string.dialog_negative, (dialog, which) -> cancel.run())
                .setOnCancelListener(dialog -> cancel.run())
                .create();
        dialog = next;
        next.show();
        next.setMessage(activity.getString(R.string.vod_auto_next_message, episode.getName(), COUNTDOWN_SECONDS));
        timer = new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (dialog == next) next.setMessage(activity.getString(R.string.vod_auto_next_message,
                        episode.getName(), (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
                if (dialog == next) confirm.run();
            }
        }.start();
    }

    public void dismiss() {
        if (timer != null) timer.cancel();
        timer = null;
        AlertDialog previous = dialog;
        dialog = null;
        if (previous != null) {
            previous.setOnCancelListener(null);
            previous.dismiss();
        }
    }
}
