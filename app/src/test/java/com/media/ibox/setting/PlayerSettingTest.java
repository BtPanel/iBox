package com.media.ibox.setting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerSettingTest {

    @Test
    public void autoNextStartsEnabledAndPersistsAnExplicitOffChoice() throws Exception {
        AtomicBoolean stored = new AtomicBoolean(true);
        try (MockedStatic<Prefers> prefs = mockStatic(Prefers.class)) {
            prefs.when(() -> Prefers.getBoolean("vod_auto_next", true)).thenAnswer(call -> stored.get());
            prefs.when(() -> Prefers.put("vod_auto_next", false)).thenAnswer(call -> {
                stored.set(false);
                return null;
            });
            Method read = PlayerSetting.class.getMethod("isAutoNextEnabled");
            Method write = PlayerSetting.class.getMethod("putAutoNextEnabled", boolean.class);

            assertTrue((boolean) read.invoke(null));
            write.invoke(null, false);
            assertFalse((boolean) read.invoke(null));
        }
    }

    @Test
    public void languagePreferencesAreIndependentAndRejectOutOfRangeChoices() {
        AtomicInteger audio = new AtomicInteger();
        AtomicInteger text = new AtomicInteger();
        try (MockedStatic<Prefers> prefs = mockStatic(Prefers.class)) {
            prefs.when(() -> Prefers.getInt("preferred_audio_language")).thenAnswer(call -> audio.get());
            prefs.when(() -> Prefers.getInt("preferred_text_language")).thenAnswer(call -> text.get());
            prefs.when(() -> Prefers.put("preferred_audio_language", 3)).thenAnswer(call -> {
                audio.set(3);
                return null;
            });
            prefs.when(() -> Prefers.put("preferred_text_language", 1)).thenAnswer(call -> {
                text.set(1);
                return null;
            });
            prefs.when(() -> Prefers.put("preferred_audio_language", 0)).thenAnswer(call -> {
                audio.set(0);
                return null;
            });

            assertEquals(0, PlayerSetting.getAudioLanguageIndex());
            assertEquals(0, PlayerSetting.getTextLanguageIndex());
            PlayerSetting.putAudioLanguageIndex(3);
            PlayerSetting.putTextLanguageIndex(1);
            assertEquals(3, PlayerSetting.getAudioLanguageIndex());
            assertEquals(1, PlayerSetting.getTextLanguageIndex());
            PlayerSetting.putAudioLanguageIndex(-10);
            assertEquals(0, PlayerSetting.getAudioLanguageIndex());
        }
    }
}
