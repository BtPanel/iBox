package com.imedia.box.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.imedia.box.R;
import com.imedia.box.utils.Notify;

public class ShortcutReceiver extends BroadcastReceiver {

    public static final String ACTION = ShortcutReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Notify.show(R.string.shortcut);
    }
}