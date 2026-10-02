package com.media.ibox.offline;

public record DownloadPolicy(boolean wifiOnly, int parallelDownloads, int segmentThreads, int retryCount) {

    public static final int MIN_PARALLEL = 1;
    public static final int MAX_PARALLEL = 8;
    public static final int MIN_SEGMENT_THREADS = 1;
    public static final int MAX_SEGMENT_THREADS = 16;
    public static final int MIN_RETRY = 0;
    public static final int MAX_RETRY = 5;

    public DownloadPolicy {
        parallelDownloads = Math.clamp(parallelDownloads, MIN_PARALLEL, MAX_PARALLEL);
        segmentThreads = Math.clamp(segmentThreads, MIN_SEGMENT_THREADS, MAX_SEGMENT_THREADS);
        retryCount = Math.clamp(retryCount, MIN_RETRY, MAX_RETRY);
    }
}
