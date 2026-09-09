package com.imedia.box.playback.vod;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeStatusTest {

    @Test
    public void recognizesCompletedRemarks() {
        assertTrue(EpisodeStatus.isComplete("\u7b2c24\u96c6\u5168"));
        assertTrue(EpisodeStatus.isComplete("\u516824\u96c6"));
        assertTrue(EpisodeStatus.isComplete("\u5df2\u5b8c\u7ed3"));
        assertTrue(EpisodeStatus.isComplete("24\u96c6 \u5b8c\u7d50"));
        assertTrue(EpisodeStatus.isComplete("\u5927\u7ed3\u5c40"));
    }

    @Test
    public void doesNotTreatUpdatingRemarksAsComplete() {
        assertFalse(EpisodeStatus.isComplete(null));
        assertFalse(EpisodeStatus.isComplete(""));
        assertFalse(EpisodeStatus.isComplete("\u66f4\u65b0\u81f3\u7b2c24\u96c6"));
        assertFalse(EpisodeStatus.isComplete("\u8fde\u8f7d\u4e2d"));
    }
}
