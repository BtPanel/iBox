package com.imedia.box.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.imedia.box.setting.Setting;
import com.imedia.box.live.EpgReminder;
import com.imedia.box.live.EpgCacheScheduler;
import com.imedia.box.ui.activity.HomeActivity;
import com.imedia.box.ui.activity.HistoryActivity;
import com.imedia.box.ui.activity.LiveActivity;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) return;
        EpgReminder.restore(context);
        EpgCacheScheduler.schedule(context);
        int mode = Setting.getBootStartMode();
        if (mode == Setting.BOOT_START_OFF) return;
        Class<?> target = switch (mode) {
            case Setting.BOOT_START_LIVE -> LiveActivity.class;
            case Setting.BOOT_START_HISTORY -> HistoryActivity.class;
            default -> HomeActivity.class;
        };
        Intent launch = new Intent(context, target);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // Some OEM systems block background activity launches after boot.
        }
    }
}
