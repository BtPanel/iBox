package com.imedia.box.server;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;
import com.imedia.box.security.SecurePreferences;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;

public final class RemoteAccess {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Long> CLIENTS = new ConcurrentHashMap<>();
    private static final PairingSessionStore SESSIONS = new PairingSessionStore(30L * 60 * 1000);
    private static final PinAttemptLimiter PIN_LIMITER = new PinAttemptLimiter(5, 5L * 60 * 1000);
    private static final String COOKIE = "ibox_pair";
    private static final String LEGACY_COOKIE = "i" + "media_pair";
    private static final String STREAM_TOKEN_PARAM = "stream_token";
    private static final String ACCESS_TOKEN_HEADER = "x-ibox-token";
    private static final String STREAM_TOKEN_HEADER = "x-ibox-stream-token";

    private RemoteAccess() {
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean("remote_access_enabled", false);
    }

    public static void setEnabled(boolean enabled) {
        Prefers.put("remote_access_enabled", enabled);
        if (enabled) ensureCredentials();
        else clearRemoteSessions();
    }

    public static String getPin() {
        ensureCredentials();
        return SecurePreferences.getString("remote_access_pin");
    }

    public static String getToken() {
        ensureCredentials();
        return SecurePreferences.getString("remote_access_token");
    }

    public static String getStreamToken() {
        ensureCredentials();
        return SecurePreferences.getString("remote_stream_token");
    }

    public static void rotateCredentials() {
        SecurePreferences.putString("remote_access_pin", String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)));
        SecurePreferences.putString("remote_access_token", UUID.randomUUID().toString().replace("-", ""));
        SecurePreferences.putString("remote_stream_token", UUID.randomUUID().toString().replace("-", ""));
        CLIENTS.clear();
        SESSIONS.clear();
        PIN_LIMITER.clear();
    }

    public static void disconnectAll() {
        SecurePreferences.putString("remote_access_token", UUID.randomUUID().toString().replace("-", ""));
        CLIENTS.clear();
        SESSIONS.clear();
        PIN_LIMITER.clear();
    }

    public static List<String> getClients() {
        long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        CLIENTS.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        List<String> result = new ArrayList<>();
        CLIENTS.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).forEach(entry ->
                result.add(entry.getKey() + "  " + format.format(new Date(entry.getValue()))));
        return result;
    }

    public static boolean isLoopback(IHTTPSession session) {
        String ip = session.getRemoteIpAddress();
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    public static boolean isPrivateNetwork(IHTTPSession session) {
        try {
            InetAddress address = InetAddress.getByName(session.getRemoteIpAddress());
            byte[] bytes = address.getAddress();
            boolean uniqueLocalV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || uniqueLocalV6;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAuthorized(IHTTPSession session) {
        if (isLoopback(session)) return true;
        if (!isEnabled() || !isPrivateNetwork(session)) return false;
        String client = session.getRemoteIpAddress();
        String managementToken = managementCredential(session.getHeaders());
        String pairingToken = pairingCredential(session.getHeaders().get("cookie"));
        boolean authorized = getToken().equals(managementToken)
                || SESSIONS.isValid(pairingToken, client, System.currentTimeMillis());
        if (!authorized && hasPairingAttempt(session)) authorized = authorizePin(session, client);
        if (authorized) CLIENTS.put(session.getRemoteIpAddress(), System.currentTimeMillis());
        return authorized;
    }

    public static boolean isStreamAuthorized(IHTTPSession session) {
        if (isLoopback(session)) return true;
        String token = session.getParms().get(STREAM_TOKEN_PARAM);
        if (TextUtils.isEmpty(token)) token = tokenHeader(session.getHeaders(), true);
        return !TextUtils.isEmpty(token) && getStreamToken().equals(token);
    }

    public static String authorizeStreamUrl(String url) {
        return appendQueryParameter(url, STREAM_TOKEN_PARAM, getStreamToken());
    }

    public static boolean pairedWithPin(IHTTPSession session) {
        return isEnabled() && hasPairingAttempt(session) && getPin().equals(session.getParms().get("pin"));
    }

    public static String cookieHeader(IHTTPSession session) {
        String token = SESSIONS.create(session.getRemoteIpAddress(), System.currentTimeMillis());
        return COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=1800";
    }

    static String managementCredential(Map<String, String> headers) {
        String token = tokenHeader(headers, false);
        return empty(token) ? null : token;
    }

    static String pairingCredential(String cookies) {
        String token = cookieValue(cookies);
        return empty(token) ? null : token;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    static boolean hasPairingAttempt(IHTTPSession session) {
        return session.getMethod() == fi.iki.elonen.NanoHTTPD.Method.POST && !TextUtils.isEmpty(session.getParms().get("pin"));
    }

    private static boolean authorizePin(IHTTPSession session, String client) {
        long now = System.currentTimeMillis();
        if (!PIN_LIMITER.isAllowed(client, now)) return false;
        if (getPin().equals(session.getParms().get("pin"))) {
            PIN_LIMITER.recordSuccess(client);
            return true;
        }
        PIN_LIMITER.recordFailure(client, now);
        return false;
    }

    private static void clearRemoteSessions() {
        CLIENTS.clear();
        SESSIONS.clear();
        PIN_LIMITER.clear();
    }

    static String cookieValue(String cookies) {
        if (cookies == null || cookies.isEmpty()) return "";
        String legacy = "";
        for (String part : cookies.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if (COOKIE.equals(pair[0])) return pair[1];
            if (LEGACY_COOKIE.equals(pair[0])) legacy = pair[1];
        }
        return legacy;
    }

    private static void ensureCredentials() {
        if (TextUtils.isEmpty(SecurePreferences.getString("remote_access_pin"))
                || TextUtils.isEmpty(SecurePreferences.getString("remote_access_token"))
                || TextUtils.isEmpty(SecurePreferences.getString("remote_stream_token"))) rotateCredentials();
    }

    static String appendQueryParameter(String url, String key, String value) {
        if (url == null || url.isEmpty() || key == null || key.isEmpty() || value == null || value.isEmpty()) return url;
        String fragment = "";
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = url.substring(fragmentIndex);
            url = url.substring(0, fragmentIndex);
        }
        String separator = url.contains("?") ? (url.endsWith("?") || url.endsWith("&") ? "" : "&") : "?";
        return url + separator + key + "=" + value + fragment;
    }

    static String tokenHeader(Map<String, String> headers, boolean stream) {
        String suffix = stream ? "-stream-token" : "-token";
        String token = headers.get(stream ? STREAM_TOKEN_HEADER : ACCESS_TOKEN_HEADER);
        return token == null || token.isEmpty() ? headers.get("x-i" + "media" + suffix) : token;
    }
}
