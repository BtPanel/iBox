package com.media.ibox.playback.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.media.ibox.player.engine.PlayerEngine;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.SpeedSetting;
import com.media.ibox.setting.SubtitleSetting;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MoviePlaybackPreferenceTest {

    @Test
    public void clampsPersistedValues() {
        MoviePlaybackPreference preference = new MoviePlaybackPreference(9, 9, 20,
                9, 9, 99, -1);

        assertEquals(PlayerSetting.ENGINE_MPV, preference.engine());
        assertEquals(PlayerEngine.HARD, preference.decode());
        assertEquals(SpeedSetting.MAX, preference.speed(), 0.001f);
        assertEquals(SubtitleSetting.STYLE_SOURCE_CUSTOM, preference.subtitleSource());
        assertEquals(SubtitleSetting.MAX_SCALE, preference.subtitleScale(), 0.001f);
        assertEquals(SubtitleSetting.MAX_POSITION, preference.subtitlePosition(), 0.001f);
        assertEquals(0, preference.updatedAt());
    }

    @Test
    public void keepsOnlyMostRecentHundredTitles() {
        Map<String, MoviePlaybackPreference> values = new LinkedHashMap<>();
        for (int i = 0; i < 105; i++) {
            values.put("movie-" + i, new MoviePlaybackPreference(PlayerSetting.ENGINE_EXO,
                    PlayerEngine.HARD, 1, SubtitleSetting.STYLE_SOURCE_ORIGINAL, 1, 0, i));
        }

        Map<String, MoviePlaybackPreference> trimmed = MoviePlaybackPreferenceStore.trim(values);

        assertEquals(MoviePlaybackPreferenceStore.MAX_ENTRIES, trimmed.size());
        assertTrue(trimmed.containsKey("movie-104"));
        assertFalse(trimmed.containsKey("movie-0"));
    }
}
