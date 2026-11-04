package com.media.ibox.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Rational;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.media3.ui.R;

import com.media.ibox.App;
import com.media.ibox.event.ActionEvent;
import com.media.ibox.receiver.ActionReceiver;
import com.media.ibox.setting.PlayerSetting;

import java.util.ArrayList;
import java.util.List;

public class PiP {

    private PictureInPictureParams.Builder builder;

    public static boolean noPiP() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !App.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    public PiP() {
        if (noPiP()) return;
        this.builder = new PictureInPictureParams.Builder();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private RemoteAction buildRemoteAction(Activity activity, @DrawableRes int icon, @StringRes int title, String action) {
        return new RemoteAction(Icon.createWithResource(activity, icon), activity.getString(title), "", ActionReceiver.getPendingIntent(activity, action));
    }

    private RemoteAction getPlayPauseAction(Activity activity, boolean play) {
        if (play) return buildRemoteAction(activity, com.media.ibox.R.drawable.ic_msr_pause, R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildRemoteAction(activity, com.media.ibox.R.drawable.ic_msr_play_arrow, R.string.exo_controls_play_description, ActionEvent.PLAY);
    }

    private RemoteAction getAction(Activity activity, String action, boolean play) {
        if (ActionEvent.PREV.equals(action)) {
            return buildRemoteAction(activity, com.media.ibox.R.drawable.ic_msr_skip_previous, R.string.exo_controls_previous_description, action);
        }
        if (ActionEvent.NEXT.equals(action)) {
            return buildRemoteAction(activity, com.media.ibox.R.drawable.ic_msr_skip_next, R.string.exo_controls_next_description, action);
        }
        return getPlayPauseAction(activity, play);
    }

    public void update(Activity activity, View view) {
        try {
            if (noPiP()) return;
            Rect rect = new Rect();
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty()) return;
            builder.setSourceRectHint(rect);
            activity.setPictureInPictureParams(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update(Activity activity, boolean play) {
        try {
            if (noPiP()) return;
            List<RemoteAction> actions = new ArrayList<>();
            for (String action : PiPControlState.actions(play)) actions.add(getAction(activity, action, play));
            activity.setPictureInPictureParams(builder.setActions(actions).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void enter(Activity activity, int width, int height, int scale) {
        enter(activity, width, height, scale, false);
    }

    public void enterManual(Activity activity, int width, int height, int scale) {
        enter(activity, width, height, scale, true);
    }

    private void enter(Activity activity, int width, int height, int scale, boolean manual) {
        try {
            if (noPiP() || activity.isInPictureInPictureMode()) return;
            boolean backgroundEnabled = PlayerSetting.isBackgroundPiP();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(!manual && backgroundEnabled);
                builder.setSeamlessResizeEnabled(true);
                activity.setPictureInPictureParams(builder.build());
            }
            if (!manual && !backgroundEnabled) return;
            // Android 12+：不锁定 aspectRatio，让 PiP 窗口进入后能自由拖拽四角缩放；
            // 初始比例仍由视频比例决定（sourceRectHint 已在 update() 中设置），
            // setSeamlessResizeEnabled(true) 负责缩放时的平滑过渡。
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (scale == 1) builder.setAspectRatio(new Rational(16, 9));
                else if (scale == 2) builder.setAspectRatio(new Rational(4, 3));
                else builder.setAspectRatio(getRational(width, height));
            }
            activity.enterPictureInPictureMode(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Rational getRational(int width, int height) {
        if (width <= 0 || height <= 0) return new Rational(16, 9);
        Rational limitWide = new Rational(239, 100);
        Rational limitTall = new Rational(100, 239);
        Rational rational = new Rational(width, height);
        if (rational.isInfinite()) return new Rational(16, 9);
        if (rational.floatValue() > limitWide.floatValue()) return limitWide;
        if (rational.floatValue() < limitTall.floatValue()) return limitTall;
        return rational;
    }
}
