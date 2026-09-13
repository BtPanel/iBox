package com.imedia.box.player.mpv;

import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.mpvplayer.MpvAndroidOptions;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.mpvplayer.MpvSubtitleOptions;

import com.imedia.box.App;
import com.imedia.box.player.track.LangUtil;
import com.imedia.box.setting.DecodeSetting;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.PreloadSetting;
import com.imedia.box.setting.SubtitleSetting;
import com.imedia.box.setting.SubtitleStyleOverride;
import com.github.catvod.utils.Path;

import java.io.File;

public final class MpvUtil {

    private static final String ASSET_CA_FILE = "cacert.pem";
    private static final double DEFAULT_SUB_POS = 100.0;
    private static final double DEFAULT_SUB_SCALE = 1.0;
    private static final double MIN_SUB_POS = 0.0;
    private static final double MAX_SUB_POS = 150.0;

    public static boolean isAvailable() {
        try {
            return MpvPlayer.isAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    public static MpvPlayer buildPlayer(int decode, Player.Listener listener) {
        MpvPlayer player = new MpvPlayer.Builder(App.get()).setDecode(decode).setConfig(buildConfig()).build();
        setPreferredLanguages(player);
        player.addListener(listener);
        return player;
    }

    public static void setSubtitleStyle(MpvPlayer player) {
        setSubtitleStyle(player, null);
    }

    public static void setSubtitleStyle(MpvPlayer player, SubtitleStyleOverride override) {
        player.setSubtitleOptions(buildSubtitleOptions(override));
    }

    private static MpvPlayerConfig buildConfig() {
        File configDir = Path.mpv();
        File cacheDir = Path.mpvCache();
        MpvPlayerConfig.Builder builder = new MpvPlayerConfig.Builder().addConfigDirectory(configDir).addAndroidFontConfig(configDir, cacheDir).addAndroidDefaults(buildAndroidOptions(cacheDir)).addTlsCaFileFromAsset(App.get(), ASSET_CA_FILE, Path.files(ASSET_CA_FILE)).addAndroidSubtitleOptions(App.get(), buildSubtitleOptions());
        addPreloadOptions(builder);
        return builder.build();
    }

    private static MpvAndroidOptions buildAndroidOptions(File shaderCacheDirectory) {
        MpvAndroidOptions.Builder builder = new MpvAndroidOptions.Builder().setShaderCacheDirectory(shaderCacheDirectory).setAudioPassthroughEnabled(DecodeSetting.isAudioPassThrough()).setDolbyVisionOutputPolicy(DecodeSetting.getDolbyVisionOutputPolicy());
        builder.setGpuNextEnabled(PlayerSetting.isMpvGpuNext());
        builder.setVulkanEnabled(PlayerSetting.isMpvVulkan());
        return builder.build();
    }

    private static void setPreferredLanguages(MpvPlayer player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguages(LangUtil.getPreferredAudioLanguages())
                .setPreferredTextLanguages(LangUtil.getPreferredTextLanguages()).build());
    }

    private static void addPreloadOptions(MpvPlayerConfig.Builder builder) {
        if (!PreloadSetting.isEnabled()) return;
        builder.addDiskCacheOptions(Path.mpvCache(), PreloadSetting.getTimeSeconds());
    }

    private static MpvSubtitleOptions buildSubtitleOptions() {
        return buildSubtitleOptions(null);
    }

    private static MpvSubtitleOptions buildSubtitleOptions(SubtitleStyleOverride override) {
        int source = override == null ? SubtitleSetting.getStyleSource() : override.source();
        float scale = override == null ? SubtitleSetting.getScale() : override.scale();
        float position = override == null ? SubtitleSetting.getPosition() : override.position();
        boolean styleForced = source != SubtitleSetting.STYLE_SOURCE_ORIGINAL;
        MpvSubtitleOptions.Builder builder = new MpvSubtitleOptions.Builder().setPosition(getSubtitlePosition(position)).setScale(getSubtitleScale(source, scale)).setSecondarySubtitle(SubtitleSetting.getSecondaryTrackId(), SubtitleSetting.getSecondaryPosition(), styleForced);
        if (source == SubtitleSetting.STYLE_SOURCE_CUSTOM) builder.setCustomStyle(SubtitleSetting.getTextColor(), SubtitleSetting.getBackgroundColor(), SubtitleSetting.getEdgeType(), SubtitleSetting.getEdgeColor(), SubtitleSetting.getEdgeWidth(), SubtitleSetting.getShadow());
        else if (source == SubtitleSetting.STYLE_SOURCE_SYSTEM) builder.setSystemCaptionStyle();
        return builder.build();
    }

    private static double getSubtitlePosition(float position) {
        if (position == 0) return DEFAULT_SUB_POS;
        return Util.constrainValue(DEFAULT_SUB_POS - position, MIN_SUB_POS, MAX_SUB_POS);
    }

    private static double getSubtitleScale(int source, float scale) {
        if (source == SubtitleSetting.STYLE_SOURCE_ORIGINAL && scale == 1.0f) return DEFAULT_SUB_SCALE;
        return SubtitleSetting.resolveScale(App.get(), source, scale);
    }
}
