package com.imedia.box.api.loader;

import com.imedia.box.App;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.GitHubUrl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        spiders.clear();
        recent = null;
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
