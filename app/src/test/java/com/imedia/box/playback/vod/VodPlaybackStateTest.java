package com.imedia.box.playback.vod;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VodPlaybackStateTest {

    @Test
    public void failedSourcesAreScopedBySiteAndId() {
        VodPlaybackState state = new VodPlaybackState();

        state.addFailedSource("site-a", "same-id");

        assertTrue(state.hasFailedSource("site-a", "same-id"));
        assertFalse(state.hasFailedSource("site-b", "same-id"));
        assertFalse(state.hasFailedSource("site-a", "other-id"));
    }
}
