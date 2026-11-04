package com.media.ibox.ui.overlay;

public final class FloatingPlaybackPolicy {

    private FloatingPlaybackPolicy() {
    }

    /**
     * onStop 时是否需要暂停：离开播放页一律暂停（浮窗播放除外）。
     * 「悬浮返回播放」按钮开启时保留会话入口并显示悬浮按钮，点击按钮回到播放页继续；
     * 按钮关闭时不提供返回入口，直接保持暂停。
     */
    public static boolean shouldPauseOnStop(boolean playbackOwner, boolean floatingWindowShowing) {
        return playbackOwner && !floatingWindowShowing;
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
