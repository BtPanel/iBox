package com.imedia.box.server;

public final class ServerAccessPolicy {

    private ServerAccessPolicy() {
    }

    static boolean isLoopbackOnly(String url) {
        return matchesEndpoint(url, "/cache") || matchesEndpoint(url, "/parse");
    }

    static boolean isStreamRequest(String url) {
        return matchesEndpoint(url, "/file") || matchesEndpoint(url, "/proxy") || matchesEndpoint(url, "/tvbus");
    }

    static boolean requiresRemoteAuthorization(String url) {
        return matchesEndpoint(url, "/action") || matchesEndpoint(url, "/manage") || matchesEndpoint(url, "/upload")
                || matchesEndpoint(url, "/newFolder") || matchesEndpoint(url, "/delFolder") || matchesEndpoint(url, "/delFile")
                || matchesEndpoint(url, "/device") || matchesEndpoint(url, "/media") || isAssetRequest(url);
    }

    static boolean isAssetRequest(String url) {
        return "/".equals(url) || url.endsWith(".html") || url.startsWith("/js/") || url.startsWith("/css/");
    }

    public static boolean matchesEndpoint(String url, String prefix) {
        return url != null && (url.equals(prefix) || url.startsWith(prefix + "/") || url.startsWith(prefix + "?"));
    }
}
