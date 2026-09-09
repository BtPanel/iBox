package com.imedia.box.sync;

import androidx.media3.common.C;

import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.annotations.SerializedName;
import com.imedia.box.App;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Keep;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.security.SecurePreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WebDavSyncPayload {

    @SerializedName("version")
    private int version = 1;
    @SerializedName("updatedAt")
    private long updatedAt;
    @SerializedName("favorites")
    private List<Keep> favorites;
    @SerializedName("history")
    private List<History> history;
    @SerializedName("subscriptions")
    private List<Config> subscriptions;
    @SerializedName("settings")
    private Map<String, ?> settings;

    public static WebDavSyncPayload capture() {
        WebDavSyncPayload payload = new WebDavSyncPayload();
        payload.updatedAt = System.currentTimeMillis();
        payload.favorites = AppDatabase.get().getKeepDao().findAll();
        payload.history = AppDatabase.get().getHistoryDao().findAll();
        payload.subscriptions = AppDatabase.get().getConfigDao().findAll();
        payload.settings = filteredSettings();
        return payload;
    }

    public static WebDavSyncPayload fromJson(String json) {
        Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER).create();
        WebDavSyncPayload payload = gson.fromJson(json, WebDavSyncPayload.class);
        if (payload == null || payload.version != 1) throw new IllegalArgumentException("Unsupported sync file");
        return payload;
    }

    private static Map<String, ?> filteredSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : Prefers.getPrefers().getAll().entrySet()) {
            if (!entry.getKey().startsWith("webdav_") && !SecurePreferences.isSensitiveKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public WebDavSyncPayload replaceSelected(WebDavSyncPayload local, WebDavSyncOptions options) {
        if (options.includes(WebDavSyncOptions.FAVORITES)) favorites = local.getFavorites();
        if (options.includes(WebDavSyncOptions.HISTORY) || options.includes(WebDavSyncOptions.PROGRESS)) history = local.getHistory();
        if (options.includes(WebDavSyncOptions.SUBSCRIPTIONS)) subscriptions = local.getSubscriptions();
        if (options.includes(WebDavSyncOptions.SETTINGS)) settings = local.getSettings();
        updatedAt = System.currentTimeMillis();
        return this;
    }

    public WebDavSyncPayload mergeSelected(WebDavSyncPayload local, WebDavSyncOptions options) {
        if (options.includes(WebDavSyncOptions.FAVORITES)) favorites = SyncMerge.newest(local.getFavorites(), getFavorites(), Keep::getKey, Keep::getCreateTime);
        if (options.includes(WebDavSyncOptions.HISTORY) || options.includes(WebDavSyncOptions.PROGRESS)) history = SyncMerge.newest(local.getHistory(), getHistory(), History::getKey, History::getCreateTime);
        if (options.includes(WebDavSyncOptions.SUBSCRIPTIONS)) subscriptions = SyncMerge.newest(local.getSubscriptions(), getSubscriptions(), WebDavSyncPayload::configKey, Config::getTime);
        if (options.includes(WebDavSyncOptions.SETTINGS)) {
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.putAll(getSettings());
            merged.putAll(local.getSettings());
            settings = merged;
        }
        updatedAt = System.currentTimeMillis();
        return this;
    }

    public void apply(WebDavSyncOptions options, boolean overwrite) {
        Map<Integer, Integer> configIds = mapConfigIds();
        if (options.includes(WebDavSyncOptions.SUBSCRIPTIONS)) configIds = applySubscriptions(overwrite);
        if (options.includes(WebDavSyncOptions.FAVORITES)) applyFavorites(configIds, overwrite);
        if (options.includes(WebDavSyncOptions.HISTORY) || options.includes(WebDavSyncOptions.PROGRESS)) applyHistory(configIds, options, overwrite);
        if (options.includes(WebDavSyncOptions.SETTINGS)) for (Map.Entry<String, ?> entry : getSettings().entrySet()) {
            if (!SecurePreferences.isSensitiveKey(entry.getKey())) Prefers.put(entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, Integer> applySubscriptions(boolean overwrite) {
        if (overwrite) AppDatabase.get().getConfigDao().deleteAll();
        Map<Integer, Integer> result = new HashMap<>();
        for (Config incoming : getSubscriptions()) {
            int remoteId = incoming.getId();
            Config existing = AppDatabase.get().getConfigDao().find(incoming.getUrl(), incoming.getType());
            if (existing != null && !overwrite) {
                result.put(remoteId, existing.getId());
                if (incoming.getTime() <= existing.getTime()) continue;
                incoming.setId(existing.getId());
            } else if (!overwrite) {
                incoming.setId(0);
            }
            incoming.save();
            Config saved = AppDatabase.get().getConfigDao().find(incoming.getUrl(), incoming.getType());
            if (saved != null) result.put(remoteId, saved.getId());
        }
        return result;
    }

    private Map<Integer, Integer> mapConfigIds() {
        Map<Integer, Integer> result = new HashMap<>();
        for (Config remote : getSubscriptions()) {
            Config local = AppDatabase.get().getConfigDao().find(remote.getUrl(), remote.getType());
            if (local != null) result.put(remote.getId(), local.getId());
        }
        return result;
    }

    private void applyFavorites(Map<Integer, Integer> configIds, boolean overwrite) {
        if (overwrite) AppDatabase.get().getKeepDao().deleteAll();
        List<Keep> items = new ArrayList<>();
        for (Keep item : getFavorites()) {
            remap(item, configIds);
            items.add(item);
        }
        if (overwrite) AppDatabase.get().getKeepDao().insertOrUpdate(items);
        else AppDatabase.get().getKeepDao().insertOrUpdate(SyncMerge.newest(items, AppDatabase.get().getKeepDao().findAll(), Keep::getKey, Keep::getCreateTime));
    }

    private void applyHistory(Map<Integer, Integer> configIds, WebDavSyncOptions options, boolean overwrite) {
        List<History> local = AppDatabase.get().getHistoryDao().findAll();
        Map<String, History> localByKey = new HashMap<>();
        for (History item : local) localByKey.put(item.getKey(), item);
        List<History> remote = new ArrayList<>();
        for (History item : getHistory()) {
            remap(item, configIds);
            History existing = localByKey.get(item.getKey());
            if (!options.includes(WebDavSyncOptions.HISTORY) && existing != null) copyMetadata(existing, item);
            if (!options.includes(WebDavSyncOptions.PROGRESS)) {
                if (existing == null) resetProgress(item);
                else copyProgress(existing, item);
            }
            remote.add(item);
        }
        if (overwrite && options.includes(WebDavSyncOptions.HISTORY)) {
            AppDatabase.get().getHistoryDao().delete();
            AppDatabase.get().getHistoryDao().insertOrUpdate(remote);
            return;
        }
        if (overwrite && options.includes(WebDavSyncOptions.PROGRESS)) {
            Map<String, History> remoteByKey = new HashMap<>();
            for (History item : remote) remoteByKey.put(item.getKey(), item);
            for (History item : local) {
                resetProgress(item);
                History incoming = remoteByKey.remove(item.getKey());
                if (incoming != null) copyProgress(incoming, item);
            }
            local.addAll(remoteByKey.values());
            AppDatabase.get().getHistoryDao().insertOrUpdate(local);
            return;
        }
        List<History> merged = SyncMerge.newest(remote, local, History::getKey, History::getCreateTime);
        AppDatabase.get().getHistoryDao().insertOrUpdate(merged);
    }

    private static void copyMetadata(History source, History target) {
        target.setVodPic(source.getVodPic());
        target.setVodName(source.getVodName());
        target.setVodFlag(source.getVodFlag());
        target.setVodRemarks(source.getVodRemarks());
        target.setEpisodeUrl(source.getEpisodeUrl());
        target.setRevSort(source.isRevSort());
        target.setRevPlay(source.isRevPlay());
    }

    private static void copyProgress(History source, History target) {
        target.setPosition(source.getPosition());
        target.setDuration(source.getDuration());
        target.setOpening(source.getOpening());
        target.setEnding(source.getEnding());
        target.setSpeed(source.getSpeed());
        target.setScale(source.getScale());
    }

    private static void resetProgress(History item) {
        item.setPosition(C.TIME_UNSET);
        item.setDuration(C.TIME_UNSET);
        item.setOpening(C.TIME_UNSET);
        item.setEnding(C.TIME_UNSET);
        item.setSpeed(1f);
        item.setScale(-1);
    }

    private static void remap(Keep item, Map<Integer, Integer> ids) {
        Integer cid = ids.get(item.getCid());
        if (cid == null || cid == item.getCid()) return;
        String[] parts = item.getKey().split(AppDatabase.SYMBOL);
        if (parts.length >= 2) item.setKey(parts[0] + AppDatabase.SYMBOL + parts[1] + AppDatabase.SYMBOL + cid);
        item.setCid(cid);
    }

    private static void remap(History item, Map<Integer, Integer> ids) {
        Integer cid = ids.get(item.getCid());
        if (cid == null || cid == item.getCid()) return;
        String[] parts = item.getKey().split(AppDatabase.SYMBOL);
        if (parts.length >= 2) item.setKey(parts[0] + AppDatabase.SYMBOL + parts[1] + AppDatabase.SYMBOL + cid);
        item.setCid(cid);
    }

    private static String configKey(Config config) {
        return config.getType() + "\n" + config.getUrl();
    }

    public List<Keep> getFavorites() {
        return favorites == null ? Collections.emptyList() : favorites;
    }

    public List<History> getHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    public List<Config> getSubscriptions() {
        return subscriptions == null ? Collections.emptyList() : subscriptions;
    }

    public Map<String, ?> getSettings() {
        return settings == null ? Collections.emptyMap() : settings;
    }

    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
