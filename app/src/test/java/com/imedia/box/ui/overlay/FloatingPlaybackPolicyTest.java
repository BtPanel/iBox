package com.imedia.box.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingPlaybackPolicyTest {

    @Test
    public void visibleFloatingWindowPreventsBackgroundPause() {
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, true, false, true));
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, false, true, true));
        assertTrue(FloatingPlaybackPolicy.shouldPauseOnStop(true, true, false, false));
        assertTrue(FloatingPlaybackPolicy.shouldPauseOnStop(true, false, true, false));
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(false, true, true, false));
    }

    @Test
    public void visibleFloatingWindowKeepsPlaybackServiceAlive() {
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, false, true));
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(true, false, false));
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, true, false));
        assertFalse(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, false, false));
    }

    @Test
    public void visibleFloatingWindowOwnsVideoSurfaceDuringEpisodeChange() {
        assertFalse(FloatingPlaybackPolicy.shouldAttachPlayerView(true));
        assertTrue(FloatingPlaybackPolicy.shouldAttachPlayerView(false));
    }
}
