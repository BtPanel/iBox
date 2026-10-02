package com.media.ibox.live;

import static org.junit.Assert.assertTrue;

import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.Group;
import com.media.ibox.bean.Live;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveChannelStoreTest {

    @After
    public void reset() {
        LiveChannelStore.setBackendForTest(null);
        setField(LiveConfig.class, LiveConfig.get(), "home", null);
        setField(LiveConfig.class.getSuperclass(), LiveConfig.get(), "config", null);
    }

    @Test
    public void recordRecentDoesNotClearExistingFailure() {
        MemoryBackend backend = new MemoryBackend();
        LiveChannelStore.setBackendForTest(backend);
        TestGroup group = new TestGroup("News");
        Channel channel = new TestChannel("News", group, List.of("https://news/live$A"));
        group.getChannel().add(channel);
        setField(LiveConfig.class, LiveConfig.get(), "home", new TestLive("source", List.of(group)));
        setField(LiveConfig.class.getSuperclass(), LiveConfig.get(), "config", new TestConfig("https://source"));

        LiveChannelStore.markFailed(channel);
        LiveChannelStore.recordRecent(channel);

        assertTrue(LiveChannelStore.isFailed(channel));
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestLive extends Live {
        private final String name;
        private final List<Group> groups;

        TestLive(String name, List<Group> groups) {
            this.name = name;
            this.groups = groups;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Group> getGroups() {
            return groups;
        }
    }

    private static final class TestConfig extends Config {
        private final String url;

        TestConfig(String url) {
            this.url = url;
        }

        @Override
        public String getUrl() {
            return url;
        }
    }

    private static final class TestChannel extends Channel {
        private final String name;
        private final Group group;
        private final List<String> urls;

        TestChannel(String name, Group group, List<String> urls) {
            this.name = name;
            this.group = group;
            this.urls = urls;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNumber() {
            return "";
        }

        @Override
        public Group getGroup() {
            return group;
        }

        @Override
        public List<String> getUrls() {
            return urls;
        }
    }

    private static final class TestGroup extends Group {
        private final String name;
        private final List<Channel> channels = new ArrayList<>();

        TestGroup(String name) {
            super(name);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Channel> getChannel() {
            return channels;
        }

        @Override
        public boolean isKeep() {
            return false;
        }

        @Override
        public boolean isRecent() {
            return false;
        }
    }

    private static final class MemoryBackend implements LiveChannelStore.Backend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.getOrDefault(key, "");
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
