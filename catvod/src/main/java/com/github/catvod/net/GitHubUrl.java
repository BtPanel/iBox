package com.github.catvod.net;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class GitHubUrl {

    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";
    private static final String GITHUB_RAW = "https://raw.githubusercontent.com";
    private static final String JSDELIVR = "https://cdn.jsdelivr.net/gh";
    private static final String[] ACCELERATORS = {
            "https://gh-proxy.com/",
            "https://ghfast.top/"
    };
    private static final String[] KNOWN_PROXY_PREFIXES = {
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://github.moeyy.xyz/",
            "https://ghproxy.net/"
    };
    private static volatile boolean accelerationEnabled = true;

    private GitHubUrl() {
    }

    public static boolean isAccelerationEnabled() {
        return accelerationEnabled;
    }

    public static void setAccelerationEnabled(boolean enabled) {
        accelerationEnabled = enabled;
    }

    public static List<String> candidates(String url) {
        if (url == null || url.isEmpty()) return Collections.emptyList();
        if (!accelerationEnabled) return Collections.singletonList(url);
        boolean accelerated = isAccelerated(url);
        String unwrapped = unwrap(url);
        String raw = toRaw(unwrapped);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (raw != null) {
            if (accelerated) candidates.add(url);
            for (String accelerator : ACCELERATORS) candidates.add(accelerator + raw);
            candidates.add(raw);
            String jsDelivr = toJsDelivr(raw);
            if (jsDelivr != null) candidates.add(jsDelivr);
        } else if (isGitHubUrl(unwrapped)) {
            if (accelerated) candidates.add(url);
            for (String accelerator : ACCELERATORS) candidates.add(accelerator + unwrapped);
            candidates.add(unwrapped);
        } else {
            candidates.add(url);
        }
        return new ArrayList<>(candidates);
    }

    public static String accelerateIfNeeded(String url) {
        if (!accelerationEnabled || url == null || url.isEmpty() || isAccelerated(url)) return url;
        return isGitHubUrl(url) ? ACCELERATORS[0] + url : url;
    }

    private static String unwrap(String url) {
        String result = url;
        boolean changed;
        do {
            changed = false;
            for (String prefix : KNOWN_PROXY_PREFIXES) {
                if (!result.startsWith(prefix)) continue;
                String nested = result.substring(prefix.length());
                if (nested.startsWith("http://") || nested.startsWith("https://")) {
                    result = nested;
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private static boolean isAccelerated(String url) {
        for (String prefix : KNOWN_PROXY_PREFIXES) if (url.startsWith(prefix)) return true;
        try {
            URI uri = URI.create(url);
            if (isGitHubHost(uri.getHost())) return false;
            String path = uri.getRawPath();
            return path != null && (path.startsWith("/https://github.com/")
                    || path.startsWith("/https://raw.githubusercontent.com/")
                    || path.startsWith("/https://gist.githubusercontent.com/"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isGitHubUrl(String url) {
        try {
            return isGitHubHost(URI.create(url).getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isGitHubHost(String host) {
        if (host == null) return false;
        String value = host.toLowerCase();
        return value.equals("github.com") || value.endsWith(".github.com")
                || value.equals("githubusercontent.com") || value.endsWith(".githubusercontent.com")
                || value.equals("githubassets.com") || value.endsWith(".githubassets.com");
    }

    private static String toRaw(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String[] parts = uri.getRawPath().split("/");
            if (GITHUB_RAW_HOST.equalsIgnoreCase(host) && parts.length >= 5) return url;
            if (GITHUB_HOST.equalsIgnoreCase(host) && parts.length >= 6
                    && ("raw".equals(parts[3]) || "blob".equals(parts[3]))) {
                return buildRaw(parts[1], parts[2], parts[4], parts, 5, uri.getRawQuery());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String buildRaw(String owner, String repo, String ref, String[] parts, int pathStart, String query) {
        StringBuilder result = new StringBuilder(GITHUB_RAW).append('/').append(owner).append('/').append(repo).append('/').append(ref);
        for (int i = pathStart; i < parts.length; i++) result.append('/').append(parts[i]);
        if (query != null && !query.isEmpty()) result.append('?').append(query);
        return result.toString();
    }

    private static String toJsDelivr(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!GITHUB_RAW_HOST.equalsIgnoreCase(uri.getHost())) return null;
            String[] parts = uri.getRawPath().split("/");
            if (parts.length < 5) return null;
            StringBuilder result = new StringBuilder(JSDELIVR).append('/').append(parts[1]).append('/').append(parts[2]).append('@').append(parts[3]);
            for (int i = 4; i < parts.length; i++) result.append('/').append(parts[i]);
            if (uri.getRawQuery() != null) result.append('?').append(uri.getRawQuery());
            return result.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

}
