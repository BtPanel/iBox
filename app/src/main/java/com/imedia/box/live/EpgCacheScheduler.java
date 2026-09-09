package com.imedia.box.live;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.imedia.box.api.LiveApi;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Live;
import com.imedia.box.receiver.EpgCacheReceiver;
import com.imedia.box.setting.LiveSetting;

import java.time.ZonedDateTime;
import java.util.ArrayList;

public final class EpgCacheScheduler {

    private static final String TAG = EpgCacheScheduler.class.getSimpleName();
    private static final int REQUEST_CODE = 48008;

    private EpgCacheScheduler() {
    }

    public static void schedule(Context context) {
        if (context == null) return;
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null) return;
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerAt(), pendingIntent(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to schedule EPG cache", e);
        }
    }

    public static void refresh() {
        LiveConfig config = LiveConfig.get();
        config.init().ensureLoaded();
        for (Live live : new ArrayList<>(config.getLives())) {
            try {
                LiveApi.parse(live);
                LiveApi.cacheEpg(live);
            } catch (Exception e) {
                Log.w(TAG, "EPG cache failed for " + live.getName(), e);
            }
        }
    }

    static long nextTriggerAt() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(LiveSetting.getEpgCacheHour()).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return next.toInstant().toEpochMilli();
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, EpgCacheReceiver.class).setAction(EpgCacheReceiver.ACTION);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
