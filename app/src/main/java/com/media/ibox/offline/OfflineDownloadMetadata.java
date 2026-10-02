package com.media.ibox.offline;

import androidx.media3.common.util.Util;

import com.media.ibox.App;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OfflineDownloadMetadata {

    private String vodName;
    private String vodPic;
    private String siteKey;
    private String vodId;
    private String flag;
    private String episode;
    private String episodeId;
    private Map<String, String> headers;

    public OfflineDownloadMetadata() {
    }

    public OfflineDownloadMetadata(String vodName, String vodPic, String siteKey, String vodId, String flag, String episode, String episodeId, Map<String, String> headers) {
        this.vodName = value(vodName);
        this.vodPic = value(vodPic);
        this.siteKey = value(siteKey);
        this.vodId = value(vodId);
        this.flag = value(flag);
        this.episode = value(episode);
        this.episodeId = value(episodeId);
        this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
    }

    public static OfflineDownloadMetadata from(byte[] data) {
        try {
            OfflineDownloadMetadata metadata = App.gson().fromJson(Util.fromUtf8Bytes(data), OfflineDownloadMetadata.class);
            return metadata == null ? new OfflineDownloadMetadata() : metadata;
        } catch (Exception ignored) {
            return new OfflineDownloadMetadata();
        }
    }

    public byte[] toBytes() {
        return App.gson().toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    public String getVodName() {
        return value(vodName);
    }

    public String getVodPic() {
        return value(vodPic);
    }

    public String getSiteKey() {
        return value(siteKey);
    }

    public String getVodId() {
        return value(vodId);
    }

    public String getFlag() {
        return value(flag);
    }

    public String getEpisode() {
        return value(episode);
    }

    public String getEpisodeId() {
        return value(episodeId);
    }

    public Map<String, String> getHeaders() {
        return headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
    }

    public String getDisplayName() {
        if (getEpisode().isEmpty()) return getVodName();
        return getVodName().isEmpty() ? getEpisode() : getVodName() + " - " + getEpisode();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
