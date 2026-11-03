package com.media.ibox.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingPlaybackPolicyTest {

    @Test
    public void backgroundOffPausesOnStopRegardlessOfFinishing() {
        assertTrue(FloatingPlaybackPolicy.shouldPauseOnStop(true, true, false));
    }

    @Test
    public void backgroundOnKeepsPlayingWhenLeavingPage() {
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, false, false));
    }

    @Test
    public void visibleFloatingWindowPreventsBackgroundPause() {
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, true, true));
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, false, true));
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(false, true, false));
    }

    @Test
    public void visibleFloatingWindowKeepsPlaybackServiceAlive() {
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, false, true, false));
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(true, false, false, false));
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, true, false, false));
        assertFalse(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, false, false, false));
    }

    @Test
    public void backgroundPlaybackKeepsServiceAliveForAudioMode() {
        assertTrue(FloatingPlaybackPolicy.shouldKeepServiceAlive(false, false, false, true));
    }

    @Test
    public void visibleFloatingWindowOwnsVideoSurfaceDuringEpisodeChange() {
        assertFalse(FloatingPlaybackPolicy.shouldAttachPlayerView(true));
        assertTrue(FloatingPlaybackPolicy.shouldAttachPlayerView(false));
    }
}
