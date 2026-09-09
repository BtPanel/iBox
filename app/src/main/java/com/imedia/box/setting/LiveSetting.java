package com.imedia.box.setting;

import com.imedia.box.App;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Live;
import com.imedia.box.player.engine.PlayerEngine;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LiveSetting {

    private static final int[] PANEL_OPACITY = {70, 80, 90, 100};
    public static final int[] PLAY_TIMEOUT_SECONDS = {5, 10, 15, 20, 25, 30};
    public static final int[] RECONNECT_DELAY_SECONDS = {0, 1, 3, 5, 10, 20};
    public static final int[] EPG_CACHE_HOURS = {8, 10, 12, 14, 16, 18, 20, 22};
    private static final String SOURCE_OVERRIDES = "live_source_overrides";
    private static final String EPG_SUBSCRIPTIONS = "live_epg_subscriptions";
    private static final String EPG_ACTIVE = "live_epg_active";
    private static final Type SOURCE_OVERRIDE_TYPE = new TypeToken<Map<String, SourceOverride>>() {}.getType();
    private static final Type EPG_SUBSCRIPTION_TYPE = new TypeToken<List<EpgSubscription>>() {}.getType();

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static boolean isReconnect() {
        return getReconnectDelaySeconds() > 0;
    }

    public static void putReconnect(boolean reconnect) {
        putReconnectDelaySeconds(reconnect ? 3 : 0);
    }

    public static int getReconnectDelaySeconds() {
        int fallback = Prefers.getBoolean("live_reconnect", true) ? 3 : 0;
        int delay = Prefers.getPrefers().contains("live_reconnect_delay")
                ? Prefers.getInt("live_reconnect_delay", fallback)
                : fallback;
        for (int value : RECONNECT_DELAY_SECONDS) if (value == delay) return value;
        return fallback;
    }

    public static void putReconnectDelaySeconds(int seconds) {
        for (int value : RECONNECT_DELAY_SECONDS) {
            if (value != seconds) continue;
            Prefers.put("live_reconnect_delay", value);
            Prefers.put("live_reconnect", value > 0);
            return;
        }
    }

    public static int getPlayTimeoutSeconds() {
        int timeout = Prefers.getInt("live_play_timeout", 10);
        for (int value : PLAY_TIMEOUT_SECONDS) if (value == timeout) return value;
        return 10;
    }

    public static long getPlayTimeoutMillis() {
        return getPlayTimeoutSeconds() * 1000L;
    }

    public static void putPlayTimeoutSeconds(int seconds) {
        for (int value : PLAY_TIMEOUT_SECONDS) {
            if (value == seconds) {
                Prefers.put("live_play_timeout", value);
                return;
            }
        }
    }

    public static boolean isRtspTcp() {
        return Prefers.getInt("live_rtsp_transport", 0) == 0;
    }

    public static void putRtspTransport(int transport) {
        Prefers.put("live_rtsp_transport", transport == 1 ? 1 : 0);
    }

    public static int getTimeshiftEndMode() {
        return Math.clamp(Prefers.getInt("live_timeshift_end", 0), 0, 1);
    }

    public static void putTimeshiftEndMode(int mode) {
        Prefers.put("live_timeshift_end", Math.clamp(mode, 0, 1));
    }

    public static boolean isRememberDecode() {
        return Prefers.getBoolean("live_remember_decode", true);
    }

    public static void putRememberDecode(boolean remember) {
        Prefers.put("live_remember_decode", remember);
    }

    public static int getDecode() {
        return Prefers.getInt("live_decode", PlayerEngine.HARD) == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD;
    }

    public static void putDecode(int decode) {
        Prefers.put("live_decode", decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD);
    }

    public static boolean isShowClock() {
        return Prefers.getBoolean("live_show_clock", true);
    }

    public static void putShowClock(boolean show) {
        Prefers.put("live_show_clock", show);
    }

    public static boolean isShowCatchupIndicator() {
        return Prefers.getBoolean("live_show_catchup_indicator", true);
    }

    public static void putShowCatchupIndicator(boolean show) {
        Prefers.put("live_show_catchup_indicator", show);
    }

    public static boolean isShowNetworkSpeed() {
        return Prefers.getBoolean("live_show_network_speed");
    }

    public static void putShowNetworkSpeed(boolean show) {
        Prefers.put("live_show_network_speed", show);
    }

    public static boolean isShowBottomInfo() {
        return Prefers.getBoolean("live_show_bottom_info", true);
    }

    public static void putShowBottomInfo(boolean show) {
        Prefers.put("live_show_bottom_info", show);
    }

    public static boolean isShowBottomLogo() {
        return Prefers.getBoolean("live_show_bottom_logo", true);
    }

    public static void putShowBottomLogo(boolean show) {
        Prefers.put("live_show_bottom_logo", show);
    }

    public static boolean isShowChannelLogo() {
        return Prefers.getBoolean("live_show_channel_logo", true);
    }

    public static void putShowChannelLogo(boolean show) {
        Prefers.put("live_show_channel_logo", show);
    }

    public static boolean isShowChannelNumber() {
        return Prefers.getBoolean("live_show_channel_number", true);
    }

    public static void putShowChannelNumber(boolean show) {
        Prefers.put("live_show_channel_number", show);
    }

    public static boolean isShowChannelEpg() {
        return getChannelEpgMode() > 0;
    }

    public static void putShowChannelEpg(boolean show) {
        putChannelEpgMode(show ? 2 : 0);
    }

    public static int getChannelEpgMode() {
        if (!Prefers.getPrefers().contains("live_channel_epg_mode")) {
            return Prefers.getBoolean("live_show_channel_epg", true) ? 2 : 0;
        }
        return Math.clamp(Prefers.getInt("live_channel_epg_mode", 2), 0, 2);
    }

    public static void putChannelEpgMode(int mode) {
        int value = Math.clamp(mode, 0, 2);
        Prefers.put("live_channel_epg_mode", value);
        Prefers.put("live_show_channel_epg", value > 0);
    }

    public static boolean isCompactList() {
        return Prefers.getBoolean("live_compact_list");
    }

    public static void putCompactList(boolean compact) {
        Prefers.put("live_compact_list", compact);
    }

    public static int getPanelOpacity() {
        int opacity = Prefers.getInt("live_panel_opacity", 90);
        for (int value : PANEL_OPACITY) if (value == opacity) return value;
        return 90;
    }

    public static void nextPanelOpacity() {
        int current = getPanelOpacity();
        for (int i = 0; i < PANEL_OPACITY.length; i++) {
            if (PANEL_OPACITY[i] != current) continue;
            Prefers.put("live_panel_opacity", PANEL_OPACITY[(i + 1) % PANEL_OPACITY.length]);
            return;
        }
    }

    public static void putPanelOpacity(int opacity) {
        for (int value : PANEL_OPACITY) {
            if (value == opacity) {
                Prefers.put("live_panel_opacity", value);
                return;
            }
        }
    }

    public static int getEpgCacheHour() {
        int hour = Prefers.getInt("live_epg_cache_hour", EPG_CACHE_HOURS[0]);
        for (int value : EPG_CACHE_HOURS) if (value == hour) return value;
        return EPG_CACHE_HOURS[0];
    }

    public static void putEpgCacheHour(int hour) {
        for (int value : EPG_CACHE_HOURS) {
            if (value == hour) {
                Prefers.put("live_epg_cache_hour", value);
                return;
            }
        }
    }

    public static boolean hasCustomEpgOffset() {
        return Prefers.getBoolean("live_epg_offset_custom");
    }

    public static int getEpgOffsetHours() {
        return Math.clamp(Prefers.getInt("live_epg_offset_hours", 0), -12, 12);
    }

    public static void putEpgOffsetHours(Integer hours) {
        Prefers.put("live_epg_offset_custom", hours != null);
        Prefers.put("live_epg_offset_hours", hours == null ? 0 : Math.clamp(hours, -12, 12));
    }

    public static boolean isEpgExternalCache() {
        return Prefers.getBoolean("live_epg_external_cache");
    }

    public static void putEpgExternalCache(boolean external) {
        Prefers.put("live_epg_external_cache", external);
    }

    public static synchronized List<EpgSubscription> getEpgSubscriptions() {
        try {
            List<EpgSubscription> items = App.gson().fromJson(Prefers.getString(EPG_SUBSCRIPTIONS), EPG_SUBSCRIPTION_TYPE);
            if (items == null) return new ArrayList<>();
            List<EpgSubscription> result = new ArrayList<>();
            boolean repaired = false;
            for (EpgSubscription item : items) {
                if (item == null) {
                    repaired = true;
                    continue;
                }
                if (item.getUrl().isEmpty()) {
                    repaired = true;
                    continue;
                }
                String id = item.getId();
                if (id.isEmpty()) {
                    id = UUID.randomUUID().toString();
                    repaired = true;
                }
                if (item.id == null || item.name == null || item.url == null || item.headers == null || item.type < 0 || item.type > 1) repaired = true;
                result.add(new EpgSubscription(id, item.getName(), item.getUrl(), item.getType(), item.getHeaders()));
            }
            if (repaired) saveEpgSubscriptions(result);
            return result;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static synchronized EpgSubscription saveEpgSubscription(String id, String name, String url, int type, Map<String, String> headers) {
        List<EpgSubscription> items = getEpgSubscriptions();
        EpgSubscription value = new EpgSubscription(id == null || id.isEmpty() ? UUID.randomUUID().toString() : id, name, url, type, headers);
        items.removeIf(item -> item == null || value.id.equals(item.getId()));
        items.add(value);
        saveEpgSubscriptions(items);
        if (Prefers.getString(EPG_ACTIVE).isEmpty()) setActiveEpgSubscription(value.id);
        return value;
    }

    public static synchronized EpgSubscription copyEpgSubscription(String id) {
        EpgSubscription source = findEpgSubscription(id);
        if (source == null) return null;
        String name = source.getName().isEmpty() ? "Copy" : source.getName() + " Copy";
        return saveEpgSubscription(null, name, source.url, source.type, source.headers);
    }

    public static synchronized void deleteEpgSubscription(String id) {
        if (id == null || id.isEmpty()) return;
        List<EpgSubscription> items = getEpgSubscriptions();
        items.removeIf(item -> item == null || id.equals(item.getId()));
        saveEpgSubscriptions(items);
        if (!id.equals(Prefers.getString(EPG_ACTIVE))) return;
        setActiveEpgSubscription(items.isEmpty() ? "" : items.get(0).getId());
    }

    public static synchronized void setActiveEpgSubscription(String id) {
        Prefers.put(EPG_ACTIVE, id == null ? "" : id);
    }

    public static synchronized EpgSubscription getActiveEpgSubscription() {
        List<EpgSubscription> items = getEpgSubscriptions();
        String activeId = Prefers.getString(EPG_ACTIVE);
        for (EpgSubscription item : items) {
            if (item != null && activeId.equals(item.getId())) return item;
        }
        if (items.isEmpty()) {
            if (!activeId.isEmpty()) setActiveEpgSubscription("");
            return null;
        }
        EpgSubscription fallback = items.get(0);
        setActiveEpgSubscription(fallback.getId());
        return fallback;
    }

    public static synchronized boolean isActiveEpgSubscription(String id) {
        return id != null && id.equals(Prefers.getString(EPG_ACTIVE));
    }

    public static Map<String, String> getEpgHeaders(String url) {
        EpgSubscription active = getActiveEpgSubscription();
        return active != null && active.getUrl().equals(url) ? active.getHeaders() : Collections.emptyMap();
    }

    private static EpgSubscription findEpgSubscription(String id) {
        if (id == null || id.isEmpty()) return null;
        for (EpgSubscription item : getEpgSubscriptions()) if (item != null && id.equals(item.getId())) return item;
        return null;
    }

    private static void saveEpgSubscriptions(List<EpgSubscription> items) {
        Prefers.put(EPG_SUBSCRIPTIONS, App.gson().toJson(items));
    }

    public static String getCustomEpg(Live live) {
        SourceOverride value = getOverride(live);
        return value == null ? null : value.epg;
    }

    public static String getCustomUa(Live live) {
        SourceOverride value = getOverride(live);
        return value == null ? null : value.ua;
    }

    public static Map<String, String> getCustomHeader(Live live) {
        SourceOverride value = getOverride(live);
        return value == null ? null : value.header;
    }

    public static void putCustomEpg(Live live, String epg) {
        updateOverride(live, value -> value.epg = emptyToNull(epg));
    }

    public static void putCustomUa(Live live, String ua) {
        updateOverride(live, value -> value.ua = emptyToNull(ua));
    }

    public static void putCustomHeader(Live live, Map<String, String> header) {
        updateOverride(live, value -> value.header = header == null || header.isEmpty() ? null : new HashMap<>(header));
    }

    public static void applySourceOverride(Live live) {
        SourceOverride value = getOverride(live);
        if (value != null && value.epg != null) live.setEpg(value.epg);
        if (value != null && value.ua != null) live.setUa(value.ua);
        if (value != null && value.header != null) live.setHeader(new HashMap<>(value.header));
        EpgSubscription active = getActiveEpgSubscription();
        if (active != null) live.setEpg(active.url);
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                if (active != null && active.url.contains("{")) channel.setEpg(resolveEpg(active.url, channel));
                else if (value != null && value.epg != null && value.epg.contains("{")) channel.setEpg(resolveEpg(value.epg, channel));
                if (value != null && value.ua != null) channel.setUa(value.ua);
                if (value != null && value.header != null) channel.setHeader(new HashMap<>(value.header));
            }
        }
    }

    public static final class EpgSubscription {

        @SerializedName(value = "id", alternate = {"a"})
        private String id;
        @SerializedName(value = "name", alternate = {"b"})
        private String name;
        @SerializedName(value = "url", alternate = {"c"})
        private String url;
        @SerializedName(value = "type", alternate = {"d"})
        private int type;
        @SerializedName(value = "headers", alternate = {"e"})
        private Map<String, String> headers;

        private EpgSubscription(String id, String name, String url, int type, Map<String, String> headers) {
            this.id = id;
            this.name = name == null ? "" : name.trim();
            this.url = url == null ? "" : url.trim();
            this.type = type == 1 ? 1 : 0;
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
        }

        public String getId() {
            return id == null ? "" : id;
        }

        public String getName() {
            return name == null ? "" : name;
        }

        public String getUrl() {
            return url == null ? "" : url;
        }

        public int getType() {
            return type;
        }

        public Map<String, String> getHeaders() {
            return headers == null ? new HashMap<>() : new HashMap<>(headers);
        }
    }

    private static String resolveEpg(String epg, Channel channel) {
        return epg.replace("{id}", channel.getTvgId()).replace("{name}", channel.getTvgName()).replace("{epg}", channel.getEpg());
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static SourceOverride getOverride(Live live) {
        if (live == null || live.getName().isEmpty()) return null;
        return getOverrides().get(live.getName());
    }

    private static void updateOverride(Live live, OverrideUpdater updater) {
        if (live == null || live.getName().isEmpty()) return;
        Map<String, SourceOverride> values = getOverrides();
        SourceOverride value = values.getOrDefault(live.getName(), new SourceOverride());
        updater.update(value);
        if (value.isEmpty()) values.remove(live.getName());
        else values.put(live.getName(), value);
        Prefers.put(SOURCE_OVERRIDES, App.gson().toJson(values));
    }

    private static Map<String, SourceOverride> getOverrides() {
        try {
            Map<String, SourceOverride> values = App.gson().fromJson(Prefers.getString(SOURCE_OVERRIDES), SOURCE_OVERRIDE_TYPE);
            return values == null ? new HashMap<>() : new HashMap<>(values);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private interface OverrideUpdater {
        void update(SourceOverride value);
    }

    private static final class SourceOverride {
        @SerializedName(value = "epg", alternate = {"a"})
        private String epg;
        @SerializedName(value = "ua", alternate = {"b"})
        private String ua;
        @SerializedName(value = "header", alternate = {"c"})
        private Map<String, String> header;

        private boolean isEmpty() {
            return epg == null && ua == null && (header == null || header.isEmpty());
        }
    }

    public static int getScale() {
        return Math.clamp(Prefers.getInt("scale_live", PlayerSetting.getScale()), PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE);
    }

    public static void putScale(int scale) {
        Prefers.put("scale_live", Math.clamp(scale, PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE));
    }
}
