package com.imedia.box.bean;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.imedia.box.App;
import com.imedia.box.utils.UrlUtil;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;

    private transient String episode;
    private transient boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Danmaku.class).getType();
        List<Danmaku> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Danmaku from(String path) {
        Danmaku danmaku = new Danmaku();
        danmaku.setName(path);
        danmaku.setUrl(path);
        return danmaku;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEpisode() {
        return TextUtils.isEmpty(episode) ? "" : episode;
    }

    public void setEpisode(String episode) {
        this.episode = episode;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public Uri getUri() {
        return isEmpty() ? null : UrlUtil.uri(UrlUtil.convert(getUrl()));
    }

    public boolean isLocalSpiderProxy() {
        Uri uri = getUri();
        if (uri == null) return false;
        String host = uri.getHost();
        boolean local = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
        return local && isLocalSpiderProxyAction(uri.getQueryParameter("do"));
    }

    static boolean isLocalSpiderProxyAction(String action) {
        if (action == null) return false;
        String normalized = action.toLowerCase(Locale.ROOT);
        return "danmu".equals(normalized) || normalized.contains("autodanmu");
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku it)) return false;
        return getUrl().equals(it.getUrl());
    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
