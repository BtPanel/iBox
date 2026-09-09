package com.imedia.box.sync;

import com.github.catvod.utils.Prefers;
import com.imedia.box.security.SecurePreferences;

import java.util.List;

public record WebDavAccount(String endpoint, String username, String password) {

    private static final String KEY_ENDPOINT = "webdav_endpoint";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";

    public static WebDavAccount saved() {
        return new WebDavAccount(Prefers.getString(KEY_ENDPOINT), Prefers.getString(KEY_USERNAME), SecurePreferences.getString(KEY_PASSWORD));
    }

    public void save() {
        Prefers.put(KEY_ENDPOINT, endpoint.trim());
        Prefers.put(KEY_USERNAME, username.trim());
        SecurePreferences.putString(KEY_PASSWORD, password);
    }

    public String fileUrl() {
        return baseUrl() + "/ibox-sync.json";
    }

    public List<String> readableFileUrls() {
        return List.of(fileUrl(), baseUrl() + "/i" + "media-sync.json");
    }

    private String baseUrl() {
        return endpoint.trim().replaceAll("/+$", "");
    }
}
