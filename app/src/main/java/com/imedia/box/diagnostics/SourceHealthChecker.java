package com.imedia.box.diagnostics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.Decoder;
import com.imedia.box.api.SiteApi;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.bean.Result;
import com.imedia.box.server.Server;
import com.imedia.box.utils.Task;
import com.imedia.box.utils.UrlUtil;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

public final class SourceHealthChecker {

    private SourceHealthChecker() {
    }

    public static Future<?> start(Consumer<List<SourceHealthCheck>> callback) {
        return Task.submit(() -> {
            List<SourceHealthCheck> results = new ArrayList<>();
            run(results, callback, SourceHealthChecker::checkNetwork);
            run(results, callback, SourceHealthChecker::checkVodConfig);
            run(results, callback, SourceHealthChecker::checkVodSource);
            run(results, callback, SourceHealthChecker::checkLiveSource);
        });
    }

    private static void run(List<SourceHealthCheck> results, Consumer<List<SourceHealthCheck>> callback, Check check) {
        if (Thread.currentThread().isInterrupted()) return;
        results.add(check.run());
        List<SourceHealthCheck> snapshot = List.copyOf(results);
        App.post(() -> callback.accept(snapshot));
    }

    private static SourceHealthCheck checkNetwork() {
        long start = now();
        ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return item(SourceHealthCheck.Kind.NETWORK, SourceHealthCheck.Status.FAILED, R.string.source_health_network,
                    R.string.source_health_network_offline, "", start);
        }
        boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        int detail = validated ? R.string.source_health_network_online : R.string.source_health_network_unverified;
        SourceHealthCheck.Status status = validated ? SourceHealthCheck.Status.OK : SourceHealthCheck.Status.WARNING;
        return item(SourceHealthCheck.Kind.NETWORK, status, R.string.source_health_network, detail, "", start);
    }

    private static SourceHealthCheck checkVodConfig() {
        long start = now();
        String url = VodConfig.getUrl();
        String address = SourceHealthAddress.sanitize(url);
        if (TextUtils.isEmpty(url)) {
            return item(SourceHealthCheck.Kind.VOD_CONFIG, SourceHealthCheck.Status.FAILED, R.string.source_health_vod_config,
                    R.string.source_health_config_missing, address, start);
        }
        try {
            Server.get().start();
            String response = Decoder.getJson(UrlUtil.convert(url), "SourceHealth");
            JsonElement element = Json.parse(response);
            if (!element.isJsonObject()) throw new IllegalStateException();
            return item(SourceHealthCheck.Kind.VOD_CONFIG, SourceHealthCheck.Status.OK, R.string.source_health_vod_config,
                    R.string.source_health_config_valid, address, start);
        } catch (Throwable error) {
            return failure(SourceHealthCheck.Kind.VOD_CONFIG, R.string.source_health_vod_config, address, start, error);
        }
    }

    private static SourceHealthCheck checkVodSource() {
        long start = now();
        if (VodConfig.get().getSites().isEmpty()) {
            return item(SourceHealthCheck.Kind.VOD_SOURCE, SourceHealthCheck.Status.SKIPPED, R.string.source_health_vod_source,
                    R.string.source_health_source_missing, "", start);
        }
        try {
            Result result = SiteApi.homeContent(VodConfig.get().getHome());
            int typeCount = result.getTypes().size();
            int itemCount = result.getList().size();
            if (typeCount == 0 && itemCount == 0) {
                return item(SourceHealthCheck.Kind.VOD_SOURCE, SourceHealthCheck.Status.WARNING, R.string.source_health_vod_source,
                        R.string.source_health_source_empty, "", start);
            }
            String detail = App.get().getString(R.string.source_health_source_available, typeCount, itemCount);
            return item(SourceHealthCheck.Kind.VOD_SOURCE, SourceHealthCheck.Status.OK, R.string.source_health_vod_source,
                    detail, "", start);
        } catch (Throwable error) {
            return failure(SourceHealthCheck.Kind.VOD_SOURCE, R.string.source_health_vod_source, "", start, error);
        }
    }

    private static SourceHealthCheck checkLiveSource() {
        long start = now();
        String address = SourceHealthAddress.sanitize(LiveConfig.getUrl());
        if (!LiveConfig.hasUrl()) {
            return item(SourceHealthCheck.Kind.LIVE_SOURCE, SourceHealthCheck.Status.SKIPPED, R.string.source_health_live_source,
                    R.string.source_health_live_missing, address, start);
        }
        try {
            LiveConfig.get().ensureLoaded();
            int groups = LiveConfig.get().getHome().getGroups().size();
            long channels = LiveConfig.get().getHome().getGroups().stream()
                    .flatMap(group -> group.getChannel().stream())
                    .filter(channel -> !channel.getCurrent().isEmpty())
                    .count();
            if (groups == 0 || channels == 0) {
                return item(SourceHealthCheck.Kind.LIVE_SOURCE, SourceHealthCheck.Status.WARNING, R.string.source_health_live_source,
                        R.string.source_health_live_empty, address, start);
            }
            String detail = App.get().getString(R.string.source_health_live_available, groups, channels);
            return item(SourceHealthCheck.Kind.LIVE_SOURCE, SourceHealthCheck.Status.OK, R.string.source_health_live_source,
                    detail, address, start);
        } catch (Throwable error) {
            return failure(SourceHealthCheck.Kind.LIVE_SOURCE, R.string.source_health_live_source, address, start, error);
        }
    }

    private static SourceHealthCheck failure(SourceHealthCheck.Kind kind, int title, String address, long start, Throwable error) {
        return item(kind, SourceHealthCheck.Status.FAILED, title, errorText(error), address, start);
    }

    private static String errorText(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof InterruptedIOException) return text(R.string.source_health_error_timeout);
            if (cause instanceof UnknownHostException) return text(R.string.source_health_error_dns);
            if (cause instanceof SSLException) return text(R.string.source_health_error_ssl);
            if (cause instanceof ConnectException) return text(R.string.source_health_error_connect);
        }
        return text(R.string.source_health_error_unknown);
    }

    private static SourceHealthCheck item(SourceHealthCheck.Kind kind, SourceHealthCheck.Status status, int title,
                                          int detail, String address, long start) {
        return item(kind, status, title, text(detail), address, start);
    }

    private static SourceHealthCheck item(SourceHealthCheck.Kind kind, SourceHealthCheck.Status status, int title,
                                          String detail, String address, long start) {
        return new SourceHealthCheck(kind, status, title, detail, address, Math.max(0, now() - start));
    }

    private static String text(int id) {
        return App.get().getString(id);
    }

    private static long now() {
        return android.os.SystemClock.elapsedRealtime();
    }

    private interface Check {
        SourceHealthCheck run();
    }
}
