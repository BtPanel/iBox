package com.github.catvod.net;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static volatile List<String> customAccelerators = Collections.emptyList();

    private GitHubUrl() {
    }

    public static boolean isAccelerationEnabled() {
        return accelerationEnabled;
    }

    public static void setAccelerationEnabled(boolean enabled) {
        accelerationEnabled = enabled;
    }

    public static List<String> getCustomAccelerators() {
        return customAccelerators;
    }

    /**
     * 设置用户自定义的加速前缀。传入的每项都会规范化为以 "/" 结尾。
     * 自定义前缀会**优先于**内置的 {@link #ACCELERATORS} 被尝试，
     * 内置的仍保留作为兜底，避免自定义失效后整个功能不可用。
     */
    public static void setCustomAccelerators(List<String> list) {
        if (list == null || list.isEmpty()) {
            customAccelerators = Collections.emptyList();
            return;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : list) {
            String value = normalize(item);
            if (value != null) normalized.add(value);
        }
        customAccelerators = Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    public static void setCustomAccelerators(String text) {
        setCustomAccelerators(parse(text));
    }

    /**
     * 把多行文本解析为前缀列表：按行拆分，忽略空行与 # 注释。
     */
    public static List<String> parse(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String value = normalize(line);
            if (value != null) result.add(value);
        }
        return result;
    }

    private static String normalize(String item) {
        if (item == null) return null;
        String value = item.trim();
        if (value.isEmpty() || value.startsWith("#")) return null;
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
        if (!value.endsWith("/")) value = value + "/";
        return value;
    }

    /**
     * 所有生效的加速前缀：自定义在前，内置在后。
     */
    private static List<String> accelerators() {
        List<String> custom = customAccelerators;
        if (custom.isEmpty()) return Arrays.asList(ACCELERATORS);
        List<String> all = new ArrayList<>(custom.size() + ACCELERATORS.length);
        all.addAll(custom);
        for (String builtin : ACCELERATORS) if (!custom.contains(builtin)) all.add(builtin);
        return all;
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
            for (String accelerator : accelerators()) candidates.add(accelerator + raw);
            candidates.add(raw);
            String jsDelivr = toJsDelivr(raw);
            if (jsDelivr != null) candidates.add(jsDelivr);
        } else if (isGitHubUrl(unwrapped)) {
            if (accelerated) candidates.add(url);
            for (String accelerator : accelerators()) candidates.add(accelerator + unwrapped);
            candidates.add(unwrapped);
        } else {
            candidates.add(url);
        }
        return new ArrayList<>(candidates);
    }

    public static String accelerateIfNeeded(String url) {
        if (!accelerationEnabled || url == null || url.isEmpty() || isAccelerated(url)) return url;
        List<String> all = accelerators();
        return isGitHubUrl(url) && !all.isEmpty() ? all.get(0) + url : url;
    }

    /**
     * 识别加速前缀时使用：已知的内置代理前缀 + 用户自定义前缀。
     */
    private static List<String> proxyPrefixes() {
        List<String> custom = customAccelerators;
        if (custom.isEmpty()) return Arrays.asList(KNOWN_PROXY_PREFIXES);
        LinkedHashSet<String> all = new LinkedHashSet<>(custom);
        all.addAll(Arrays.asList(KNOWN_PROXY_PREFIXES));
        return new ArrayList<>(all);
    }

    private static String unwrap(String url) {
        String result = url;
        boolean changed;
        do {
            changed = false;
            for (String prefix : proxyPrefixes()) {
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
        for (String prefix : proxyPrefixes()) if (url.startsWith(prefix)) return true;
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
