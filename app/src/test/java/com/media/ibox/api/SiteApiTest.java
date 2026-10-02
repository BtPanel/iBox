package com.media.ibox.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SiteApiTest {

    @Test
    public void recognizesOfflinePushRequestsWithoutConsultingConfiguredSites() {
        assertTrue(SiteApi.isOfflineRequest(SiteApi.PUSH, "offline://download-id"));
    }

    @Test
    public void leavesRegularPushAndOtherSourcesUnchanged() {
        assertFalse(SiteApi.isOfflineRequest(SiteApi.PUSH, "https://example.com/video.mp4"));
        assertFalse(SiteApi.isOfflineRequest("source", "offline://download-id"));
    }
}
