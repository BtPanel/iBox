package com.imedia.box.setting;

public record SubtitleStyleOverride(int source, float scale, float position) {

    public SubtitleStyleOverride {
        source = Math.clamp(source, SubtitleSetting.STYLE_SOURCE_ORIGINAL, SubtitleSetting.STYLE_SOURCE_CUSTOM);
        scale = Math.clamp(scale, SubtitleSetting.MIN_SCALE, SubtitleSetting.MAX_SCALE);
        position = Math.clamp(position, SubtitleSetting.MIN_POSITION, SubtitleSetting.MAX_POSITION);
    }

    public static SubtitleStyleOverride global() {
        return new SubtitleStyleOverride(SubtitleSetting.getStyleSource(), SubtitleSetting.getScale(), SubtitleSetting.getPosition());
    }
}
