package com.imedia.box.offline;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.Downloader;
import androidx.media3.exoplayer.offline.DownloaderFactory;
import androidx.media3.exoplayer.scheduler.Requirements;

import com.github.catvod.net.OkHttp;
import com.imedia.box.App;
import com.imedia.box.bean.Result;
import com.imedia.box.setting.DownloadSetting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@OptIn(markerClass = UnstableApi.class)
public final class OfflineDownloadManager {

    public static final int STOP_REASON_PAUSED = 1;
    public static final String CHANNEL_ID = "offline_downloads";

    private static final String DOWNLOAD_DIRECTORY = "offline";
    private static final String CONTENT_DIRECTORY = "content";

    private static DatabaseProvider databaseProvider;
    private static Cache cache;
    private static DownloadManager manager;
    private static ThreadPoolExecutor segmentExecutor;

    private OfflineDownloadManager() {
    }

    public static synchronized DownloadManager get() {
        if (manager != null) return manager;
        Context context = App.get();
        DownloadPolicy policy = DownloadSetting.get();
        Executor downloadExecutor = getSegmentExecutor(policy.segmentThreads());
        DownloaderFactory downloaderFactory = new MetadataDownloaderFactory(context, getCache(), downloadExecutor);
        manager = new DownloadManager(context, new DefaultDownloadIndex(getDatabaseProvider()), downloaderFactory);
        manager.addListener(new DownloadManager.Listener() {
            @Override
            public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
                OfflineArtworkManager.deleteIfUnused(OfflineDownloadMetadata.from(download.request.data));
            }
        });
        applyPolicy(manager, policy);
        return manager;
    }

    public static void applyPolicy(DownloadPolicy policy) {
        applyPolicy(get(), policy);
    }

    private static void applyPolicy(DownloadManager downloadManager, DownloadPolicy policy) {
        int networkRequirement = policy.wifiOnly() ? Requirements.NETWORK_UNMETERED : Requirements.NETWORK;
        downloadManager.setRequirements(new Requirements(networkRequirement));
        downloadManager.setMaxParallelDownloads(policy.parallelDownloads());
        downloadManager.setMinRetryCount(policy.retryCount());
        resizeSegmentExecutor(policy.segmentThreads());
    }

    private static synchronized Executor getSegmentExecutor(int threads) {
        if (segmentExecutor == null) {
            segmentExecutor = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            segmentExecutor.allowCoreThreadTimeOut(true);
        } else {
            resizeSegmentExecutor(threads);
        }
        return segmentExecutor;
    }

    private static synchronized void resizeSegmentExecutor(int threads) {
        if (segmentExecutor == null) return;
        if (threads < segmentExecutor.getCorePoolSize()) {
            segmentExecutor.setCorePoolSize(threads);
            segmentExecutor.setMaximumPoolSize(threads);
        } else {
            segmentExecutor.setMaximumPoolSize(threads);
            segmentExecutor.setCorePoolSize(threads);
        }
    }

    public static DownloadRequest createRequest(Result result, OfflineDownloadMetadata metadata) throws IOException {
        ResolvedSource source = resolveSource(result);
        String identity = metadata.getSiteKey() + '\u001f' + metadata.getVodId() + '\u001f' + metadata.getFlag() + '\u001f' + metadata.getEpisodeId();
        String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
        return new DownloadRequest.Builder(id, source.uri())
                .setMimeType(source.mimeType())
                .setData(metadata.toBytes())
                .build();
    }

    public static boolean supports(Result result) {
        if (result == null || result.needParse() || result.isUseParse() || result.getDrm() != null) return false;
        String url = result.getRealUrl();
        if (TextUtils.isEmpty(url)) return false;
        String scheme = Uri.parse(url).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public static List<Download> getDownloads() {
        List<Download> items = new ArrayList<>();
        try (DownloadCursor cursor = get().getDownloadIndex().getDownloads()) {
            while (cursor.moveToNext()) items.add(cursor.getDownload());
        } catch (IOException ignored) {
        }
        items.sort(Comparator.comparingLong((Download item) -> item.updateTimeMs).reversed());
        return items;
    }

    public static Optional<Download> findCompleted(String id) {
        if (TextUtils.isEmpty(id)) return Optional.empty();
        return getDownloads().stream()
                .filter(item -> item.state == Download.STATE_COMPLETED)
                .filter(item -> id.equals(item.request.id))
                .findFirst();
    }

    public static List<OfflineDownloadItem> getCompletedFor(OfflineDownloadMetadata metadata) {
        if (metadata == null) return List.of();
        return getDownloads().stream()
                .filter(item -> item.state == Download.STATE_COMPLETED)
                .map(OfflineDownloadItem::from)
                .filter(item -> metadata.getSiteKey().equals(item.metadata().getSiteKey()))
                .filter(item -> metadata.getVodId().equals(item.metadata().getVodId()))
                .toList();
    }

    public static long getDownloadedBytes() {
        long total = 0;
        for (Download item : getDownloads()) total += Math.max(0, item.getBytesDownloaded());
        return total;
    }

    public static boolean hasActiveDownloads() {
        return getDownloads().stream()
                .anyMatch(item -> OfflineDownloadItem.from(item).isActive());
    }

    public static File getDownloadDirectory() {
        File root = App.get().getExternalFilesDir(null);
        if (root == null) root = App.get().getFilesDir();
        File directory = new File(root, DOWNLOAD_DIRECTORY);
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    public static Cache getCacheForPlayback() {
        return getCache();
    }

    private static synchronized Cache getCache() {
        if (cache != null) return cache;
        File content = new File(getDownloadDirectory(), CONTENT_DIRECTORY);
        if (!content.exists()) content.mkdirs();
        return cache = new SimpleCache(content, new NoOpCacheEvictor(), getDatabaseProvider());
    }

    private static synchronized DatabaseProvider getDatabaseProvider() {
        if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(App.get());
        return databaseProvider;
    }

    @Nullable
    private static String normalizeMimeType(@Nullable String format, Uri uri) {
        if (!TextUtils.isEmpty(format)) {
            String lower = format.toLowerCase(Locale.ROOT);
            if (lower.contains("m3u8") || lower.contains("hls")) return MimeTypes.APPLICATION_M3U8;
            if (lower.contains("mpd") || lower.contains("dash")) return MimeTypes.APPLICATION_MPD;
            if (format.contains("/")) return format;
        }
        return switch (androidx.media3.common.util.Util.inferContentType(uri)) {
            case C.CONTENT_TYPE_HLS -> MimeTypes.APPLICATION_M3U8;
            case C.CONTENT_TYPE_DASH -> MimeTypes.APPLICATION_MPD;
            default -> null;
        };
    }

    private static ResolvedSource resolveSource(Result result) throws IOException {
        Uri uri = Uri.parse(result.getRealUrl());
        String normalized = normalizeMimeType(result.getFormat(), uri);
        if (!isLocalProxy(uri)) return new ResolvedSource(uri, normalized);
        return resolveProxySource(result, normalized);
    }

    private static ResolvedSource resolveProxySource(Result result, @Nullable String fallbackMimeType) throws IOException {
        Request.Builder builder = new Request.Builder().url(result.getRealUrl());
        for (Map.Entry<String, String> header : result.getHeader().entrySet()) {
            if (header.getKey() != null && header.getValue() != null) builder.header(header.getKey(), header.getValue());
        }
        builder.header("Range", "bytes=0-8191");
        try (Response response = OkHttp.player().newCall(builder.build()).execute(); ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) throw new IOException("Unable to resolve local proxy source: HTTP " + response.code());
            Uri resolvedUri = Uri.parse(response.request().url().toString());
            if (isLocalProxy(resolvedUri)) throw new IOException("This source requires a local proxy and cannot be downloaded for offline playback");
            String contentType = body.contentType() == null ? response.header("Content-Type") : body.contentType().toString();
            byte[] bytes = new byte[8192];
            int length = body.byteStream().read(bytes);
            String preview = length <= 0 ? "" : new String(bytes, 0, length, StandardCharsets.UTF_8);
            String detected = OfflineMediaTypeResolver.adaptiveMimeType(result.getFormat(), contentType, preview);
            return new ResolvedSource(resolvedUri, detected == null ? fallbackMimeType : detected);
        }
    }

    private static boolean isLocalProxy(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (!("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host))) return false;
        String path = uri.getPath();
        return path != null && path.startsWith("/proxy");
    }

    private record ResolvedSource(Uri uri, @Nullable String mimeType) {
    }

    private static final class MetadataDownloaderFactory implements DownloaderFactory {

        private final Context context;
        private final Cache cache;
        private final Executor executor;

        private MetadataDownloaderFactory(Context context, Cache cache, Executor executor) {
            this.context = context.getApplicationContext();
            this.cache = cache;
            this.executor = executor;
        }

        @Override
        public Downloader createDownloader(DownloadRequest request) {
            OfflineDownloadMetadata metadata = OfflineDownloadMetadata.from(request.data);
            OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(OkHttp.player());
            Map<String, String> headers = metadata.getHeaders();
            if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);
            DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(context, httpFactory);
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            return new DefaultDownloaderFactory(cacheFactory, executor).createDownloader(request);
        }
    }
}
