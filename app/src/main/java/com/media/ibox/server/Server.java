package com.media.ibox.server;

import android.net.Uri;

import com.media.ibox.service.PlaybackService;
import com.media.ibox.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

public class Server {

    private volatile PlaybackService service;
    private volatile Nano nano;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getStreamAddress(String path) {
        return RemoteAccess.authorizeStreamUrl(getAddress(false) + path);
    }

    public String getFileStreamAddress(String path) {
        String value = path == null ? "" : path.replace("file://", "").replace("file:", "");
        if (value.startsWith(Path.rootPath())) value = value.substring(Path.rootPath().length());
        while (value.startsWith("/")) value = value.substring(1);
        return getStreamAddress("/file/" + Uri.encode(value, "/"));
    }

    public String exposeLocalStream(String url) {
        if (url == null || !url.contains("127.0.0.1:" + Proxy.getPort())) return url;
        String exposed = url.replace("127.0.0.1", Util.getIp());
        return isStreamUrl(exposed) ? RemoteAccess.authorizeStreamUrl(exposed) : exposed;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    private boolean isStreamUrl(String url) {
        String base = getAddress(false);
        if (!url.startsWith(base)) return false;
        String path = url.substring(base.length());
        return ServerAccessPolicy.isStreamRequest(path);
    }

    public synchronized void start() {
        if (nano != null) return;
        for (int i = 9978; i < 9999; i++) {
            try {
                nano = new Nano(i);
                nano.start(500);
                Proxy.set(i);
                break;
            } catch (Throwable e) {
                nano = null;
            }
        }
    }

    public void stop() {
        Task.execute(() -> {
            if (nano != null) nano.stop();
            service = null;
            nano = null;
        });
    }
}
