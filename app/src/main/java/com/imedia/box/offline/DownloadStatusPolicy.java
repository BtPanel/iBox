package com.imedia.box.offline;

import androidx.media3.exoplayer.offline.Download;

public final class DownloadStatusPolicy {

    private DownloadStatusPolicy() {
    }

    public static boolean showSpeed(boolean always, int state, long speed) {
        return always || state == Download.STATE_DOWNLOADING && speed > 0;
    }

    public static long displayedSpeed(int state, long speed) {
        return state == Download.STATE_DOWNLOADING ? Math.max(0, speed) : 0;
    }

    public static int displayedProgress(int state, int progress) {
        return state == Download.STATE_COMPLETED ? 100 : Math.clamp(progress, 0, 100);
    }
}
