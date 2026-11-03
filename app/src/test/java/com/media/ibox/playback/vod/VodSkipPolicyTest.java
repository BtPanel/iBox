package com.media.ibox.playback.vod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;

import com.media.ibox.bean.History;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.SkipSegment;

import org.junit.Test;

public class VodSkipPolicyTest {

    private SkipSegment segment(String type, Long start, Long end) {
        SkipSegment segment = new SkipSegment();
        try {
            java.lang.reflect.Field t = SkipSegment.class.getDeclaredField("type");
            t.setAccessible(true);
            t.set(segment, type);
            java.lang.reflect.Field s = SkipSegment.class.getDeclaredField("start");
            s.setAccessible(true);
            s.set(segment, start);
            java.lang.reflect.Field e = SkipSegment.class.getDeclaredField("end");
            e.setAccessible(true);
            e.set(segment, end);
        } catch (Exception ignored) {
        }
        return segment;
    }

    private Result result(SkipSegment... segments) {
        Result result = new Result();
        try {
            java.lang.reflect.Field f = Result.class.getDeclaredField("skipSegments");
            f.setAccessible(true);
            f.set(result, java.util.Arrays.asList(segments));
        } catch (Exception ignored) {
        }
        return result;
    }

    @Test
    public void startPositionSkipsOpeningWhenNotManuallyMarked() {
        History history = new History();
        Result result = result(segment("opening", 0L, 90000L));
        assertEquals(90000L, VodSkipPolicy.startPositionMs(history, result, C.TIME_UNSET));
    }

    @Test
    public void startPositionKeepsResumeBeyondOpeningEnd() {
        History history = new History();
        Result result = result(segment("opening", 0L, 90000L));
        assertEquals(120000L, VodSkipPolicy.startPositionMs(history, result, 120000L));
    }

    @Test
    public void startPositionPrefersManualOpeningMark() {
        History history = new History();
        history.setOpening(30000L);
        Result result = result(segment("opening", 0L, 90000L));
        assertEquals(30000L, VodSkipPolicy.startPositionMs(history, result, C.TIME_UNSET));
    }

    @Test
    public void startPositionHonoursResultPosition() {
        History history = new History();
        Result result = result(segment("opening", 0L, 90000L));
        result.setPosition(180000L);
        assertEquals(180000L, VodSkipPolicy.startPositionMs(history, result, C.TIME_UNSET));
    }

    @Test
    public void activeSegmentReturnsMiddleAlways() {
        History history = new History();
        history.setOpening(10000L);
        history.setEnding(60000L);
        SkipSegment middle = segment("middle", 300000L, 360000L);
        assertSame(middle, VodSkipPolicy.activeSegment(history, result(middle), 310000L));
    }

    @Test
    public void activeSegmentIgnoresOpeningWhenManuallyMarked() {
        History history = new History();
        history.setOpening(10000L);
        SkipSegment opening = segment("opening", 0L, 90000L);
        assertNull(VodSkipPolicy.activeSegment(history, result(opening), 5000L));
    }

    @Test
    public void activeSegmentIgnoresEndingWhenManuallyMarked() {
        History history = new History();
        history.setEnding(60000L);
        SkipSegment ending = segment("ending", 1800000L, null);
        assertNull(VodSkipPolicy.activeSegment(history, result(ending), 1900000L));
    }

    @Test
    public void activeSegmentMatchesEndingWhenAutoSkip() {
        History history = new History();
        SkipSegment ending = segment("ending", 1800000L, null);
        assertSame(ending, VodSkipPolicy.activeSegment(history, result(ending), 1900000L));
    }

    @Test
    public void activeSegmentIgnoresInvalidResultAndPositions() {
        History history = new History();
        assertNull(VodSkipPolicy.activeSegment(history, null, 1000L));
        assertNull(VodSkipPolicy.activeSegment(history, result(), 1000L));
        assertNull(VodSkipPolicy.activeSegment(history, result(segment("opening", 0L, 90000L)), -1L));
    }

    @Test
    public void historyAutoSkipSemantics() {
        assertTrue(History.isAutoSkip(C.TIME_UNSET));
        assertTrue(History.isAutoSkip(0));
        assertFalse(History.isAutoSkip(1000));
    }
}
