package com.media.ibox.playback.preference;

import com.media.ibox.player.engine.PlayerEngine;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.SpeedSetting;
import com.media.ibox.setting.SubtitleSetting;
import com.media.ibox.setting.SubtitleStyleOverride;

public record MoviePlaybackPreference(int engine, int decode, float speed, int subtitleSource,
                                      float subtitleScale, float subtitlePosition, long updatedAt) {

    public MoviePlaybackPreference {
        engine = Math.clamp(engine, PlayerSetting.ENGINE_EXO, PlayerSetting.ENGINE_MPV);
        decode = decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD;
        speed = SpeedSetting.clamp(speed);
        subtitleSource = Math.clamp(subtitleSource, SubtitleSetting.STYLE_SOURCE_ORIGINAL, SubtitleSetting.STYLE_SOURCE_CUSTOM);
        subtitleScale = Math.clamp(subtitleScale, SubtitleSetting.MIN_SCALE, SubtitleSetting.MAX_SCALE);
        subtitlePosition = Math.clamp(subtitlePosition, SubtitleSetting.MIN_POSITION, SubtitleSetting.MAX_POSITION);
        updatedAt = Math.max(0, updatedAt);
    }

    public static MoviePlaybackPreference global(int decode) {
        return new MoviePlaybackPreference(PlayerSetting.getEngine(), decode, SpeedSetting.getPlayback(),
                SubtitleSetting.getStyleSource(), SubtitleSetting.getScale(), SubtitleSetting.getPosition(),
                System.currentTimeMillis());
    }

    public SubtitleStyleOverride subtitleStyle() {
        return new SubtitleStyleOverride(subtitleSource, subtitleScale, subtitlePosition);
    }

    public MoviePlaybackPreference withEngine(int value) {
        return new MoviePlaybackPreference(value, decode, speed, subtitleSource, subtitleScale, subtitlePosition, System.currentTimeMillis());
    }

    public MoviePlaybackPreference withDecode(int value) {
        return new MoviePlaybackPreference(engine, value, speed, subtitleSource, subtitleScale, subtitlePosition, System.currentTimeMillis());
    }

    public MoviePlaybackPreference withSpeed(float value) {
        return new MoviePlaybackPreference(engine, decode, value, subtitleSource, subtitleScale, subtitlePosition, System.currentTimeMillis());
    }
}
