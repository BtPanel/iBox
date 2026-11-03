package com.media.ibox.api.loader;

import com.media.ibox.App;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final List<Spider> retired;
    private final Loader loader;
    private volatile String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        retired = Collections.synchronizedList(new ArrayList<>());
        loader = new Loader();
    }

    /**
     * 配置变化：存量爬虫转入 retired 暂存（保持存活），QuickJS 模块缓存
     * 保留到 App 退出统一释放，避免在途请求中断。
     */
    public void clear() {
        retired.addAll(spiders.values());
        spiders.clear();
        recent = null;
    }

    /** App 退出：销毁全部存量与 retired 爬虫并释放 QuickJS 模块。 */
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
        Module.get().clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        try {
            Spider spider = loader.spider(api, BaseLoader.get().dex(jar));
            spider.siteKey = key;
            spider.init(App.get(), ext);
            // 只缓存初始化成功的实例；失败实例不保留，下次访问可重试
            Spider existing = spiders.putIfAbsent(key, spider);
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

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
