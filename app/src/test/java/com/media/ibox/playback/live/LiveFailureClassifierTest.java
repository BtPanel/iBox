package com.media.ibox.playback.live;

import static org.junit.Assert.assertEquals;

import com.media.ibox.player.PlaybackFailure;

import org.junit.Test;

public class LiveFailureClassifierTest {

    @Test
    public void mapsEveryPlaybackFailureKindOneToOne() {
        for (PlaybackFailure.Kind kind : PlaybackFailure.Kind.values()) {
            assertEquals(LiveFailureKind.valueOf(kind.name()),
                    LiveFailureClassifier.from(new PlaybackFailure(kind, "failure", 0)));
        }
    }

    @Test
    public void sourceClassifiesResolverFailures() {
        assertEquals(LiveFailureKind.SOURCE, LiveFailureClassifier.source());
    }
}
