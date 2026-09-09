package com.imedia.box.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.imedia.box.live.EpgCacheScheduler;
import com.imedia.box.utils.Task;

public class EpgCacheReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.imedia.box.action.EPG_CACHE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        EpgCacheScheduler.schedule(context);
        PendingResult result = goAsync();
        Task.executeSerial(() -> {
            try {
                EpgCacheScheduler.refresh();
            } finally {
                result.finish();
            }
        });
    }
}
