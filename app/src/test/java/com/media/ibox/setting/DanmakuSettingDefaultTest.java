package com.media.ibox.setting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

public class DanmakuSettingDefaultTest {

    @Test
    public void displayDefaultsOffButKeepsSavedChoice() {
        AtomicReference<Boolean> stored = new AtomicReference<>();
        try (MockedStatic<Prefers> prefs = mockStatic(Prefers.class)) {
            prefs.when(() -> Prefers.getBoolean("danmaku_show", true))
                    .thenAnswer(call -> stored.get() == null || stored.get());
            prefs.when(() -> Prefers.getBoolean("danmaku_show", false))
                    .thenAnswer(call -> stored.get() != null && stored.get());
            prefs.when(() -> Prefers.put("danmaku_show", true)).thenAnswer(call -> {
                stored.set(true);
                return null;
            });
            prefs.when(() -> Prefers.put("danmaku_show", false)).thenAnswer(call -> {
                stored.set(false);
                return null;
            });

            assertFalse(DanmakuSetting.isShow());
            DanmakuSetting.putShow(true);
            assertTrue(DanmakuSetting.isShow());
            DanmakuSetting.putShow(false);
            assertFalse(DanmakuSetting.isShow());
        }
    }
}
