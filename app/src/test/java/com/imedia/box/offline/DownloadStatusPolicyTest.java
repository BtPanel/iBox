package com.imedia.box.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.offline.Download;
import org.junit.Test;

public class DownloadStatusPolicyTest {

    @Test
    public void mobileKeepsSpeedVisibleForEveryTaskState() {
        for (int state : new int[]{Download.STATE_QUEUED, Download.STATE_DOWNLOADING,
                Download.STATE_STOPPED, Download.STATE_COMPLETED, Download.STATE_FAILED}) {
            assertTrue(DownloadStatusPolicy.showSpeed(true, state, 0));
        }
    }

    @Test
    public void nonTransferringTasksNeverShowStaleMeasuredSpeed() {
        assertEquals(0, DownloadStatusPolicy.displayedSpeed(Download.STATE_QUEUED, 1_000));
        assertEquals(0, DownloadStatusPolicy.displayedSpeed(Download.STATE_COMPLETED, 1_000));
        assertEquals(0, DownloadStatusPolicy.displayedSpeed(Download.STATE_DOWNLOADING, -1));
        assertEquals(1_000, DownloadStatusPolicy.displayedSpeed(Download.STATE_DOWNLOADING, 1_000));
    }

    @Test
    public void tvKeepsItsExistingSpeedVisibility() {
        assertFalse(DownloadStatusPolicy.showSpeed(false, Download.STATE_QUEUED, 0));
        assertFalse(DownloadStatusPolicy.showSpeed(false, Download.STATE_DOWNLOADING, 0));
        assertTrue(DownloadStatusPolicy.showSpeed(false, Download.STATE_DOWNLOADING, 1_000));
    }

    @Test
    public void completedDownloadStillDisplaysOneHundredPercent() {
        assertEquals(100, DownloadStatusPolicy.displayedProgress(Download.STATE_COMPLETED, 0));
        assertEquals(44, DownloadStatusPolicy.displayedProgress(Download.STATE_STOPPED, 44));
    }
}
