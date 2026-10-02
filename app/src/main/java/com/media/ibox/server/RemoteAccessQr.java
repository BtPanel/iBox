package com.media.ibox.server;

import java.net.URI;

public final class RemoteAccessQr {

    private RemoteAccessQr() {
    }

    public static String url(boolean enabled, String address) {
        if (!enabled || address == null) return null;
        try {
            URI uri = URI.create(address);
            String host = uri.getHost();
            if (!"http".equals(uri.getScheme()) || host == null || host.isBlank()
                    || "127.0.0.1".equals(host) || "::1".equals(host)
                    || "0.0.0.0".equals(host) || uri.getPort() <= 0
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) return null;
            return address;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
