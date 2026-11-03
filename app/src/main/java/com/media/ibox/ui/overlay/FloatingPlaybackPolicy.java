package com.media.ibox.ui.overlay;

public final class FloatingPlaybackPolicy {

    private FloatingPlaybackPolicy() {
    }

    /**
     * onStop 时是否需要暂停：
     * 仅在「背景播放关闭」时暂停（无论是切后台还是退出页面）；
     * 背景播放开启时保持播放，由前台服务接续（退出页面可继续听，通知栏可控）。
     */
    public static boolean shouldPauseOnStop(boolean playbackOwner, boolean backgroundOff, boolean floatingWindowShowing) {
        return playbackOwner && !floatingWindowShowing && backgroundOff;
    }

    /**
     * 页面销毁后服务是否保活：
     * 媒体客户端（网页遥控等）、其他页面回调、浮窗、或本页开启背景播放时保活。
     */
    public static boolean shouldKeepServiceAlive(boolean hasMediaClient, boolean hasPlayerCallback,
                                                 boolean floatingWindowShowing, boolean backgroundPlayback) {
        return hasMediaClient || hasPlayerCallback || floatingWindowShowing || backgroundPlayback;
    }

    public static boolean shouldAttachPlayerView(boolean floatingWindowShowing) {
        return !floatingWindowShowing;
    }
}
