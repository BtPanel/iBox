package com.media.ibox.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record LiveLineKey(String sourceKey, String group, String channel, String urlDigest) {

    public LiveLineKey {
        sourceKey = text(sourceKey);
        group = text(group);
        channel = text(channel);
        urlDigest = text(urlDigest);
    }

    public static LiveLineKey create(String sourceKey, String group, String channel, String rawUrl) {
        String url = text(rawUrl).split("\\$", 2)[0];
        return new LiveLineKey(sourceKey, group, channel, sha256(url));
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
