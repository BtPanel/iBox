package com.media.ibox.playback.live;

import com.media.ibox.player.PlaybackFailure;

public final class LiveFailureClassifier {

    private LiveFailureClassifier() {
    }

    public static LiveFailureKind from(PlaybackFailure failure) {
        return failure == null ? LiveFailureKind.PLAYBACK : LiveFailureKind.valueOf(failure.kind().name());
    }

    public static LiveFailureKind source() {
        return LiveFailureKind.SOURCE;
    }
}
