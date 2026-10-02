package com.media.ibox.utils;

import com.media.ibox.event.ActionEvent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PiPControlStateTest {

    @Test
    public void playingMovieShowsPreviousPauseAndNext() {
        assertEquals(
                List.of(ActionEvent.PREV, ActionEvent.PAUSE, ActionEvent.NEXT),
                PiPControlState.actions(true));
    }

    @Test
    public void pausedMovieShowsPreviousPlayAndNext() {
        assertEquals(
                List.of(ActionEvent.PREV, ActionEvent.PLAY, ActionEvent.NEXT),
                PiPControlState.actions(false));
    }

    @Test
    public void progressClampsToIndicatorRange() {
        assertEquals(2500, PiPControlState.progress(30_000, 120_000, 10_000));
        assertEquals(0, PiPControlState.progress(-1, 120_000, 10_000));
        assertEquals(0, PiPControlState.progress(30_000, 0, 10_000));
        assertEquals(10_000, PiPControlState.progress(150_000, 120_000, 10_000));
    }
}
