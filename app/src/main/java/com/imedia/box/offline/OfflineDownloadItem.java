package com.imedia.box.offline;

import androidx.media3.exoplayer.offline.Download;

public record OfflineDownloadItem(Download download, OfflineDownloadMetadata metadata) {

    public static OfflineDownloadItem from(Download download) {
        return new OfflineDownloadItem(download, OfflineDownloadMetadata.from(download.request.data));
    }

    public boolean isActive() {
        return download.state == Download.STATE_QUEUED
                || download.state == Download.STATE_DOWNLOADING
                || download.state == Download.STATE_RESTARTING
                || download.state == Download.STATE_STOPPED;
    }

    public boolean isCompleted() {
        return download.state == Download.STATE_COMPLETED;
    }

    public boolean isFailed() {
        return download.state == Download.STATE_FAILED;
    }

    public boolean isPaused() {
        return download.state == Download.STATE_STOPPED && download.stopReason == OfflineDownloadManager.STOP_REASON_PAUSED;
    }

    public int progress() {
        float percent = download.getPercentDownloaded();
        return percent < 0 ? 0 : Math.clamp(Math.round(percent), 0, 100);
    }
}
