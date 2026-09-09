package com.imedia.box.player.engine;

import static com.imedia.box.player.engine.PlayerEngine.Type.EXO;
import static com.imedia.box.player.engine.PlayerEngine.Type.MPV;

import androidx.media3.common.Player;

import com.imedia.box.player.exo.ExoPlayerEngine;
import com.imedia.box.player.media.PlaySpec;
import com.imedia.box.player.mpv.MpvPlayerEngine;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.utils.UrlUtil;

public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return create(decode, PlayerSetting.getEngine(), listener);
    }

    public static PlayerEngine create(int decode, int preferredEngine, Player.Listener listener) {
        return create(decode, resolve(preferredEngine), listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, spec, PlayerSetting.getEngine(), listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, int preferredEngine, Player.Listener listener) {
        return create(decode, resolve(spec, preferredEngine), listener);
    }

    public static PlayerEngine create(int decode, PlayerEngine.Type type, Player.Listener listener) {
        return switch (type) {
            case EXO -> new ExoPlayerEngine(decode, listener);
            case MPV -> new MpvPlayerEngine(decode, listener);
        };
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return matches(engine, spec, PlayerSetting.getEngine());
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec, int preferredEngine) {
        return engine != null && engine.getType() == resolve(spec, preferredEngine);
    }

    private static PlayerEngine.Type resolve(PlaySpec spec, int preferredEngine) {
        if (requiresExo(spec)) return EXO;
        if (!isMpvReady(preferredEngine)) return EXO;
        return MPV;
    }

    private static PlayerEngine.Type resolve(int preferredEngine) {
        return isMpvReady(preferredEngine) ? MPV : EXO;
    }

    private static boolean requiresExo(PlaySpec spec) {
        return spec.getDrm() != null || "smb".equals(UrlUtil.scheme(spec.getUrl())) || spec.getKey().contains("offline://");
    }

    private static boolean isMpvReady(int preferredEngine) {
        return preferredEngine == PlayerSetting.ENGINE_MPV && MpvPlayerEngine.isAvailable();
    }
}
