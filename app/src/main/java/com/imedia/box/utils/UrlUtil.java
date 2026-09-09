package com.imedia.box.utils;

import android.net.Uri;

import com.imedia.box.server.Server;
import com.github.catvod.Proxy;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlUtil {

    private static final String SERVER_PORT = "(?:997[89]|998\\d|999[0-8])";
    private static final Pattern LOCAL_PROXY = Pattern.compile("(127\\.0\\.0\\.1:)(" + SERVER_PORT + ")(?=/proxy(?:[/?#]|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_LOCAL_PROXY = Pattern.compile("(127\\.0\\.0\\.1%3a)(" + SERVER_PORT + ")(?=%2fproxy(?:%3f|%2f|%23|$))", Pattern.CASE_INSENSITIVE);

    public static Uri uri(String url) {
        url = normalizeLocalProxyPort(url).trim().replace("\\", "");
        return url.startsWith("/") ? Uri.fromFile(new File(url)) : Uri.parse(url);
    }

    public static String scheme(String url) {
        return url == null ? "" : scheme(uri(url));
    }

    public static String scheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT).trim();
    }

    public static String host(String url) {
        return url == null ? "" : host(uri(url));
    }

    public static String host(Uri uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT).trim();
    }

    public static String path(String url) {
        return url == null ? "" : path(uri(url));
    }

    public static String path(Uri uri) {
        String path = uri.getLastPathSegment();
        return path == null ? "" : path.trim();
    }

    public static String resolve(String baseUri, String referenceUri) {
        return UriUtil.resolve(baseUri, referenceUri);
    }

    public static String convert(String url) {
        String scheme = scheme(url);
        String prefix = scheme + "://";
        String converted = switch (scheme) {
            case "assets" -> url.replace(prefix, Server.get().getAddress("/"));
            case "proxy" -> url.replace(prefix, Server.get().getAddress("/proxy?"));
            case "file" -> Server.get().getAddress("/file/") + Uri.encode(url.substring((prefix).length()), "/");
            default -> url;
        };
        return normalizeLocalProxyPort(converted);
    }

    public static String normalizeLocalProxyPort(String url) {
        return normalizeLocalProxyPort(url, Proxy.getPort());
    }

    static String normalizeLocalProxyPort(String url, int currentPort) {
        if (url == null || currentPort < 9978 || currentPort > 9998) return url;
        return replacePort(ENCODED_LOCAL_PROXY, replacePort(LOCAL_PROXY, url, currentPort), currentPort);
    }

    private static String replacePort(Pattern pattern, String url, int currentPort) {
        Matcher matcher = pattern.matcher(url);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + currentPort));
        matcher.appendTail(result);
        return result.toString();
    }

    public static String getName(String url) {
        Uri uri = uri(url);
        String path = path(uri);
        String host = host(uri);
        return !path.isEmpty() ? path : !host.isEmpty() ? host : url;
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }
}
