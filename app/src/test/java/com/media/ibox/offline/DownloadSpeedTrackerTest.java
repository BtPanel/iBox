package com.media.ibox.offline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Set;

public class DownloadSpeedTrackerTest {

    @Test
    public void calculatesBytesPerSecondFromStableSamples() {
        DownloadSpeedTracker tracker = new DownloadSpeedTracker();
        assertEquals(0, tracker.update("one", 1_000, 1_000));
        assertEquals(2_000, tracker.update("one", 3_000, 2_000));
    }

    @Test
    public void keepsLastSpeedBetweenRefreshSamplesAndResetsRemovedItems() {
        DownloadSpeedTracker tracker = new DownloadSpeedTracker();
        tracker.update("one", 1_000, 1_000);
        assertEquals(2_000, tracker.update("one", 3_000, 2_000));
        assertEquals(2_000, tracker.update("one", 3_200, 2_200));
        tracker.retain(Set.of());
        assertEquals(0, tracker.update("one", 4_000, 3_000));
    }
}
