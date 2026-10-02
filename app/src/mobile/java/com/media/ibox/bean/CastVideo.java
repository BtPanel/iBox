package com.media.ibox.bean;

import com.media.ibox.player.PlayerManager;
import com.media.ibox.server.Server;

import java.util.LinkedHashMap;
import java.util.Map;

public record CastVideo(String name, String url, long position, Map<String, String> headers, String image) {

    public static CastVideo create(PlayerManager player, long position) {
        return new CastVideo(player.getMediaTitle(), player.getUrl(), position, player.getHeaders(), "");
    }

    public CastVideo image(String image) {
        return new CastVideo(name, url, position, headers, image == null ? "" : image);
    }

    public CastVideo {
        headers = new LinkedHashMap<>(headers);
        if (url.startsWith("file")) url = Server.get().getFileStreamAddress(url);
        else url = Server.get().exposeLocalStream(url);
    }
}
