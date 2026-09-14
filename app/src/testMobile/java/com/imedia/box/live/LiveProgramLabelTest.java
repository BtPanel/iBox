package com.imedia.box.live;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveProgramLabelTest {

    @Test
    public void compactPreviewShowsOnlyTheProgramStartTime() {
        assertEquals("02:45 非遗里的中国", LiveProgramLabel.format("02:45", "非遗里的中国", true));
        assertEquals("非遗里的中国", LiveProgramLabel.format("02:45", "非遗里的中国", false));
    }

    @Test
    public void missingTimeDoesNotConsumeTitleSpace() {
        assertEquals("天气预报", LiveProgramLabel.format("", "天气预报", true));
    }

    @Test
    public void oneLinePreviewKeepsUpcomingProgramAfterCurrent() {
        assertEquals("正在 09:00-10:00 新闻  ›  稍后 10:00-10:30 天气",
                LiveProgramLabel.join("正在 09:00-10:00 新闻", "稍后 10:00-10:30 天气"));
        assertEquals("正在 新闻", LiveProgramLabel.join("正在 新闻", ""));
    }
}
