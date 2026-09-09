package com.imedia.box.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class DownloadPolicyTest {

    @Test
    public void clampsValuesToSupportedRange() {
        DownloadPolicy low = new DownloadPolicy(false, -2, -4, -1);
        DownloadPolicy high = new DownloadPolicy(true, 10, 20, 20);

        assertFalse(low.wifiOnly());
        assertEquals(DownloadPolicy.MIN_PARALLEL, low.parallelDownloads());
        assertEquals(DownloadPolicy.MIN_SEGMENT_THREADS, low.segmentThreads());
        assertEquals(DownloadPolicy.MIN_RETRY, low.retryCount());
        assertEquals(DownloadPolicy.MAX_PARALLEL, high.parallelDownloads());
        assertEquals(DownloadPolicy.MAX_SEGMENT_THREADS, high.segmentThreads());
        assertEquals(DownloadPolicy.MAX_RETRY, high.retryCount());
    }
}
