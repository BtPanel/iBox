package com.imedia.box.bean;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

public class CatchupTest {

    private static final long TIME = Instant.parse("2026-08-27T13:00:20Z").toEpochMilli();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    public void formatsUtcModifierInCatchupTemplate() {
        assertEquals("20260827", Catchup.formatTime(TIME, "yyyyMMdd|UTC", SHANGHAI));
        assertEquals("130020", Catchup.formatTime(TIME, "HHmmss|utc", SHANGHAI));
    }

    @Test
    public void keepsLocalTimeWithoutUtcModifier() {
        assertEquals("210020", Catchup.formatTime(TIME, "HHmmss", SHANGHAI));
        assertEquals("210020", Catchup.formatTime(TIME, "HHmmss|LOCAL", SHANGHAI));
    }

    @Test
    public void formatsTimestampToken() {
        assertEquals(String.valueOf(TIME / 1000), Catchup.formatTime(TIME, "timestamp", SHANGHAI));
    }
}
