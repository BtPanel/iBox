package com.media.ibox.utils;

import com.media.ibox.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Future;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private Callback callback;
    private Future<?> future;
    private long maxBytes;
    private long timeout;
    private String tag;
    private Map<String, String> headers;

    public static Download create(String url, File file) {
        return new Download(url, file);
    }

    public Download(String url, File file) {
        this.maxBytes = Long.MAX_VALUE;
        this.timeout = 30000;
        this.tag = url;
        this.url = url;
        this.file = file;
        this.headers = Collections.emptyMap();
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public Download maxBytes(long maxBytes) {
        this.maxBytes = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
        return this;
    }

    public Download timeout(long timeout) {
        this.timeout = timeout > 0 ? timeout : 30000;
        return this;
    }

    public Download headers(Map<String, String> headers) {
        this.headers = headers == null ? Collections.emptyMap() : new HashMap<>(headers);
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }

    public Download cancel() {
        if (future != null) future.cancel(true);
        OkHttp.cancel(tag);
        future = null;
        return this;
    }

    private void doInBackground() {
        Request request = new Request.Builder().url(url).headers(Headers.of(headers)).tag(tag).build();
        try (Response res = OkHttp.client(timeout).newCall(request).execute()) {
            download(res.body().byteStream(), getLength(res));
            if (callback != null) App.post(() -> callback.success(file));
        } catch (Exception e) {
            Path.clear(file);
            if (callback != null) App.post(() -> callback.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void download(InputStream is, double length) throws IOException {
        if (length > maxBytes) throw new IOException("Download size limit exceeded");
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                totalBytes += readBytes;
                if (totalBytes > maxBytes) throw new IOException("Download size limit exceeded");
                os.write(buffer, 0, readBytes);
                if (length <= 0) continue;
                int progress = (int) (totalBytes / length * 100.0);
                if (callback != null) App.post(() -> callback.progress(progress));
            }
        }
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress);

        void error(String msg);

        void success(File file);
    }
}
