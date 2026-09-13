package com.imedia.box.server.process;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.imedia.box.App;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.api.config.WallConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.server.Nano;
import com.imedia.box.server.ServerAccessPolicy;
import com.imedia.box.server.impl.Process;
import com.imedia.box.setting.DanmakuSetting;
import com.imedia.box.setting.DecodeSetting;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.PreloadSetting;
import com.imedia.box.setting.Setting;
import com.github.catvod.utils.Prefers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class Manage implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return ServerAccessPolicy.matchesEndpoint(url, "/manage");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            String action = value(session.getParms(), "do");
            switch (action) {
                case "setting" -> saveSetting(session.getParms());
                case "config-save" -> saveConfig(session.getParms());
                case "config-delete" -> deleteConfig(session.getParms());
                case "config-active" -> activateConfig(findConfig(session.getParms()));
                case "epg-save" -> saveEpg(session.getParms());
                case "epg-delete" -> deleteEpg(session.getParms());
                case "epg-active" -> activateEpg(session.getParms());
            }
            return json(state().toString());
        } catch (Exception e) {
            return Nano.error(Response.Status.BAD_REQUEST, e.getMessage() == null ? "Invalid request" : e.getMessage());
        }
    }

    private Response json(String body) {
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body);
    }

    private JsonObject state() {
        JsonObject result = new JsonObject();
        JsonObject subscriptions = new JsonObject();
        subscriptions.add("vod", App.gson().toJsonTree(Config.getAll(0)));
        subscriptions.add("live", App.gson().toJsonTree(Config.getAll(1)));
        subscriptions.add("wall", App.gson().toJsonTree(Config.getAll(2)));
        subscriptions.add("epg", App.gson().toJsonTree(LiveSetting.getEpgSubscriptions()));
        result.add("subscriptions", subscriptions);

        JsonObject active = new JsonObject();
        active.addProperty("vod", VodConfig.get().getConfig().getId());
        active.addProperty("live", LiveConfig.get().getConfig().getId());
        active.addProperty("wall", WallConfig.get().getConfig().getId());
        LiveSetting.EpgSubscription epg = LiveSetting.getActiveEpgSubscription();
        active.addProperty("epg", epg == null ? "" : epg.getId());
        result.add("active", active);
        result.add("settings", settings());
        return result;
    }

    private JsonObject settings() {
        JsonObject values = new JsonObject();
        values.addProperty("wallBlur", Setting.isWallBlur());
        values.addProperty("incognito", Setting.isIncognito());
        values.addProperty("bootAutoStart", Setting.isBootAutoStart());
        values.addProperty("bootStartMode", Setting.getBootStartMode());
        values.addProperty("autoFallbackLine", Setting.isAutoFallbackLine());
        values.addProperty("autoFallbackSource", Setting.isAutoFallbackSource());
        values.addProperty("fontScale", Setting.getFontScaleMode());
        values.addProperty("highContrast", Setting.isHighContrast());
        values.addProperty("reduceMotion", Setting.isReduceMotion());
        values.addProperty("themeColor", Setting.getThemeColor());
        values.addProperty("focusColor", Setting.getFocusColor());
        values.addProperty("searchThreads", Setting.getSearchThreads());
        values.addProperty("adblock", Setting.isAdblock());
        values.addProperty("configNotice", Setting.isConfigNoticeEnabled());

        values.addProperty("playerEngine", PlayerSetting.getEngine());
        values.addProperty("playerMpvGpuNext", PlayerSetting.isMpvGpuNext());
        values.addProperty("playerMpvVulkan", PlayerSetting.isMpvVulkan());
        values.addProperty("playerRender", PlayerSetting.getRender());
        values.addProperty("playerSize", PlayerSetting.getSize());
        values.addProperty("playerScale", PlayerSetting.getScale());
        values.addProperty("playerBackground", PlayerSetting.getBackground());
        values.addProperty("playerBuffer", PlayerSetting.getBuffer());
        values.addProperty("userAgent", Setting.getUa());

        values.addProperty("decodeTunnel", DecodeSetting.isTunnel());
        values.addProperty("decodeAudioPassThrough", DecodeSetting.isAudioPassThrough());
        values.addProperty("decodeAudioPrefer", DecodeSetting.isAudioPrefer());
        values.addProperty("decodeVideoPrefer", DecodeSetting.isVideoPrefer());
        values.addProperty("decodeDolbyVision", DecodeSetting.getDolbyVisionOutputPolicy());
        values.addProperty("decodePreferAac", DecodeSetting.isPreferAAC());

        values.addProperty("preloadEnabled", PreloadSetting.isEnabled());
        values.addProperty("preloadNextEpisode", PreloadSetting.isNextEpisodeEnabled());
        values.addProperty("preloadThreads", PreloadSetting.getThreads());
        values.addProperty("preloadSizeMb", PreloadSetting.getSizeMb());
        values.addProperty("preloadTimeSeconds", PreloadSetting.getTimeSeconds());

        values.addProperty("liveChange", LiveSetting.isChange());
        values.addProperty("liveInvert", LiveSetting.isInvert());
        values.addProperty("liveReconnectDelay", LiveSetting.getReconnectDelaySeconds());
        values.addProperty("liveTimeout", LiveSetting.getPlayTimeoutSeconds());
        values.addProperty("liveRtspTcp", LiveSetting.isRtspTcp());
        values.addProperty("liveTimeshiftEnd", LiveSetting.getTimeshiftEndMode());
        values.addProperty("liveRememberDecode", LiveSetting.isRememberDecode());
        values.addProperty("liveShowClock", LiveSetting.isShowClock());
        values.addProperty("liveShowCatchupIndicator", LiveSetting.isShowCatchupIndicator());
        values.addProperty("liveShowNetwork", LiveSetting.isShowNetworkSpeed());
        values.addProperty("liveShowBottomInfo", LiveSetting.isShowBottomInfo());
        values.addProperty("liveShowBottomLogo", LiveSetting.isShowBottomLogo());
        values.addProperty("liveShowChannelLogo", LiveSetting.isShowChannelLogo());
        values.addProperty("liveShowChannelNumber", LiveSetting.isShowChannelNumber());
        values.addProperty("liveChannelEpg", LiveSetting.getChannelEpgMode());
        values.addProperty("liveCompactList", LiveSetting.isCompactList());
        values.addProperty("livePanelOpacity", LiveSetting.getPanelOpacity());
        values.addProperty("liveEpgCacheHour", LiveSetting.getEpgCacheHour());
        values.addProperty("liveEpgOffset", LiveSetting.getEpgOffsetHours());
        values.addProperty("liveEpgExternalCache", LiveSetting.isEpgExternalCache());
        values.addProperty("liveScale", LiveSetting.getScale());

        values.addProperty("danmakuLoad", DanmakuSetting.isLoad());
        values.addProperty("danmakuAuto", DanmakuSetting.isAuto());
        values.addProperty("danmakuSpiderFirst", DanmakuSetting.isSpiderFirst());
        values.addProperty("danmakuShow", DanmakuSetting.isShow());
        values.addProperty("danmakuTextScale", DanmakuSetting.getTextScale());
        values.addProperty("danmakuTransparency", DanmakuSetting.getTransparency());
        values.addProperty("danmakuBold", DanmakuSetting.isTextBold());
        values.addProperty("danmakuMaxOnScreen", DanmakuSetting.getMaxOnScreen());
        values.addProperty("danmakuScrollArea", DanmakuSetting.getScrollAreaRatio());
        values.addProperty("danmakuShowScroll", DanmakuSetting.isShowScroll());
        values.addProperty("danmakuShowTop", DanmakuSetting.isShowTop());
        values.addProperty("danmakuShowBottom", DanmakuSetting.isShowBottom());
        values.addProperty("danmakuShowReverse", DanmakuSetting.isShowReverse());
        values.addProperty("danmakuMerge", DanmakuSetting.isMergeDuplicates());
        values.addProperty("danmakuMaxLength", DanmakuSetting.getMaxTextLength());
        values.addProperty("danmakuBlocked", DanmakuSetting.getBlockedKeywords());
        return values;
    }

    private void saveSetting(Map<String, String> params) {
        String key = value(params, "key");
        String value = value(params, "value");
        switch (key) {
            case "wallBlur" -> Setting.putWallBlur(bool(value));
            case "incognito" -> Setting.putIncognito(bool(value));
            case "bootAutoStart" -> Setting.putBootAutoStart(bool(value));
            case "bootStartMode" -> Setting.putBootStartMode(integer(value));
            case "autoFallbackLine" -> Setting.putAutoFallbackLine(bool(value));
            case "autoFallbackSource" -> Setting.putAutoFallbackSource(bool(value));
            case "fontScale" -> Setting.putFontScaleMode(integer(value));
            case "highContrast" -> Setting.putHighContrast(bool(value));
            case "reduceMotion" -> Setting.putReduceMotion(bool(value));
            case "themeColor" -> Setting.putThemeColor(integer(value));
            case "focusColor" -> Setting.putFocusColor(integer(value));
            case "searchThreads" -> Setting.putSearchThreads(integer(value));
            case "adblock" -> Setting.putAdblock(bool(value));
            case "configNotice" -> Setting.putConfigNoticeEnabled(bool(value));

            case "playerEngine" -> PlayerSetting.putEngine(integer(value));
            case "playerMpvGpuNext" -> PlayerSetting.putMpvGpuNext(bool(value));
            case "playerMpvVulkan" -> PlayerSetting.putMpvVulkan(bool(value));
            case "playerRender" -> PlayerSetting.putRender(integer(value));
            case "playerSize" -> PlayerSetting.putSize(integer(value));
            case "playerScale" -> PlayerSetting.putScale(integer(value));
            case "playerBackground" -> PlayerSetting.putBackground(integer(value));
            case "playerBuffer" -> PlayerSetting.putBuffer(integer(value));
            case "userAgent" -> Setting.putUa(value.trim());

            case "decodeTunnel" -> DecodeSetting.putTunnel(bool(value));
            case "decodeAudioPassThrough" -> DecodeSetting.putAudioPassThrough(bool(value));
            case "decodeAudioPrefer" -> DecodeSetting.putAudioPrefer(bool(value));
            case "decodeVideoPrefer" -> DecodeSetting.putVideoPrefer(bool(value));
            case "decodeDolbyVision" -> DecodeSetting.putDolbyVisionOutputPolicy(Math.clamp(integer(value), 0, 2));
            case "decodePreferAac" -> DecodeSetting.putPreferAAC(bool(value));

            case "preloadEnabled" -> PreloadSetting.putEnabled(bool(value));
            case "preloadNextEpisode" -> PreloadSetting.putNextEpisodeEnabled(bool(value));
            case "preloadThreads" -> PreloadSetting.putThreads(integer(value));
            case "preloadSizeMb" -> PreloadSetting.putSizeMb(integer(value));
            case "preloadTimeSeconds" -> PreloadSetting.putTimeSeconds(integer(value));

            case "liveChange" -> LiveSetting.putChange(bool(value));
            case "liveInvert" -> LiveSetting.putInvert(bool(value));
            case "liveReconnectDelay" -> LiveSetting.putReconnectDelaySeconds(integer(value));
            case "liveTimeout" -> LiveSetting.putPlayTimeoutSeconds(integer(value));
            case "liveRtspTcp" -> LiveSetting.putRtspTransport(bool(value) ? 0 : 1);
            case "liveTimeshiftEnd" -> LiveSetting.putTimeshiftEndMode(integer(value));
            case "liveRememberDecode" -> LiveSetting.putRememberDecode(bool(value));
            case "liveShowClock" -> LiveSetting.putShowClock(bool(value));
            case "liveShowCatchupIndicator" -> LiveSetting.putShowCatchupIndicator(bool(value));
            case "liveShowNetwork" -> LiveSetting.putShowNetworkSpeed(bool(value));
            case "liveShowBottomInfo" -> LiveSetting.putShowBottomInfo(bool(value));
            case "liveShowBottomLogo" -> LiveSetting.putShowBottomLogo(bool(value));
            case "liveShowChannelLogo" -> LiveSetting.putShowChannelLogo(bool(value));
            case "liveShowChannelNumber" -> LiveSetting.putShowChannelNumber(bool(value));
            case "liveChannelEpg" -> LiveSetting.putChannelEpgMode(integer(value));
            case "liveCompactList" -> LiveSetting.putCompactList(bool(value));
            case "livePanelOpacity" -> LiveSetting.putPanelOpacity(integer(value));
            case "liveEpgCacheHour" -> LiveSetting.putEpgCacheHour(integer(value));
            case "liveEpgOffset" -> LiveSetting.putEpgOffsetHours(integer(value));
            case "liveEpgExternalCache" -> LiveSetting.putEpgExternalCache(bool(value));
            case "liveScale" -> LiveSetting.putScale(integer(value));

            case "danmakuLoad" -> DanmakuSetting.putLoad(bool(value));
            case "danmakuAuto" -> DanmakuSetting.putAuto(bool(value));
            case "danmakuSpiderFirst" -> DanmakuSetting.putSpiderFirst(bool(value));
            case "danmakuShow" -> DanmakuSetting.putShow(bool(value));
            case "danmakuTextScale" -> DanmakuSetting.putTextScale(decimal(value));
            case "danmakuTransparency" -> DanmakuSetting.putTransparency(decimal(value));
            case "danmakuBold" -> DanmakuSetting.putTextBold(bool(value));
            case "danmakuMaxOnScreen" -> DanmakuSetting.putMaxOnScreen(integer(value));
            case "danmakuScrollArea" -> DanmakuSetting.putScrollAreaRatio(decimal(value));
            case "danmakuShowScroll" -> DanmakuSetting.putShowScroll(bool(value));
            case "danmakuShowTop" -> DanmakuSetting.putShowTop(bool(value));
            case "danmakuShowBottom" -> DanmakuSetting.putShowBottom(bool(value));
            case "danmakuShowReverse" -> DanmakuSetting.putShowReverse(bool(value));
            case "danmakuMerge" -> DanmakuSetting.putMergeDuplicates(bool(value));
            case "danmakuMaxLength" -> DanmakuSetting.putMaxTextLength(integer(value));
            case "danmakuBlocked" -> DanmakuSetting.putBlockedKeywords(value);
            default -> throw new IllegalArgumentException("Unsupported setting");
        }
        postSettingEvent(key);
    }

    private void postSettingEvent(String key) {
        App.post(() -> {
            if (key.startsWith("live")) RefreshEvent.live();
            else if (key.equals("playerSize")) RefreshEvent.size();
            else if (key.equals("highContrast")) {
                if (!Setting.applyThemeMode()) RefreshEvent.theme();
            } else if (key.equals("themeColor") || key.equals("focusColor") || key.equals("fontScale")) RefreshEvent.theme();
            else if (key.equals("wallBlur")) ConfigEvent.wall();
            else if (key.startsWith("danmaku")) RefreshEvent.danmaku("");
            ConfigEvent.common();
        });
    }

    private void saveConfig(Map<String, String> params) {
        int type = type(params);
        int id = optionalInt(params.get("id"), 0);
        String name = value(params, "name").trim();
        String url = value(params, "url").trim();
        if (url.isEmpty()) throw new IllegalArgumentException("Address is required");
        Config item = id > 0 ? Config.find(id) : Config.find(url, type);
        if (item != null && id > 0 && item.getType() != type) throw new IllegalArgumentException("Subscription type does not match");
        boolean active = item != null && item.getId() == activeConfig(type).getId();
        if (item == null) item = Config.create(type);
        item.type(type).url(url).name(name);
        item.setTime(System.currentTimeMillis());
        item.save();
        if (active) activateConfig(item);
    }

    private void deleteConfig(Map<String, String> params) {
        Config item = findConfig(params);
        int type = item.getType();
        boolean active = item.getId() == activeConfig(type).getId();
        item.delete();
        if (!active) return;
        List<Config> remaining = Config.getAll(type);
        if (remaining.isEmpty()) resetConfig(type);
        else activateConfig(remaining.get(0));
    }

    private Config findConfig(Map<String, String> params) {
        int id = integer(value(params, "id"));
        Config item = Config.find(id);
        if (item == null) throw new IllegalArgumentException("Subscription not found");
        return item;
    }

    private Config activeConfig(int type) {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> throw new IllegalArgumentException("Invalid subscription type");
        };
    }

    private void activateConfig(Config item) {
        item.update();
        Callback callback = new Callback();
        switch (item.getType()) {
            case 0 -> VodConfig.load(item, callback);
            case 1 -> LiveConfig.load(item, callback);
            case 2 -> {
                Setting.putWall(0);
                WallConfig.load(item, callback);
            }
            default -> throw new IllegalArgumentException("Invalid subscription type");
        }
    }

    private void resetConfig(int type) {
        Config empty = Config.create(type);
        Prefers.remove("config_" + type);
        switch (type) {
            case 0 -> {
                VodConfig.get().clear().config(empty);
                App.post(ConfigEvent::vod);
            }
            case 1 -> {
                LiveConfig.get().clear().config(empty);
                App.post(ConfigEvent::live);
            }
            case 2 -> {
                Setting.putWall(0);
                WallConfig.get().config(empty);
                App.post(ConfigEvent::wall);
            }
            default -> throw new IllegalArgumentException("Invalid subscription type");
        }
    }

    private void saveEpg(Map<String, String> params) {
        String id = value(params, "id");
        String name = value(params, "name").trim();
        String url = value(params, "url").trim();
        if (url.isEmpty()) throw new IllegalArgumentException("Address is required");
        Map<String, String> headers = LiveSetting.getEpgSubscriptions().stream()
                .filter(item -> item != null && id.equals(item.getId()))
                .findFirst()
                .map(LiveSetting.EpgSubscription::getHeaders)
                .orElseGet(Collections::emptyMap);
        LiveSetting.saveEpgSubscription(id, name, url, optionalInt(params.get("type"), 0), headers);
        App.post(RefreshEvent::live);
    }

    private void deleteEpg(Map<String, String> params) {
        String id = value(params, "id");
        if (id.isEmpty()) throw new IllegalArgumentException("Subscription not found");
        LiveSetting.deleteEpgSubscription(id);
        App.post(RefreshEvent::live);
    }

    private void activateEpg(Map<String, String> params) {
        String id = value(params, "id");
        boolean found = LiveSetting.getEpgSubscriptions().stream().anyMatch(item -> item != null && id.equals(item.getId()));
        if (!found) throw new IllegalArgumentException("Subscription not found");
        LiveSetting.setActiveEpgSubscription(id);
        App.post(RefreshEvent::live);
    }

    private int type(Map<String, String> params) {
        int type = integer(value(params, "type"));
        if (type < 0 || type > 2) throw new IllegalArgumentException("Invalid subscription type");
        return type;
    }

    private String value(Map<String, String> params, String key) {
        return params.getOrDefault(key, "");
    }

    private boolean bool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private int integer(String value) {
        return Integer.parseInt(value);
    }

    private int optionalInt(String value, int fallback) {
        return TextUtils.isEmpty(value) ? fallback : integer(value);
    }

    private float decimal(String value) {
        return Float.parseFloat(value);
    }
}
