package com.media.ibox.playback;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Surface;

import com.media.ibox.utils.ResUtil;

public final class PlaybackOrientation {

    public static int getScreenOrientation(Context context) {
        int rotation = ResUtil.getDisplay(context).getRotation();
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && rotation == Surface.ROTATION_90) return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    public static int getRotateOrientation(Context context) {
        if (ResUtil.isLand(context)) return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    public static boolean shouldRestoreMovieDetailOnRotate(boolean portraitLayout, boolean landscapeScreen) {
        return portraitLayout && landscapeScreen;
    }

    public static int getEnterFullscreenOrientation(boolean portraitVideo) {
        if (portraitVideo) return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    public static int getExitFullscreenOrientation(boolean portMode) {
        if (portMode) return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
    }

    public static int getLockOrientation(Context context, boolean lock, boolean rotate) {
        if (lock) return getScreenOrientation(context);
        return getPlayerOrientation(context, rotate);
    }

    public static int getLockOrientation(Context context, boolean lock, boolean rotate, boolean allowFullUser) {
        if (lock) return getScreenOrientation(context);
        if (!rotate && allowFullUser) return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        return getPlayerOrientation(context, rotate);
    }

    public static int getPortAutoRotateOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
    }

    public static int getLandAutoRotateOrientation() {
        return ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
    }

    public static int getPlayerOrientation(Context context, boolean rotate) {
        if (rotate || !ResUtil.isLand(context)) return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }
}
