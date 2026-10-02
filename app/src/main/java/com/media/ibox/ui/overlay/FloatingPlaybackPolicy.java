package com.media.ibox.ui.overlay;

public final class FloatingPlaybackPolicy {

    private FloatingPlaybackPolicy() {
    }

    public static boolean shouldPauseOnStop(boolean playbackOwner, boolean finishing,
                                            boolean backgroundOff, boolean floatingWindowShowing) {
        return playbackOwner && !floatingWindowShowing && (finishing || backgroundOff);
    }

    public static boolean shouldKeepServiceAlive(boolean hasMediaClient, boolean hasPlayerCallback,
                                                  boolean floatingWindowShowing) {
        return hasMediaClient || hasPlayerCallback || floatingWindowShowing;
    }

    public static boolean shouldAttachPlayerView(boolean floatingWindowShowing) {
        return !floatingWindowShowing;
    }
}
