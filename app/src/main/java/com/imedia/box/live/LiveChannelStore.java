package com.imedia.box.live;

import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Group;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class LiveChannelStore {

    private static final int RECENT_LIMIT = 20;
    private static final long FAILURE_TTL = TimeUnit.HOURS.toMillis(24);

    private LiveChannelStore() {
    }

    public static Group buildRecent(List<Group> groups) {
        Group recent = Group.create(R.string.live_recent);
        for (RecentEntry entry : loadRecent()) {
            Channel channel = find(groups, entry.group(), entry.channel());
            if (channel != null) recent.add(channel);
        }
        return recent;
    }

    public static void recordRecent(Channel channel) {
        ChannelRef ref = resolve(channel);
        if (ref == null) return;
        List<RecentEntry> items = new ArrayList<>(loadRecent());
        items.removeIf(item -> item.group().equals(ref.group()) && item.channel().equals(ref.channel().getName()));
        items.add(0, new RecentEntry(ref.group(), ref.channel().getName()));
        if (items.size() > RECENT_LIMIT) items = new ArrayList<>(items.subList(0, RECENT_LIMIT));
        Prefers.put(recentKey(), App.gson().toJson(items));
    }

    public static void markFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        if (ref == null) return;
        Map<String, Long> failures = loadFailures();
        failures.put(itemKey(ref.group(), ref.channel().getName()), System.currentTimeMillis());
        Prefers.put(failureKey(), App.gson().toJson(failures));
    }

    public static void clearFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        if (ref == null) return;
        Map<String, Long> failures = loadFailures();
        if (failures.remove(itemKey(ref.group(), ref.channel().getName())) != null) Prefers.put(failureKey(), App.gson().toJson(failures));
    }

    public static boolean isFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        return ref != null && loadFailures().containsKey(itemKey(ref.group(), ref.channel().getName()));
    }

    static ChannelRef resolve(Channel channel) {
        if (channel == null) return null;
        for (Group group : sourceGroups()) {
            if (group.isKeep() || group.isRecent()) continue;
            Channel found = group.getChannel().stream().filter(channel::equals).findFirst().orElse(null);
            if (found != null) return new ChannelRef(group.getName(), found);
        }
        Group group = channel.getGroup();
        return group == null ? null : new ChannelRef(group.getName(), channel);
    }

    private static Channel find(List<Group> groups, String groupName, String channelName) {
        for (Group group : groups) {
            if (!group.getName().equals(groupName)) continue;
            return group.getChannel().stream().filter(item -> item.getName().equals(channelName)).findFirst().orElse(null);
        }
        return null;
    }

    private static List<Group> sourceGroups() {
        return LiveConfig.get().getHome().getGroups();
    }

    private static List<RecentEntry> loadRecent() {
        try {
            Type type = TypeToken.getParameterized(List.class, RecentEntry.class).getType();
            List<RecentEntry> result = App.gson().fromJson(Prefers.getString(recentKey()), type);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, Long> loadFailures() {
        try {
            Type type = new TypeToken<LinkedHashMap<String, Long>>() { }.getType();
            Map<String, Long> result = App.gson().fromJson(Prefers.getString(failureKey()), type);
            if (result == null) return new LinkedHashMap<>();
            long cutoff = System.currentTimeMillis() - FAILURE_TTL;
            result.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < cutoff);
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String recentKey() {
        return "live_recent_" + sourceKey();
    }

    private static String failureKey() {
        return "live_failed_" + sourceKey();
    }

    static String sourceKey() {
        return Integer.toHexString(Objects.hash(LiveConfig.get().getHome().getName(), LiveConfig.get().getConfig().getUrl()));
    }

    private static String itemKey(String group, String channel) {
        return group + "\n" + channel;
    }

    record ChannelRef(String group, Channel channel) {
    }

    private record RecentEntry(@SerializedName("group") String group, @SerializedName("channel") String channel) {
    }
}
