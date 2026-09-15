package com.imedia.box.ui.overlay;

public final class FloatingWindowControlState {

    private FloatingWindowControlState() {
    }

    public static int progress(long positionMs, long durationMs, int max) {
        if (positionMs < 0 || durationMs <= 0 || max <= 0) return 0;
        double fraction = Math.min(positionMs, durationMs) / (double) durationMs;
        return (int) Math.round(fraction * max);
    }

    public static long seekPosition(int progress, int max, long durationMs) {
        if (max <= 0 || durationMs <= 0) return 0;
        double fraction = Math.clamp(progress, 0, max) / (double) max;
        return Math.round(fraction * durationMs);
    }

    public static boolean shouldAutoHide(boolean playing, boolean scrubbing) {
        return playing && !scrubbing;
    }
}
