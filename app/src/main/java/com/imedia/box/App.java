package com.imedia.box;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.imedia.box.utils.Notify;
import com.imedia.box.live.EpgCacheScheduler;
import com.imedia.box.setting.Setting;
import com.imedia.box.storage.StoragePrivacyManager;
import com.imedia.box.utils.Task;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.net.GitHubUrl;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static final long DEFERRED_STARTUP_DELAY_MS = 750;
    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;
    private final AtomicBoolean deferredStartupScheduled = new AtomicBoolean();

    private WeakReference<Activity> activity = new WeakReference<>(null);
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity.get();
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        GitHubUrl.setAccelerationEnabled(Setting.isGitHubAcceleration());
        Setting.applyThemeMode();
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        scheduleDeferredStartup();
    }

    private void scheduleDeferredStartup() {
        if (!deferredStartupScheduled.compareAndSet(false, true)) return;
        post(() -> Task.execute(() -> {
            Path.iBox();
            StoragePrivacyManager.runAutoCleanIfDue();
            EpgCacheScheduler.schedule(this);
        }), DEFERRED_STARTUP_DELAY_MS);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity.clear();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}
