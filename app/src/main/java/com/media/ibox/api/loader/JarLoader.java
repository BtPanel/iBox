package com.media.ibox.api.loader;

import android.content.Context;

import com.media.ibox.App;
import com.media.ibox.utils.Download;
import com.media.ibox.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final Set<String> rejected;
    private final List<Spider> retired;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
        rejected = ConcurrentHashMap.newKeySet();
        retired = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 配置变化：关闭宿主引用（DexClassLoader/解析方法/锁），把存量爬虫转入
     * retired 暂存（保持存活，供在途请求收尾），App 退出时才统一销毁。
     */
    public void clear() {
        retired.addAll(spiders.values());
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        rejected.clear();
        recent = null;
    }

    /** App 退出：销毁全部存量与 retired 爬虫。 */
    public void destroy() {
        clear();
        synchronized (retired) {
            for (Spider spider : retired) {
                try {
                    if (spider != null) spider.destroy();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            retired.clear();
        }
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!Path.exists(file) || !file.setReadOnly()) return;
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        invokeInit(loader);
        if (!isGuardReady(loader)) {
            rejected.add(key);
            return;
        }
        invokeProxy(key, loader);
        loaders.put(key, loader);
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private boolean isGuardReady(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("loader");
            if (method.invoke(null) != null) return true;
            new IllegalStateException("Guard loader initialization returned null").printStackTrace();
            return false;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key) || rejected.contains(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key) || rejected.contains(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            if (!md5.isEmpty() && Util.equals(jar, md5)) {
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                load(key, Download.create(jar, Path.jar(jar)).get());
            } else if (jar.startsWith("file")) {
                load(key, Path.local(jar));
            }
        }
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Util.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isRejected(String jar) {
        return rejected.contains(Util.md5(jar));
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        String spKey = jaKey + key;
        Spider cached = spiders.get(spKey);
        if (cached != null) return cached;
        try {
            parseJar(jaKey, jar);
            DexClassLoader loader = loaders.get(jaKey);
            if (loader == null) return new SpiderNull();
            Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
            spider.siteKey = key;
            spider.init(App.get(), ext);
            // 只缓存初始化成功的实例；失败实例不保留，下次访问可重试
            Spider existing = spiders.putIfAbsent(spKey, spider);
            if (existing != null) {
                spider.destroy();
                return existing;
            }
            return spider;
        } catch (Throwable e) {
            e.printStackTrace();
            return new SpiderNull();
        }
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
