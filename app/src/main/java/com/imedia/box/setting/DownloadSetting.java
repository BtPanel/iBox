package com.imedia.box.setting;

import com.github.catvod.utils.Prefers;
import com.imedia.box.offline.DownloadPolicy;

public final class DownloadSetting {

    private DownloadSetting() {
    }

    public static DownloadPolicy get() {
        return new DownloadPolicy(
                Prefers.getBoolean("download_wifi_only"),
                Prefers.getInt("download_parallel", 2),
                Prefers.getInt("download_segment_threads", 4),
                Prefers.getInt("download_retry_count", 5));
    }

    public static void put(DownloadPolicy policy) {
        Prefers.put("download_wifi_only", policy.wifiOnly());
        Prefers.put("download_parallel", policy.parallelDownloads());
        Prefers.put("download_segment_threads", policy.segmentThreads());
        Prefers.put("download_retry_count", policy.retryCount());
    }
}
