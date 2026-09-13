package com.imedia.box.player.track;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mockStatic;

import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Locale;

public class LangUtilPreferenceTest {

    @Test
    public void explicitAudioAndSubtitleLanguagesOverrideDeviceLocaleIndependently() {
        Locale original = Locale.getDefault();
        try (MockedStatic<Prefers> prefs = mockStatic(Prefers.class)) {
            Locale.setDefault(Locale.ENGLISH);
            prefs.when(() -> Prefers.getInt("preferred_audio_language")).thenReturn(3);
            prefs.when(() -> Prefers.getInt("preferred_text_language")).thenReturn(1);

            assertArrayEquals(new String[]{"en"}, LangUtil.getPreferredAudioLanguages());
            assertArrayEquals(new String[]{"zh-Hans", "zh"}, LangUtil.getPreferredTextLanguages());
        } finally {
            Locale.setDefault(original);
        }
    }
}
