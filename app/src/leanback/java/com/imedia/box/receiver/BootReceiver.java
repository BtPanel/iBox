package com.imedia.box.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;

import com.imedia.box.App;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.live.EpgReminder;
import com.imedia.box.live.EpgCacheScheduler;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.activity.HomeActivity;
import com.imedia.box.ui.activity.LiveActivity;

import java.util.concurrent.atomic.AtomicBoolean;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isBootAction(intent.getAction())) return;
        EpgReminder.restore(context);
        EpgCacheScheduler.schedule(context);
        if (Setting.isBootAutoStart()) startSelectedPage(context);
        registerCallback();
    }

    private void startSelectedPage(Context context) {
        int mode = Setting.getBootStartMode();
        Class<?> target = mode == Setting.BOOT_START_LIVE ? LiveActivity.class : HomeActivity.class;
        Intent launch = new Intent(context, target);
        if (target == HomeActivity.class) launch.putExtra(HomeActivity.EXTRA_BOOT_START_MODE, mode);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Some TV firmwares block background activity launches after boot.
        }
    }

    private boolean isBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    private void registerCallback() {
        try {
            ((ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(new Callback());
        } catch (RuntimeException ignored) {
            // The process may already have reached the platform callback limit.
        }
    }

    static class Callback extends ConnectivityManager.NetworkCallback {

        private final AtomicBoolean handled = new AtomicBoolean();

        @Override
        public void onAvailable(@NonNull Network network) {
            doJob();
        }

        @Override
        public void onLost(@NonNull Network network) {
        }

        private void doJob() {
            if (!handled.compareAndSet(false, true)) return;
            LiveConfig.get().init().load();
            try {
                ((ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(this);
            } catch (RuntimeException ignored) {
                // The callback may have been removed while the process was stopping.
            }
        }
    }
}
