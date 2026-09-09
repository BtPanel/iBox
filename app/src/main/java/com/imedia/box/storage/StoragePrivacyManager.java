package com.imedia.box.storage;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.imedia.box.App;
import com.imedia.box.bean.History;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.impl.Callback;
import com.imedia.box.offline.OfflineDownloadManager;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Task;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class StoragePrivacyManager {

    private static final String KEY_AUTO = "storage_auto_clean";
    private static final String KEY_LAST_CLEAN = "storage_last_clean";
    private static final long AUTO_CLEAN_INTERVAL = TimeUnit.DAYS.toMillis(7);
    private static final int HISTORY_IMPORT_LIMIT = 10 * 1024 * 1024;

    private StoragePrivacyManager() {
    }

    public static void load(StatsCallback callback) {
        Task.execute(() -> {
            long cache = FileUtil.getDirectorySize(Path.cache());
            long downloads = OfflineDownloadManager.getDownloadedBytes();
            int histories = AppDatabase.get().getHistoryDao().findAll().size();
            App.post(() -> callback.onResult(new Stats(cache, downloads, histories)));
        });
    }

    public static boolean isAutoCleanEnabled() {
        return Prefers.getBoolean(KEY_AUTO);
    }

    public static void setAutoCleanEnabled(boolean enabled) {
        Prefers.put(KEY_AUTO, enabled);
        if (enabled && Prefers.getLong(KEY_LAST_CLEAN) == 0) Prefers.put(KEY_LAST_CLEAN, System.currentTimeMillis());
    }

    public static void runAutoCleanIfDue() {
        if (!isAutoCleanEnabled()) return;
        long last = Prefers.getLong(KEY_LAST_CLEAN);
        if (System.currentTimeMillis() - last < AUTO_CLEAN_INTERVAL) return;
        clearCache(new Callback());
    }

    public static void clearCache(Callback callback) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                Prefers.put(KEY_LAST_CLEAN, System.currentTimeMillis());
                callback.success();
            }
        });
    }

    public static void clearHistory(Callback callback) {
        Task.execute(() -> {
            AppDatabase.get().getHistoryDao().delete();
            App.post(callback::success);
        });
    }

    public static void exportHistory(Context context, Uri uri, Callback callback) {
        Task.execute(() -> {
            try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("Unable to open destination");
                List<History> histories = AppDatabase.get().getHistoryDao().findAll();
                output.write(App.gson().toJson(histories).getBytes(StandardCharsets.UTF_8));
                App.post(callback::success);
            } catch (Exception e) {
                App.post(callback::error);
            }
        });
    }

    public static void importHistory(Context context, Uri uri, Callback callback) {
        Task.execute(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Unable to open source");
                String json = readLimited(input);
                List<History> histories = History.arrayFrom(json).stream()
                        .filter(item -> item != null && !TextUtils.isEmpty(item.getKey()))
                        .toList();
                if (histories.isEmpty() && !"[]".equals(json.trim())) throw new IllegalArgumentException("Invalid history file");
                AppDatabase.get().getHistoryDao().insertOrUpdate(histories);
                App.post(callback::success);
            } catch (Exception e) {
                App.post(callback::error);
            }
        });
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > HISTORY_IMPORT_LIMIT) throw new IllegalArgumentException("History file is too large");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public record Stats(long cacheBytes, long downloadBytes, int historyCount) {
    }

    public interface StatsCallback {
        void onResult(Stats stats);
    }
}
