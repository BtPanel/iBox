package com.imedia.box.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class OfflineArtworkManagerTest {

    @Test
    public void reusesArtworkForEpisodesFromTheSameTitle() {
        assertEquals(
                OfflineArtworkManager.fileName("site", "vod"),
                OfflineArtworkManager.fileName("site", "vod"));
    }

    @Test
    public void separatesArtworkAcrossTitlesAndSources() {
        assertNotEquals(OfflineArtworkManager.fileName("site", "vod-1"), OfflineArtworkManager.fileName("site", "vod-2"));
        assertNotEquals(OfflineArtworkManager.fileName("site-1", "vod"), OfflineArtworkManager.fileName("site-2", "vod"));
    }
}
