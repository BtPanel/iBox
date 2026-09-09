package com.imedia.box.sync;

import com.github.catvod.net.OkHttp;
import com.imedia.box.App;
import com.imedia.box.utils.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class WebDavSyncManager {

    public enum Action {
        MERGE, UPLOAD, DOWNLOAD
    }

    public interface Callback {
        void success();

        void error(String message);
    }

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(20);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private WebDavSyncManager() {
    }

    public static void execute(WebDavAccount account, WebDavSyncOptions options, Action action, Callback callback) {
        Task.executeSerial(() -> {
            try {
                WebDavSyncPayload remote = download(account);
                WebDavSyncPayload local = WebDavSyncPayload.capture();
                if (action == Action.DOWNLOAD) {
                    if (remote == null) throw new IOException("Sync file not found");
                    remote.apply(options, true);
                } else if (action == Action.UPLOAD) {
                    if (remote == null) remote = new WebDavSyncPayload();
                    upload(account, remote.replaceSelected(local, options));
                } else {
                    if (remote == null) remote = new WebDavSyncPayload();
                    remote.mergeSelected(local, options).apply(options, false);
                    upload(account, remote.replaceSelected(WebDavSyncPayload.capture(), options));
                }
                App.post(callback::success);
            } catch (Exception e) {
                String message = e.getMessage();
                App.post(() -> callback.error(message == null || message.isBlank() ? e.getClass().getSimpleName() : message));
            }
        });
    }

    private static WebDavSyncPayload download(WebDavAccount account) throws IOException {
        for (String url : account.readableFileUrls()) {
            WebDavSyncPayload payload = download(account, url);
            if (payload != null) return payload;
        }
        return null;
    }

    private static WebDavSyncPayload download(WebDavAccount account, String url) throws IOException {
        Request request = request(account, url).get().build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            if (response.code() == 404) return null;
            if (!response.isSuccessful()) throw status(response);
            if (response.body() == null) throw new IOException("Empty sync file");
            return WebDavSyncPayload.fromJson(response.body().string());
        }
    }

    private static void upload(WebDavAccount account, WebDavSyncPayload payload) throws IOException {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        Request request = request(account).put(RequestBody.create(bytes, JSON)).build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            if (response.isSuccessful()) return;
            if (response.code() != 404 && response.code() != 409) throw status(response);
        }
        createDirectory(account);
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            if (!response.isSuccessful()) throw status(response);
        }
    }

    private static void createDirectory(WebDavAccount account) throws IOException {
        Request request = request(account, account.endpoint().trim().replaceAll("/+$", ""))
                .method("MKCOL", RequestBody.create(new byte[0])).build();
        try (Response response = OkHttp.client(TIMEOUT).newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 405) throw status(response);
        }
    }

    private static Request.Builder request(WebDavAccount account) {
        return request(account, account.fileUrl());
    }

    private static Request.Builder request(WebDavAccount account, String url) {
        Request.Builder builder = new Request.Builder().url(url);
        if (!account.username().isBlank()) builder.header("Authorization", Credentials.basic(account.username(), account.password(), StandardCharsets.UTF_8));
        return builder.header("Accept", "application/json");
    }

    private static IOException status(Response response) {
        return new IOException("WebDAV HTTP " + response.code());
    }
}
