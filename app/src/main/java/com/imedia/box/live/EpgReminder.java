package com.imedia.box.live;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.imedia.box.App;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;
import com.imedia.box.receiver.EpgReminderReceiver;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EpgReminder {

    private static final String KEY = "epg_reminders";
    private static final long LEAD_TIME = TimeUnit.MINUTES.toMillis(5);
    private static List<Entry> entries;

    private EpgReminder() {
    }

    public static synchronized boolean toggle(Context context, Channel channel, EpgData data) {
        if (context == null || channel == null || data == null || !data.isFuture()) return false;
        String id = id(channel, data);
        Entry existing = find(id);
        if (existing != null) {
            entries().remove(existing);
            save();
            cancel(context, existing);
            return false;
        }
        Entry entry = new Entry(id, channel.getShow(), data.getTitle(), data.getStartTime());
        entries().add(entry);
        save();
        schedule(context, entry);
        return true;
    }

    public static synchronized boolean isScheduled(Channel channel, EpgData data) {
        return find(id(channel, data)) != null;
    }

    public static synchronized void restore(Context context) {
        prune();
        for (Entry entry : entries()) schedule(context, entry);
    }

    public static synchronized Entry consume(String id) {
        Entry entry = find(id);
        if (entry == null) return null;
        entries().remove(entry);
        save();
        return entry;
    }

    private static void schedule(Context context, Entry entry) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long triggerAt = Math.max(System.currentTimeMillis() + 1000, entry.startTime() - LEAD_TIME);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, entry));
    }

    private static void cancel(Context context, Entry entry) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendingIntent(context, entry));
    }

    private static PendingIntent pendingIntent(Context context, Entry entry) {
        Intent intent = new Intent(context, EpgReminderReceiver.class)
                .setAction(EpgReminderReceiver.ACTION)
                .putExtra(EpgReminderReceiver.EXTRA_ID, entry.id());
        return PendingIntent.getBroadcast(context, requestCode(entry.id()), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(String id) {
        return id.hashCode() & Integer.MAX_VALUE;
    }

    private static String id(Channel channel, EpgData data) {
        return channel.getName() + "\n" + data.getTitle() + "\n" + data.getStartTime();
    }

    private static Entry find(String id) {
        for (Entry entry : entries()) if (entry.id().equals(id)) return entry;
        return null;
    }

    private static List<Entry> entries() {
        if (entries != null) return entries;
        try {
            Type type = TypeToken.getParameterized(List.class, Entry.class).getType();
            List<Entry> stored = App.gson().fromJson(Prefers.getString(KEY), type);
            entries = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
        } catch (Exception e) {
            entries = new ArrayList<>();
        }
        prune();
        return entries;
    }

    private static void prune() {
        if (entries == null) return;
        if (entries.removeIf(entry -> entry == null || entry.id() == null || entry.channel() == null || entry.title() == null || entry.startTime() <= System.currentTimeMillis())) save();
    }

    private static void save() {
        Prefers.put(KEY, App.gson().toJson(entries == null ? List.of() : entries));
    }

    public record Entry(
            @SerializedName("id") String id,
            @SerializedName("channel") String channel,
            @SerializedName("title") String title,
            @SerializedName("startTime") long startTime) {
    }
}
