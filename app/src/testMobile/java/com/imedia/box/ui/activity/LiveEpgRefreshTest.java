package com.imedia.box.ui.activity;

import com.imedia.box.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveEpgRefreshTest {

    @Test
    public void requestedXmlRefreshReportsItsResultOnce() {
        LiveEpgRefresh refresh = new LiveEpgRefresh();
        assertTrue(refresh.request(false));
        assertEquals(R.string.live_epg_refresh_success, refresh.onParsed(true));
        assertEquals(0, refresh.onParsed(false));

        assertTrue(refresh.request(false));
        assertEquals(R.string.live_epg_refresh_failed, refresh.onParsed(false));
    }

    @Test
    public void diypRefreshDoesNotStartXmlOrReportUnrelatedParse() {
        LiveEpgRefresh refresh = new LiveEpgRefresh();
        assertFalse(refresh.request(true));
        assertEquals(0, refresh.onParsed(false));
    }
}
