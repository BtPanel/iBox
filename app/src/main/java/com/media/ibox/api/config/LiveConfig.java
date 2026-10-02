package com.media.ibox.api.config;

import android.text.TextUtils;

import com.media.ibox.api.Decoder;
import com.media.ibox.api.LiveApi;
import com.media.ibox.api.loader.BaseLoader;
import com.media.ibox.api.parser.LiveParser;
import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.Depot;
import com.media.ibox.bean.Group;
import com.media.ibox.bean.Keep;
import com.media.ibox.bean.Live;
import com.media.ibox.bean.Rule;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.setting.LiveSetting;
import com.media.ibox.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LiveConfig extends BaseConfig {

    private static final String TAG = LiveConfig.class.getSimpleName();

    private Live home;
    private List<Live> lives;
    private List<Rule> rules;
    private List<String> ads;

    public static LiveConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static String getResp() {
        return get().getHome().getCore().getResp();
    }

    public static int getHomeIndex() {
        return get().getLives().indexOf(get().getHome());
    }

    public static boolean isOnly() {
        return get().getLives().size() == 1;
    }

    public static boolean isEmpty() {
        return get().getHome().isEmpty();
    }

    public static boolean hasUrl() {
        return !TextUtils.isEmpty(getUrl());
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public LiveConfig init() {
        return config(Config.live());
    }

    public LiveConfig config(Config config) {
        this.config = config;
        if (config.isEmpty()) return this;
        this.sync = config.getUrl().equals(VodConfig.getUrl());
        return this;
    }

    public LiveConfig clear() {
        ads = null;
        home = null;
        lives = null;
        rules = null;
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.live();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.live();
    }

    @Override
    protected void load(Config config) throws Throwable {
        String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
        // 缓存原始内容，供下次启动秒开
        config.json(json).save();
        if (Json.isObj(json)) checkJson(config, Json.parse(json).getAsJsonObject());
        else parseText(config, json);
    }

    @Override
    protected boolean loadFromCache(Config config) {
        if (config == null || TextUtils.isEmpty(config.getJson())) return false;
        try {
            String json = config.getJson();
            if (Json.isObj(json)) checkJson(config, Json.parse(json).getAsJsonObject());
            else parseText(config, json);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    protected boolean isLoaded() {
        return !getLives().isEmpty() && !getHome().getGroups().isEmpty();
    }

    @Override
    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            super.ensureLoaded();
            LiveApi.parse(getHome());
            LiveApi.parseXml(getHome());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (sync) return;
        load(new Callback());
    }

    private void parseText(Config config, String text) {
        Live live = new Live(UrlUtil.getName(config.getUrl()), config.getUrl()).sync();
        lives = new ArrayList<>(List.of(live));
        LiveParser.text(live, text);
        LiveSetting.applySourceOverride(live);
        setHome(config, live, false);
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = Config.importDepot(items, LIVE);
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        Config selected = configs.get(0);
        load(this.config = selected);
        selected.update();
        Config.delete(config.getUrl(), LIVE);
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initLive(config, object);
    }

    public void parse(JsonObject object) {
        initLive(getConfig(), object);
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, false);
        setLives(Json.safeListElement(object, "lives").stream().map(e -> Live.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        Map<String, Live> items = Live.findAll().stream().collect(Collectors.toMap(Live::getName, Function.identity()));
        getLives().forEach(live -> live.sync(items.get(live.getName())));
        getLives().forEach(LiveSetting::applySourceOverride);
        setHome(config, getLives().isEmpty() ? new Live() : getLives().stream().filter(item -> item.getName().equals(config.getHome())).findFirst().orElse(getLives().get(0)), false);
    }

    public void setKeep(Channel channel) {
        if (home != null && !channel.getGroup().isHidden()) home.keep(channel).saveAsync();
    }

    public void applyKeepsToGroups(List<Group> items) {
        Set<String> key = Keep.getLive().stream().map(Keep::getKey).collect(Collectors.toSet());
        items.stream().filter(group -> !group.isKeep())
                .flatMap(group -> group.getChannel().stream())
                .filter(channel -> key.contains(channel.getName()))
                .forEach(channel -> items.get(0).add(channel));
    }

    public int[] findKeepPosition(List<Group> items) {
        String[] splits = getHome().getKeep().split(AppDatabase.SYMBOL);
        if (splits.length < 3) return new int[]{1, 0};
        for (int i = 0; i < items.size(); i++) {
            Group group = items.get(i);
            if (group.getName().equals(splits[0])) {
                int j = group.find(splits[1]);
                if (j != -1) {
                    group.getChannel().get(j).setIndex(splits[2]);
                    return new int[]{i, j};
                }
            }
        }
        return new int[]{1, 0};
    }

    public int[] findByChannelNumber(String number, List<Group> items) {
        int num = Integer.parseInt(number);
        for (int i = 0; i < items.size(); i++) {
            int j = items.get(i).find(num);
            if (j != -1) return new int[]{i, j};
        }
        return new int[]{-1, -1};
    }

    public List<Live> getLives() {
        return lives == null ? lives = new ArrayList<>() : lives;
    }

    private void setLives(List<Live> lives) {
        this.lives = lives;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Live getHome() {
        return home == null ? new Live() : home;
    }

    public void setHome(Live home) {
        setHome(getConfig(), home, true);
    }

    public Live getLive(String key) {
        return getLives().stream().filter(item -> item.getName().equals(key)).findFirst().orElse(new Live());
    }

    private void setHome(Config config, Live live, boolean save) {
        home = live;
        home.setSelected(true);
        config.setHome(home.getName());
        if (save) config.save();
        getLives().forEach(item -> item.setSelected(home));
        if (!save && (home.isBoot() || LiveSetting.isBoot())) ConfigEvent.boot();
    }

    private static class Loader {
        static volatile LiveConfig INSTANCE = new LiveConfig();
    }
}
