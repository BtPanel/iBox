package com.imedia.box.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FloatingWindowControlStateTest {

    @Test
    public void progressClampsPositionAndRejectsInvalidRanges() {
        assertEquals(2_500, FloatingWindowControlState.progress(30_000, 120_000, 10_000));
        assertEquals(0, FloatingWindowControlState.progress(-1, 120_000, 10_000));
        assertEquals(0, FloatingWindowControlState.progress(30_000, 0, 10_000));
        assertEquals(10_000, FloatingWindowControlState.progress(150_000, 120_000, 10_000));
    }

    @Test
    public void seekPositionClampsProgressAndRejectsInvalidRanges() {
        assertEquals(30_000, FloatingWindowControlState.seekPosition(2_500, 10_000, 120_000));
        assertEquals(0, FloatingWindowControlState.seekPosition(-1, 10_000, 120_000));
        assertEquals(120_000, FloatingWindowControlState.seekPosition(12_000, 10_000, 120_000));
        assertEquals(0, FloatingWindowControlState.seekPosition(2_500, 0, 120_000));
        assertEquals(0, FloatingWindowControlState.seekPosition(2_500, 10_000, 0));
    }

    @Test
    public void controlsAutoHideOnlyDuringUnscrubbedPlayback() {
        assertTrue(FloatingWindowControlState.shouldAutoHide(true, false));
        assertFalse(FloatingWindowControlState.shouldAutoHide(false, false));
        assertFalse(FloatingWindowControlState.shouldAutoHide(true, true));
    }
}
