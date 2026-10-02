package com.media.ibox.live;

import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LiveChannelSearch {

    private static final int LIMIT = 100;

    private LiveChannelSearch() {
    }

    public static List<Match> find(List<Group> groups, String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        List<Match> result = new ArrayList<>();
        for (Group group : groups) {
            if (group.isHidden() || group.isKeep() || group.isRecent()) continue;
            for (Channel channel : group.getChannel()) {
                if (matches(channel, query)) result.add(new Match(group, channel));
                if (result.size() >= LIMIT) return result;
            }
        }
        return result;
    }

    private static boolean matches(Channel channel, String query) {
        return contains(channel.getName(), query)
                || contains(channel.getShow(), query)
                || contains(channel.getNumber(), query)
                || contains(channel.getTvgName(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public record Match(Group group, Channel channel) {
    }
}
