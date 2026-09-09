package com.imedia.box.offline;

import android.net.Uri;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.imedia.box.App;
import com.imedia.box.utils.ImgUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineArtworkManager {

    private static final String DIRECTORY = "artwork";
    private static final long MAX_ARTWORK_BYTES = 20L * 1024L * 1024L;

    private OfflineArtworkManager() {
    }

    public static String cache(String siteKey, String vodId, String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl)) return "";
        if (isLocal(sourceUrl)) return sourceUrl;
        File target = artworkFile(siteKey, vodId);
        if (isValid(target)) return Uri.fromFile(target).toString();

        FutureTarget<File> request = null;
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        try {
            request = Glide.with(App.get()).downloadOnly().load(ImgUtil.getUrl(sourceUrl)).submit();
            File source = request.get();
            if (!isValid(source) || source.length() > MAX_ARTWORK_BYTES) return sourceUrl;
            copy(source, temporary);
            if (target.exists() && !target.delete()) return sourceUrl;
            if (!temporary.renameTo(target)) return sourceUrl;
            return Uri.fromFile(target).toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return sourceUrl;
        } catch (Exception ignored) {
            return sourceUrl;
        } finally {
            if (temporary.exists()) temporary.delete();
            if (request != null) Glide.with(App.get()).clear(request);
        }
    }

    public static void deleteIfUnused(OfflineDownloadMetadata metadata) {
        if (metadata == null) return;
        boolean inUse = OfflineDownloadManager.getDownloads().stream()
                .map(OfflineDownloadItem::from)
                .map(OfflineDownloadItem::metadata)
                .anyMatch(item -> item.getSiteKey().equals(metadata.getSiteKey()) && item.getVodId().equals(metadata.getVodId()));
        if (!inUse) artworkFile(metadata.getSiteKey(), metadata.getVodId()).delete();
    }

    static String fileName(String siteKey, String vodId) {
        String identity = value(siteKey) + '\u001f' + value(vodId);
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)) + ".image";
    }

    private static File artworkFile(String siteKey, String vodId) {
        File directory = new File(OfflineDownloadManager.getDownloadDirectory(), DIRECTORY);
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, fileName(siteKey, vodId));
    }

    private static boolean isLocal(String value) {
        String scheme = Uri.parse(value).getScheme();
        return "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme);
    }

    private static boolean isValid(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    private static void copy(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
