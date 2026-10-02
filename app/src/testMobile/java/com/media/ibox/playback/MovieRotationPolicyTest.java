package com.media.ibox.playback;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MovieRotationPolicyTest {

    @Test
    public void rotatingLandscapePhonePlaybackToPortraitRestoresDetail() {
        assertTrue(PlaybackOrientation.shouldRestoreMovieDetailOnRotate(true, true));
    }

    @Test
    public void portraitFullscreenAndLandscapeLayoutKeepTheirRotationBehavior() {
        assertFalse(PlaybackOrientation.shouldRestoreMovieDetailOnRotate(true, false));
        assertFalse(PlaybackOrientation.shouldRestoreMovieDetailOnRotate(false, true));
    }
}
