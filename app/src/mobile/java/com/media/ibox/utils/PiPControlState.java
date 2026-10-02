package com.media.ibox.utils;

import com.media.ibox.event.ActionEvent;

import java.util.Arrays;
import java.util.List;

public final class PiPControlState {

    private PiPControlState() {
    }

    public static List<String> actions(boolean playing) {
        return Arrays.asList(ActionEvent.PREV, playing ? ActionEvent.PAUSE : ActionEvent.PLAY, ActionEvent.NEXT);
    }

    public static int progress(long position, long duration, int max) {
        if (position < 0 || duration <= 0 || max <= 0) return 0;
        double fraction = Math.min(position, duration) / (double) duration;
        return (int) Math.round(fraction * max);
    }
}
