package com.imedia.box.ui.activity;

import com.imedia.box.R;

final class LiveEpgRefresh {

    private boolean requested;

    boolean request(boolean diyp) {
        if (diyp) return false;
        requested = true;
        return true;
    }

    int onParsed(boolean success) {
        if (!requested) return 0;
        requested = false;
        return success ? R.string.live_epg_refresh_success : R.string.live_epg_refresh_failed;
    }
}
