package com.media.ibox.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.media.ibox.bean.Sub;
import com.media.ibox.player.effect.PlayerEffect;
import com.media.ibox.player.media.PlaySpec;
import com.media.ibox.setting.SubtitleStyleOverride;

public interface PlayerEngine {

    int SOFT = C.DECODE_SOFTWARE;
    int HARD = C.DECODE_HARDWARE;

    Type getType();

    Player getPlayer();

    int getAudioChannelCount();

    void release();

    void setDecode(int decode);

    default void setDecode(int decode, boolean forceSoftwareDecode) {
        setDecode(decode);
    }

    default PlayerEffect getEffect() {
        return PlayerEffect.NONE;
    }

    void start(PlaySpec spec, long startPositionMs);

    default void preload(PlaySpec spec, long startPositionMs) {
    }

    default void clearPreload() {
    }

    void stop();

    default void setSubtitleStyle() {
    }

    default void setSubtitleStyle(SubtitleStyleOverride override) {
        setSubtitleStyle();
    }

    default boolean addSubtitle(Sub sub) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        DECODE,
        FATAL
    }

    enum Type {
        EXO,
        MPV
    }
}
