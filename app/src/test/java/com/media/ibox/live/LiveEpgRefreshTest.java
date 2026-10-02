package com.media.ibox.live;

import com.media.ibox.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveEpgRefreshTest {

    @Test
    public void reportsOnlyTheMatchingRequestOnce() {
        LiveEpgRefresh refresh = new LiveEpgRefresh();
        refresh.request(7);
        assertEquals(0, refresh.onParsed(6, false));
        assertEquals(R.string.live_epg_refresh_success, refresh.onParsed(7, true));
        assertEquals(0, refresh.onParsed(7, false));
    }

    @Test
    public void laterRequestCannotConsumeEarlierResult() {
        LiveEpgRefresh refresh = new LiveEpgRefresh();
        refresh.request(7);
        refresh.request(8);
        assertEquals(0, refresh.onParsed(7, true));
        assertEquals(R.string.live_epg_refresh_failed, refresh.onParsed(8, false));
    }

    @Test
    public void sourceChangeClearsPendingFeedback() {
        LiveEpgRefresh refresh = new LiveEpgRefresh();
        refresh.request(7);
        refresh.clear();
        assertEquals(0, refresh.onParsed(7, true));
        assertEquals(0, refresh.onParsed(8, false));
    }
}
