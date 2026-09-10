package com.imedia.box.live;

import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
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
    private static final Gson GSON = new Gson();
    private static final Backend PREFERS_BACKEND = new Backend() {
        @Override
        public String get(String key) {
            return Prefers.getString(key);
        }

        @Override
        public void put(String key, String value) {
            Prefers.put(key, value);
        }
    };

    private static Backend backend = PREFERS_BACKEND;

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
        backend.put(recentKey(), GSON.toJson(items));
    }

    public static void markFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        if (ref == null) return;
        Map<String, Long> failures = loadFailures();
        failures.put(itemKey(ref.group(), ref.channel().getName()), System.currentTimeMillis());
        backend.put(failureKey(), GSON.toJson(failures));
    }

    public static void clearFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        if (ref == null) return;
        Map<String, Long> failures = loadFailures();
        if (failures.remove(itemKey(ref.group(), ref.channel().getName())) != null) backend.put(failureKey(), GSON.toJson(failures));
    }

    public static boolean isFailed(Channel channel) {
        ChannelRef ref = resolve(channel);
        return ref != null && loadFailures().containsKey(itemKey(ref.group(), ref.channel().getName()));
    }

    static ChannelRef resolve(Channel channel) {
        if (channel == null) return null;
        List<Group> groups = sourceGroups();
        Group selected = channel.getGroup();
        if (isDirectSourceGroup(selected, groups)) return new ChannelRef(selected.getName(), channel);
        if (selected == null || (!selected.isKeep() && !selected.isRecent())) return null;
        List<ChannelRef> matches = new ArrayList<>();
        for (Group group : groups) {
            if (group.isKeep() || group.isRecent()) continue;
            for (Channel candidate : group.getChannel()) {
                if (sameSourceChannel(channel, candidate)) matches.add(new ChannelRef(group.getName(), candidate));
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    static void setBackendForTest(Backend value) {
        backend = value == null ? PREFERS_BACKEND : value;
    }

    private static boolean isDirectSourceGroup(Group selected, List<Group> groups) {
        if (selected == null || selected.isKeep() || selected.isRecent()) return false;
        return groups.stream().anyMatch(group -> group == selected);
    }

    private static boolean sameSourceChannel(Channel alias, Channel candidate) {
        boolean sameName = !alias.getName().isEmpty() && alias.getName().equals(candidate.getName());
        boolean sameNumber = !alias.getNumber().isEmpty() && alias.getNumber().equals(candidate.getNumber());
        return (sameName || sameNumber) && alias.getUrls().equals(candidate.getUrls());
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
            List<RecentEntry> result = GSON.fromJson(backend.get(recentKey()), type);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, Long> loadFailures() {
        try {
            Type type = new TypeToken<LinkedHashMap<String, Long>>() { }.getType();
            Map<String, Long> result = GSON.fromJson(backend.get(failureKey()), type);
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

    interface Backend {
        String get(String key);

        void put(String key, String value);
    }

    private record RecentEntry(@SerializedName("group") String group, @SerializedName("channel") String channel) {
    }
}
