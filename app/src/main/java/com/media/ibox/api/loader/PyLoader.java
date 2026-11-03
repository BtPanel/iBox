package com.media.ibox.api.loader;

import com.media.ibox.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.GitHubUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final List<Spider> retired;
    private final Loader loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        retired = Collections.synchronizedList(new ArrayList<>());
        loader = new Loader();
    }

    /** 配置变化：存量爬虫转入 retired 暂存（保持存活），Python 运行时保留到 App 退出。 */
    public void clear() {
        retired.addAll(spiders.values());
        spiders.clear();
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

    public Spider getSpider(String key, String api, String ext) {
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        synchronized (spiders) {
            cached = spiders.get(key);
            if (cached != null) return cached;
            for (String candidate : GitHubUrl.candidates(api)) {
                Spider spider = null;
                try {
                    spider = loader.spider(candidate);
                    spider.siteKey = key;
                    spider.init(App.get(), ext);
                    spiders.put(key, spider);
                    return spider;
                } catch (Throwable e) {
                    if (spider != null) spider.destroy();
                    e.printStackTrace();
                }
            }
            return new SpiderNull();
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
