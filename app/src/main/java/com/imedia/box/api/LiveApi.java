package com.imedia.box.api;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.parser.EpgParser;
import com.imedia.box.api.parser.LiveParser;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Result;
import com.imedia.box.player.extractor.Source;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static final int GUIDE_START_OFFSET = -6;
    public static final int GUIDE_END_OFFSET = 2;

    private static final String DEFAULT_EPG = "https://epg.112114.xyz/?ch={name}&date={date}";
    private static final String DEFAULT_EPG_XML = "https://epg.pw/xmltv/epg_CN.xml.gz";

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        return parseXml(item, false);
    }

    public static boolean parseXml(@NonNull Live item, boolean forceRefresh) {
        LiveSetting.EpgSubscription active = LiveSetting.getActiveEpgSubscription();
        if (active != null) {
            if (active.getType() == 1) return false;
            return startXml(item, active.getUrl(), forceRefresh) || startXml(item, DEFAULT_EPG_XML, forceRefresh);
        }
        boolean success = item.getEpgXml().stream().map(url -> startXml(item, url, forceRefresh)).reduce(false, Boolean::logicalOr);
        return success || startXml(item, DEFAULT_EPG_XML, forceRefresh);
    }

    public static boolean cacheEpg(@NonNull Live item) {
        LiveSetting.EpgSubscription active = LiveSetting.getActiveEpgSubscription();
        if (active != null) {
            if (active.getType() == 1) return true;
            return cacheXml(active.getUrl()) || cacheXml(DEFAULT_EPG_XML);
        }
        boolean success = item.getEpgXml().stream().map(LiveApi::cacheXml).reduce(false, Boolean::logicalOr);
        return success || cacheXml(DEFAULT_EPG_XML);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        Epg epg = findEpg(item, today);
        if (epg.getList().isEmpty()) {
            fetchDefaultEpg(item, zoneId, today);
            epg = findEpg(item, today);
        }
        return epg.selected();
    }

    public static void getEpgGuide(@NonNull Channel item, @NonNull ZoneId zoneId) {
        for (int offset = GUIDE_START_OFFSET; offset <= GUIDE_END_OFFSET; offset++) {
            fetchEpgDay(item, zoneId, offset);
        }
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        return startXml(item, url, false);
    }

    private static boolean startXml(Live item, String url, boolean forceRefresh) {
        try {
            EpgParser.start(item, url, forceRefresh);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (!need) return;
        try {
            LiveSetting.EpgSubscription active = LiveSetting.getActiveEpgSubscription();
            String json = active != null && active.getType() == 1
                    ? OkHttp.string(url, active.getHeaders())
                    : OkHttp.string(url);
            item.setData(Epg.objectFrom(json, item.getTvgId(), zoneId));
        } catch (Exception ignored) {
        }
    }

    private static boolean cacheXml(String url) {
        try {
            EpgParser.cache(url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchDefaultEpg(@NonNull Channel item, @NonNull ZoneId zoneId, String date) {
        try {
            String url = DEFAULT_EPG.replace("{name}", Uri.encode(item.getTvgName())).replace("{date}", date);
            Epg epg = Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId);
            if (!epg.getList().isEmpty()) item.setData(epg);
        } catch (Exception ignored) {
        }
    }

    private static Epg findEpg(@NonNull Channel item, String date) {
        return item.getDataList().stream().filter(epg -> epg.equal(date)).findFirst().orElseGet(Epg::new);
    }
}
