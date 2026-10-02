package com.media.ibox.live;

import com.media.ibox.R;

public final class LiveEpgRefresh {

    private long requestedId = -1;

    public void request(long requestId) {
        requestedId = requestId;
    }

    public int onParsed(long requestId, boolean success) {
        if (requestedId != requestId) return 0;
        requestedId = -1;
        return success ? R.string.live_epg_refresh_success : R.string.live_epg_refresh_failed;
    }

    public void clear() {
        requestedId = -1;
    }
}
