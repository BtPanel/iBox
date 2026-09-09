package com.imedia.box.db;

import android.content.Context;
import android.os.Looper;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.imedia.box.App;
import com.imedia.box.bean.Backup;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Device;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Keep;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Site;
import com.imedia.box.bean.Track;
import com.imedia.box.db.dao.ConfigDao;
import com.imedia.box.db.dao.DeviceDao;
import com.imedia.box.db.dao.HistoryDao;
import com.imedia.box.db.dao.KeepDao;
import com.imedia.box.db.dao.LiveDao;
import com.imedia.box.db.dao.SiteDao;
import com.imedia.box.db.dao.TrackDao;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Formatters;
import com.imedia.box.utils.Task;
import com.github.catvod.utils.Path;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 35;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;
    private static final ExecutorService databaseExecutor = Task.limitedExecutor(1);
    private static final DatabaseDispatcher dispatcher = new DatabaseDispatcher(
            databaseExecutor, () -> Looper.myLooper() == Looper.getMainLooper());

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static <T> T call(Callable<T> action) {
        return dispatcher.call(action);
    }

    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }

    public static void runAsync(Runnable action) {
        databaseExecutor.execute(() -> run(action));
    }

    public static <T> void queryAsync(Callable<T> action, Consumer<T> consumer) {
        databaseExecutor.execute(() -> {
            T result = call(action);
            if (!Thread.currentThread().isInterrupted()) App.post(() -> consumer.accept(result));
        });
    }

    public static void backup() {
        backup(new com.imedia.box.impl.Callback());
    }

    public static void backup(com.imedia.box.impl.Callback callback) {
        Task.execute(() -> {
            File file = new File(Path.iBox(), "tv-" + LocalDate.now().format(Formatters.DATE) + ".bk");
            Backup backup = Backup.create();
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                Path.write(file, backup.toString().getBytes());
                FileUtil.gzipCompress(file);
                App.post(callback::success);
                cleanOld();
            }
        });
    }

    public static void restore(File file, com.imedia.box.impl.Callback callback) {
        Task.execute(() -> {
            File restore = Path.cache("restore");
            FileUtil.gzipDecompress(file, restore);
            Backup backup = Backup.objectFrom(Path.read(restore));
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                backup.restore();
                Path.clear(restore);
                App.post(callback::success);
            }
        });
    }

    private static void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.iBox().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) items.add(file);
        if (!items.isEmpty()) items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > 7) for (int i = 7; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.ALL)
                .build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
