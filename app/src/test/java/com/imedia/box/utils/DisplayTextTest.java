package com.imedia.box.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DisplayTextTest {

    @Test
    public void removesEyeDecorationFromSourceTitles() {
        assertEquals("瓜子 | 不卡", DisplayText.withoutEyeDecoration("\uD83D\uDC40\uD83D\uDC40瓜子 | 不卡"));
        assertEquals("配置中心", DisplayText.withoutEyeDecoration(" \uD83D\uDC41\uFE0F配置中心 "));
    }

    @Test
    public void removesEmojiAndUpdateMetadataFromSourceTitles() {
        assertEquals("", DisplayText.withoutEyeDecoration("\uD83D\uDC2E【更新日期:20260829-1】\uD83D\uDC2E"));
        assertEquals("影视仓", DisplayText.withoutEyeDecoration("影视仓【更新时间：2026-08-29】"));
    }

    @Test
    public void handlesMissingTitles() {
        assertEquals("", DisplayText.withoutEyeDecoration(null));
    }
}
