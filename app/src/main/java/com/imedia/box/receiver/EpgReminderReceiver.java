package com.imedia.box.receiver;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.imedia.box.R;
import com.imedia.box.live.EpgReminder;
import com.imedia.box.ui.activity.LiveActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EpgReminderReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.imedia.box.action.EPG_REMINDER";
    public static final String EXTRA_ID = "id";
    private static final String CHANNEL = "epg_reminders";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        EpgReminder.Entry entry = EpgReminder.consume(intent.getStringExtra(EXTRA_ID));
        if (entry == null) return;
        show(context, entry);
    }

    private void show(Context context, EpgReminder.Entry entry) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.live_epg_reminder_channel))
                .build());
        Intent launch = new Intent(context, LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.live_epg_reminder_notification, entry.title()))
                .setContentText(context.getString(R.string.live_epg_reminder_detail, entry.channel(), TIME.format(Instant.ofEpochMilli(entry.startTime()))))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(entry.id().hashCode() & Integer.MAX_VALUE, notification);
        }
    }
}
