package com.media.ibox.utils;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.databinding.ViewProgressBinding;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

public class Notify {

    public static final String DEFAULT = "default";
    public static final int ID = 9527;
    private AlertDialog mDialog;
    private Toast mToast;
    private String mCompactText;
    private long mCompactTime;

    private static class Loader {
        static volatile Notify INSTANCE = new Notify();
    }

    private static Notify get() {
        return Loader.INSTANCE;
    }

    public static void createChannel() {
        NotificationManagerCompat notifyMgr = NotificationManagerCompat.from(App.get());
        notifyMgr.createNotificationChannel(new NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW).setName("TV").build());
    }

    public static String getError(int resId, Throwable e) {
        if (TextUtils.isEmpty(e.getMessage())) return ResUtil.getString(resId);
        return ResUtil.getString(resId) + "\n" + e.getMessage();
    }

    public static void show(Notification notification) {
        if (ContextCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(App.get()).notify(ID, notification);
    }

    public static void show(int resId) {
        if (resId != 0) show(ResUtil.getString(resId));
    }

    public static void show(String text) {
        if (!TextUtils.isEmpty(text)) get().makeText(text);
    }

    public static void showCompact(int resId) {
        if (resId != 0) showCompact(ResUtil.getString(resId));
    }

    public static void showCompact(String text) {
        if (!TextUtils.isEmpty(text)) get().makeCompactText(text);
    }

    public static void progress(Context context) {
        dismiss();
        get().create(context);
    }

    public static void dismiss() {
        try {
            if (get().mDialog != null) get().mDialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void create(Context context) {
        ViewProgressBinding binding = ViewProgressBinding.inflate(LayoutInflater.from(context));
        mDialog = new IBoxAlertDialogBuilder(context).setView(binding.getRoot()).create();
        mDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        mDialog.show();
    }

    private void makeText(String text) {
        renderText(text, Toast.LENGTH_LONG);
    }

    private void makeCompactText(String text) {
        long now = SystemClock.elapsedRealtime();
        if (text.equals(mCompactText) && now - mCompactTime < 1500) return;
        mCompactText = text;
        mCompactTime = now;
        renderText(text, Toast.LENGTH_SHORT);
    }

    @SuppressWarnings("deprecation")
    private void renderText(String text, int duration) {
        if (mToast != null) mToast.cancel();
        View view = LayoutInflater.from(App.get()).inflate(R.layout.view_toast_compact, null);
        TextView message = view.findViewById(R.id.text);
        message.setText(text);
        int availableWidth = App.get().getResources().getDisplayMetrics().widthPixels
                - 2 * App.get().getResources().getDimensionPixelSize(R.dimen.ibox_notification_horizontal_margin);
        message.setMaxWidth(Math.min(message.getMaxWidth(), Math.max(1, availableWidth)));
        mToast = new Toast(App.get());
        mToast.setDuration(duration);
        mToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                App.get().getResources().getDimensionPixelSize(R.dimen.ibox_notification_bottom_offset));
        mToast.setView(view);
        SubscriptionToastGuard.show(mToast);
    }
}
