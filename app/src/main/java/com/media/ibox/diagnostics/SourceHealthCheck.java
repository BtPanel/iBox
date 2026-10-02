package com.media.ibox.diagnostics;

import androidx.annotation.StringRes;

public record SourceHealthCheck(Kind kind, Status status, @StringRes int title, String detail,
                                String address, long elapsedMs) {

    public enum Kind {
        NETWORK,
        VOD_CONFIG,
        VOD_SOURCE,
        LIVE_SOURCE
    }

    public enum Status {
        OK,
        WARNING,
        FAILED,
        SKIPPED
    }
}
