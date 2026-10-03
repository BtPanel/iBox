package com.media.ibox.bean;

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

    @Test
    public void formatsKu9MillisecondTokens() {
        // 酷9: ${(b)} 为 13 位毫秒, ${(b)13} 同义, ${(b)10} 为 10 位秒
        assertEquals(String.valueOf(TIME), Catchup.formatTime(TIME, "", SHANGHAI));
        assertEquals(String.valueOf(TIME), Catchup.formatTime(TIME, "13", SHANGHAI));
        assertEquals(String.valueOf(TIME / 1000), Catchup.formatTime(TIME, "10", SHANGHAI));
    }

    @Test
    public void supportsArbitraryTimezoneModifier() {
        // 13:00:20Z = 纽约(EDT, UTC-4) 09:00:20
        assertEquals("090020", Catchup.formatTime(TIME, "HHmmss|America/New_York", SHANGHAI));
    }

    @Test
    public void returnsEmptyInsteadOfThrowingOnInvalidPattern() {
        // 旧实现遇到非法时区/格式会抛异常导致整个回看地址生成失败
        assertEquals("", Catchup.formatTime(TIME, "HHmmss|Not/AZone", SHANGHAI));
    }

    @Test
    public void substitutesEidToken() {
        Catchup catchup = Catchup.create();
        catchup.setType("default");
        catchup.setSource("http://x/?c=play&eid={eid}");
        EpgData data = new EpgData();
        data.setEid("17736");
        assertEquals("http://x/?c=play&eid=17736", catchup.format("http://x/live", data));
    }

    @Test
    public void eidTokenStaysEmptyWhenEpgHasNoId() {
        Catchup catchup = Catchup.create();
        catchup.setType("append");
        catchup.setSource("&eid={eid}");
        assertEquals("http://x/live&eid=", catchup.format("http://x/live", new EpgData()));
    }

    @Test
    public void playbackFactoryChoosesModeBySourceShape() {
        // 酷9 txt PB=: 参数片段走 append, 完整地址走 default
        assertEquals("append", Catchup.playback("playseek=${(b)}-${(e)}").getType());
        assertEquals("playseek=${(b)}-${(e)}", Catchup.playback("playseek=${(b)}-${(e)}").getSource());
        assertEquals("default", Catchup.playback("http://x/play?ch=1&playseek=${(b)}-${(e)}").getType());
    }
}
