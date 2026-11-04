package com.media.ibox.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingPlaybackPolicyTest {

    @Test
    public void leavingPageAlwaysPausesRegardlessOfBackgroundSetting() {
        assertTrue(FloatingPlaybackPolicy.shouldPauseOnStop(true, false));
    }

    @Test
    public void visibleFloatingWindowPreventsBackgroundPause() {
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(true, true));
        assertFalse(FloatingPlaybackPolicy.shouldPauseOnStop(false, false));
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
