package com.media.ibox.api.config;

import android.text.TextUtils;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.bean.Config;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.server.Server;
import com.media.ibox.setting.Setting;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.Task;
import com.media.ibox.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

abstract class BaseConfig {

    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    private final AtomicInteger taskId = new AtomicInteger(0);

    protected boolean sync;
    protected volatile Config config;
    private volatile Future<?> future;

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    /**
     * 用本地缓存的 json 快速加载，成功返回 true。
     * <p>
     * 用于"缓存优先、后台刷新"：启动时先拿缓存把界面渲染出来，
     * 网络请求随后在后台进行，下载失败也不影响已显示的内容。
     * 默认不缓存，需要的子类覆盖。
     */
    protected boolean loadFromCache(Config config) {
        return false;
    }

    protected abstract boolean isLoaded();

    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            if (config == null) config = defaultConfig();
            Server.get().start();
            Config target = config;
            if (loadFromCache(target)) {
                // 已有缓存：先让界面用上，再在后台静默拉取最新内容
                Task.execute(() -> {
                    try {
                        load(target);
                        target.update();
                        postEvent();
                    } catch (Throwable ignored) {
                    }
                });
            } else {
                load(target);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        return sync || config == null || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    public boolean isInitialized() {
        return config != null;
    }

    public boolean isLoading() {
        Future<?> task = future;
        return task != null && !task.isDone();
    }

    protected void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    protected void setProxy(List<Proxy> proxy) {
        OkHttp.selector().addAll(proxy);
    }

    protected void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void load(Callback callback) {
        int id = taskId.incrementAndGet();
        if (future != null && !future.isDone()) future.cancel(true);
        future = Task.submit(() -> loadConfig(id, config, callback));
        callback.start();
    }

    protected void loadConfig(int id, Config config, Callback callback) {
        boolean cached = false;
        try {
            Server.get().start();
            OkHttp.cancel(getTag());
            // 先用缓存把内容显示出来，再联网刷新；网络失败时保留缓存内容
            cached = loadFromCache(config);
            if (cached) {
                if (taskId.get() != id) return;
                App.post(() -> {
                    if (taskId.get() == id) callback.success();
                });
            }
            load(config);
            if (taskId.get() != id) return;
            if (config.equals(this.config)) config.update();
            if (Setting.isConfigNoticeEnabled() && !TextUtils.isEmpty(config.getNotice())) {
                App.post(() -> Notify.showCompact(config.getNotice()));
            }
            App.post(() -> {
                if (taskId.get() != id) return;
                try {
                    callback.success();
                } finally {
                    if (taskId.get() == id) postEvent();
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            if (isCanceled(e)) return;
            if (taskId.get() != id) return;
            // 缓存已经渲染过内容，网络错误静默忽略，不让界面退回错误态
            if (cached) return;
            String message = TextUtils.isEmpty(config.getUrl()) ? "" : Notify.getError(R.string.error_config_get, e);
            App.post(() -> {
                if (taskId.get() == id) callback.error(message);
            });
        }
    }

    protected boolean isCanceled(Throwable e) {
        if ("Canceled".equals(e.getMessage())) return true;
        if (e instanceof InterruptedException) return true;
        if (e instanceof InterruptedIOException) return true;
        return e.getCause() instanceof InterruptedIOException;
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
            else if (item.isJsonObject()) result.add(item);
        }
        return result;
    }

    private JsonArray fetch(String url) {
        try {
            JsonElement parsed = Json.parse(OkHttp.string(UrlUtil.convert(url)));
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
