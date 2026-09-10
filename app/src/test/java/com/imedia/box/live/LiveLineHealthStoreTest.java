package com.imedia.box.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Live;
import com.imedia.box.playback.live.LiveFailureKind;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class LiveLineHealthStoreTest {

    private static final long NOW = TimeUnit.DAYS.toMillis(100);

    @After
    public void resetLiveHome() {
        setLiveHome(null);
    }

    @Test
    public void persistedJsonContainsDigestButNotClearUrl() {
        MemoryBackend backend = new MemoryBackend();
        LiveLineHealthStore store = store(backend, NOW);
        Channel channel = channelWith("https://private/live.m3u8?token=secret$主线");

        store.recordFailure(channel, 0, LiveFailureKind.NETWORK);

        String json = backend.value();
        assertFalse(json.contains("private/live"));
        assertFalse(json.contains("secret"));
        assertTrue(json.contains(LiveLineKey.sha256("https://private/live.m3u8?token=secret")));
    }

    @Test
    public void keySeparatesSourceGroupChannelAndNormalizedUrl() {
        LiveLineKey first = LiveLineKey.create("source-a", "group-a", "channel-a", "https://a/live$One");

        assertFalse(first.equals(LiveLineKey.create("source-b", "group-a", "channel-a", "https://a/live$One")));
        assertFalse(first.equals(LiveLineKey.create("source-a", "group-b", "channel-a", "https://a/live$One")));
        assertFalse(first.equals(LiveLineKey.create("source-a", "group-a", "channel-b", "https://a/live$One")));
        assertFalse(first.equals(LiveLineKey.create("source-a", "group-a", "channel-a", "https://b/live$One")));
        assertEquals(first, LiveLineKey.create("source-a", "group-a", "channel-a", "https://a/live$Two"));
    }

    @Test
    public void canonicalSourceGroupSharesHealthWithRecentAlias() {
        TestGroup sourceGroup = new TestGroup("Sports");
        Channel source = channelWith("https://one/live$A");
        sourceGroup.getChannel().add(source);
        Live home = new Live("source", "");
        home.getGroups().add(sourceGroup);
        setLiveHome(home);

        Channel recentAlias = channelWith("https://one/live$A");
        recentAlias.setGroup(new TestGroup("Recent"));
        LiveLineHealthStore store = store(new MemoryBackend(), NOW);
        store.recordFailure(recentAlias, 0, LiveFailureKind.NETWORK);

        assertTrue(store.current().containsKey(LiveLineKey.create("test", "Sports", "News", "https://one/live$A")));
    }

    @Test
    public void ignoresUnknownVersionsAndMalformedEntries() {
        MemoryBackend backend = new MemoryBackend();
        backend.put("live_line_health_v2_test", "{\"version\":99,\"entries\":[]}");
        assertTrue(store(backend, NOW).current().isEmpty());

        backend.put("live_line_health_v2_test", "{\"version\":2,\"entries\":[null,{\"key\":null,\"health\":null},{\"key\":{\"sourceKey\":\"test\",\"group\":\"g\",\"channel\":\"c\",\"urlDigest\":\"d\"},\"health\":{\"successCount\":-1}},{\"key\":{\"sourceKey\":\"test\",\"group\":\"g\",\"channel\":\"c\",\"urlDigest\":\"d\"},\"health\":{\"successCount\":1,\"failureCount\":0,\"consecutiveFailures\":0,\"lastSuccessMs\":1,\"lastFailureMs\":0,\"averageStartupMs\":0,\"cooldownUntilMs\":0}}]}");

        assertEquals(1, store(backend, NOW).current().size());
    }

    @Test
    public void expiresOldEntriesAndPrunesToNewestFiveHundred() {
        MemoryBackend backend = new MemoryBackend();
        MutableClock clock = new MutableClock(NOW - TimeUnit.DAYS.toMillis(29));
        LiveLineHealthStore store = store(backend, clock);
        Channel channel = channelWith("https://line/");
        for (int i = 0; i < 499; i++) {
            channel.setUrls(List.of("https://line/" + i));
            store.recordSuccess(channel, 0, i);
        }
        clock.now = NOW - TimeUnit.DAYS.toMillis(31);
        channel.setUrls(List.of("https://expired"));
        store.recordSuccess(channel, 0, 0);
        assertTrue(store.current().containsKey(LiveLineKey.create("test", "News", "News", "https://expired")));
        clock.now = NOW;
        channel.setUrls(List.of("https://trigger"));
        store.recordSuccess(channel, 0, 0);

        assertEquals(500, store.current().size());
        assertFalse(store.current().containsKey(LiveLineKey.create("test", "News", "News", "https://expired")));
    }

    @Test
    public void rankExcludesAttemptedUsesNonCoolingScoresAndStableTies() {
        MemoryBackend backend = new MemoryBackend();
        LiveLineHealthStore store = store(backend, NOW);
        Channel channel = channelWith("https://zero", "https://one", "https://two");
        store.recordFailure(channel, 0, LiveFailureKind.NETWORK);
        store.recordSuccess(channel, 2, 1_000);

        assertEquals(List.of(2, 1), store.rank(channel, Set.of(0)));

        LiveLineHealthStore allCooling = store(new MemoryBackend(), NOW);
        allCooling.recordFailure(channel, 0, LiveFailureKind.NETWORK);
        allCooling.recordFailure(channel, 1, LiveFailureKind.NETWORK);
        allCooling.recordFailure(channel, 2, LiveFailureKind.NETWORK);
        assertEquals(List.of(0, 1, 2), allCooling.rank(channel, Set.of()));
    }

    private static LiveLineHealthStore store(MemoryBackend backend, long now) {
        return store(backend, () -> now);
    }

    private static LiveLineHealthStore store(MemoryBackend backend, LongSupplier clock) {
        return new LiveLineHealthStore("test", backend, clock);
    }

    private static Channel channelWith(String... urls) {
        TestChannel channel = new TestChannel("News", new TestGroup("News"));
        channel.setUrls(List.of(urls));
        return channel;
    }

    private static void setLiveHome(Live home) {
        try {
            Field field = LiveConfig.class.getDeclaredField("home");
            field.setAccessible(true);
            field.set(LiveConfig.get(), home);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestChannel extends Channel {
        private final String name;
        private Group group;
        private List<String> urls = List.of();

        TestChannel(String name, Group group) {
            this.name = name;
            this.group = group;
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
        public void setGroup(Group group) {
            this.group = group;
        }

        @Override
        public List<String> getUrls() {
            return urls;
        }

        @Override
        public void setUrls(List<String> urls) {
            this.urls = urls;
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

    private static final class MemoryBackend implements LiveLineHealthStore.Backend {
        private String value = "";

        @Override
        public String get(String key) {
            return value;
        }

        @Override
        public void put(String key, String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
