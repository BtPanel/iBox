package com.media.ibox.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Group;
import com.media.ibox.bean.Live;
import com.media.ibox.playback.live.LiveFailureKind;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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

    @Test public void mixedPersistedEntriesKeepValidRecordsAndRejectMissingOrInvalidFields() {
        MemoryBackend backend = new MemoryBackend();
        Gson gson = new Gson();
        LiveLineKey validKey = LiveLineKey.create("test", "News", "Valid", "https://valid");
        LiveLineHealth validHealth = new LiveLineHealth(20, 20, 20, NOW - 10, NOW, 500, NOW + 120_000);
        JsonObject valid = gson.toJsonTree(new LiveLineHealthStore.Entry(validKey, validHealth)).getAsJsonObject();
        JsonArray values = new JsonArray();
        values.add(valid);
        for (String field : List.of("successCount", "failureCount", "consecutiveFailures", "lastSuccessMs",
                "lastFailureMs", "averageStartupMs", "cooldownUntilMs")) {
            JsonObject broken = invalidEntry(valid, field);
            broken.getAsJsonObject("health").remove(field);
            values.add(broken);
        }
        for (String field : List.of("sourceKey", "group", "channel", "urlDigest")) {
            JsonObject broken = invalidEntry(valid, field);
            broken.getAsJsonObject("key").remove(field);
            values.add(broken);
        }
        for (String digest : List.of("", "digest", "g".repeat(64), "a".repeat(63))) {
            JsonObject broken = invalidEntry(valid, "digest-" + digest);
            broken.getAsJsonObject("key").addProperty("urlDigest", digest);
            values.add(broken);
        }
        for (String field : List.of("successCount", "failureCount", "consecutiveFailures")) {
            for (JsonPrimitive number : List.of(new JsonPrimitive(-1), new JsonPrimitive(21),
                    new JsonPrimitive(1.5), new JsonPrimitive("1"), new JsonPrimitive(Double.NaN))) {
                JsonObject broken = invalidEntry(valid, field + number);
                broken.getAsJsonObject("health").add(field, number);
                values.add(broken);
            }
        }
        for (String field : List.of("lastSuccessMs", "lastFailureMs", "averageStartupMs", "cooldownUntilMs")) {
            JsonObject broken = invalidEntry(valid, field + "negative");
            broken.getAsJsonObject("health").addProperty(field, -1);
            values.add(broken);
            broken = invalidEntry(valid, field + "overflow");
            broken.getAsJsonObject("health").add(field, new JsonPrimitive(new java.math.BigInteger("9223372036854775808")));
            values.add(broken);
        }
        for (String field : List.of("lastSuccessMs", "lastFailureMs")) {
            JsonObject broken = invalidEntry(valid, field + "future");
            broken.getAsJsonObject("health").addProperty(field, NOW + 1);
            values.add(broken);
        }
        JsonObject broken = invalidEntry(valid, "impossible cooldown");
        broken.getAsJsonObject("health").addProperty("cooldownUntilMs", NOW + TimeUnit.HOURS.toMillis(24) + 1);
        values.add(broken);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("version", 2);
        envelope.add("entries", values);
        backend.put("live_line_health_v2_test", envelope.toString());

        assertEquals(java.util.Map.of(validKey, validHealth), store(backend, NOW).current());
    }

    private static JsonObject invalidEntry(JsonObject valid, String name) {
        JsonObject copy = valid.deepCopy();
        copy.getAsJsonObject("key").addProperty("channel", "Invalid " + name);
        return copy;
    }

    @Test public void rankingAndReadsExpireOnlyAfterTheThirtyDayBoundaryWithoutAnUpdate() {
        MemoryBackend backend = new MemoryBackend();
        MutableClock clock = new MutableClock(NOW);
        LiveLineHealthStore store = store(backend, clock);
        Channel channel = channelWith("https://zero", "https://one");
        setLiveHome(home(channel.getGroup()));
        store.recordSuccess(channel, 1, 300);
        clock.now += TimeUnit.DAYS.toMillis(30);
        assertEquals(List.of(1, 0), store.rank(channel, Set.of()));
        assertEquals(1, store.current().size());
        clock.now++;
        assertEquals(List.of(0, 1), store.rank(channel, Set.of()));
        assertTrue(store.current().isEmpty());
        assertTrue(store(backend, clock).current().isEmpty());
    }

    @Test public void expiredLineStartsFreshBeforeEitherSuccessOrFailureUpdate() {
        for (boolean success : List.of(false, true)) {
            MutableClock clock = new MutableClock(NOW);
            LiveLineHealthStore store = store(new MemoryBackend(), clock);
            Channel channel = channelWith("https://one");
            setLiveHome(home(channel.getGroup()));
            store.recordSuccess(channel, 0, 20_000);
            store.recordFailure(channel, 0, LiveFailureKind.NETWORK);
            store.recordFailure(channel, 0, LiveFailureKind.NETWORK);
            clock.now += TimeUnit.DAYS.toMillis(30) + 1;
            if (success) store.recordSuccess(channel, 0, 500);
            else store.recordFailure(channel, 0, LiveFailureKind.NETWORK);
            LiveLineHealth health = store.current().values().iterator().next();
            assertEquals(success ? 1 : 0, health.successCount());
            assertEquals(success ? 0 : 1, health.failureCount());
            assertEquals(success ? 0 : 1, health.consecutiveFailures());
            assertEquals(success ? 500 : 0, health.averageStartupMs());
            assertEquals(success ? 0 : clock.now + 120_000, health.cooldownUntilMs());
        }
    }

    @After
    public void resetLiveHome() {
        setLiveHome(null);
    }

    @Test
    public void persistedJsonContainsDigestButNotClearUrl() {
        MemoryBackend backend = new MemoryBackend();
        LiveLineHealthStore store = store(backend, NOW);
        Channel channel = channelWith("https://private/live.m3u8?token=secret$主线");
        setLiveHome(home(channel.getGroup()));

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
        source.setGroup(sourceGroup);
        sourceGroup.getChannel().add(source);
        Live home = new Live("source", "");
        home.getGroups().add(sourceGroup);
        setLiveHome(home);

        Channel recentAlias = channelWith("https://one/live$A");
        recentAlias.setGroup(new TestGroup("Recent", false, true));
        LiveLineHealthStore store = store(new MemoryBackend(), NOW);
        store.recordFailure(recentAlias, 0, LiveFailureKind.NETWORK);

        assertTrue(store.current().containsKey(LiveLineKey.create("test", "Sports", "News", "https://one/live$A")));
    }

    @Test
    public void resolvesSameNamedRecentAliasByExactUrlsWithoutContaminatingOtherGroup() {
        TestGroup first = new TestGroup("First");
        TestGroup second = new TestGroup("Second");
        Channel firstSource = channel("News", "001", first, "https://first/live$A");
        Channel secondSource = channel("News", "001", second, "https://second/live$B");
        first.getChannel().add(firstSource);
        second.getChannel().add(secondSource);
        setLiveHome(home(first, second));

        Channel recentAlias = channel("News", "001", new TestGroup("Recent", false, true), "https://second/live$B");
        LiveLineHealthStore store = store(new MemoryBackend(), NOW);
        store.recordFailure(recentAlias, 0, LiveFailureKind.NETWORK);

        assertEquals("First", LiveChannelStore.resolve(firstSource).group());
        assertEquals("Second", LiveChannelStore.resolve(recentAlias).group());
        assertTrue(store.current().containsKey(LiveLineKey.create("test", "Second", "News", "https://second/live$B")));
        assertFalse(store.current().containsKey(LiveLineKey.create("test", "First", "News", "https://second/live$B")));
    }

    @Test
    public void resolvesFavoriteAliasByExactUrls() {
        TestGroup first = new TestGroup("First");
        TestGroup second = new TestGroup("Second");
        first.getChannel().add(channel("News", "001", first, "https://first/live$A"));
        second.getChannel().add(channel("News", "001", second, "https://second/live$B"));
        setLiveHome(home(first, second));

        Channel favoriteAlias = channel("News", "001", new TestGroup("Favorite", true, false), "https://second/live$B");

        assertEquals("Second", LiveChannelStore.resolve(favoriteAlias).group());
    }

    @Test
    public void leavesAmbiguousVirtualAliasUnresolved() {
        TestGroup first = new TestGroup("First");
        TestGroup second = new TestGroup("Second");
        first.getChannel().add(channel("News", "001", first, "https://same/live$A"));
        second.getChannel().add(channel("News", "001", second, "https://same/live$A"));
        setLiveHome(home(first, second));

        Channel recentAlias = channel("News", "001", new TestGroup("Recent", false, true), "https://same/live$A");

        assertEquals(null, LiveChannelStore.resolve(recentAlias));
    }

    @Test
    public void ambiguousAliasHasNoHealthAndKeepsUnattemptedSourceOrder() {
        TestGroup first = new TestGroup("First");
        TestGroup second = new TestGroup("Second");
        first.getChannel().add(channel("News", "001", first, "https://zero", "https://one", "https://two"));
        second.getChannel().add(channel("News", "001", second, "https://zero", "https://one", "https://two"));
        setLiveHome(home(first, second));
        Channel alias = channel("News", "001", new TestGroup("Recent", false, true),
                "https://zero", "https://one", "https://two");
        LiveLineHealthStore store = store(new MemoryBackend(), NOW);

        store.recordFailure(alias, 0, LiveFailureKind.NETWORK);
        store.recordSuccess(alias, 2, 500);

        assertTrue(store.current().isEmpty());
        assertEquals(List.of(0, 2), store.rank(alias, Set.of(1)));
    }

    @Test
    public void ignoresUnknownVersionsAndMalformedEntries() {
        MemoryBackend backend = new MemoryBackend();
        backend.put("live_line_health_v2_test", "{\"version\":99,\"entries\":[]}");
        assertTrue(store(backend, NOW).current().isEmpty());

        backend.put("live_line_health_v2_test", "{\"version\":2,\"entries\":[null,{\"key\":null,\"health\":null},{\"key\":{\"sourceKey\":\"test\",\"group\":\"g\",\"channel\":\"c\",\"urlDigest\":\"d\"},\"health\":{\"successCount\":-1}},{\"key\":{\"sourceKey\":\"test\",\"group\":\"g\",\"channel\":\"c\",\"urlDigest\":\"d\"},\"health\":{\"successCount\":1,\"failureCount\":0,\"consecutiveFailures\":0,\"lastSuccessMs\":1,\"lastFailureMs\":0,\"averageStartupMs\":0,\"cooldownUntilMs\":0}}]}");

        assertTrue(store(backend, NOW).current().isEmpty());
    }

    @Test
    public void expiresOldEntriesAndPrunesToNewestFiveHundred() {
        MemoryBackend backend = new MemoryBackend();
        MutableClock clock = new MutableClock(NOW - TimeUnit.DAYS.toMillis(29));
        LiveLineHealthStore store = store(backend, clock);
        Channel channel = channelWith("https://line/");
        setLiveHome(home(channel.getGroup()));
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
    public void prunesOldestOfFiveHundredAndOneNonExpiredEntries() {
        MutableClock clock = new MutableClock(NOW - TimeUnit.DAYS.toMillis(1));
        LiveLineHealthStore store = store(new MemoryBackend(), clock);
        Channel channel = channelWith("https://line/");
        setLiveHome(home(channel.getGroup()));
        for (int index = 0; index < 501; index++) {
            clock.now++;
            channel.setUrls(List.of("https://line/" + index));
            store.recordSuccess(channel, 0, 0);
        }

        assertEquals(500, store.current().size());
        assertFalse(store.current().containsKey(LiveLineKey.create("test", "News", "News", "https://line/0")));
        assertTrue(store.current().containsKey(LiveLineKey.create("test", "News", "News", "https://line/500")));
    }

    @Test
    public void rankExcludesAttemptedUsesNonCoolingScoresAndStableTies() {
        MemoryBackend backend = new MemoryBackend();
        LiveLineHealthStore store = store(backend, NOW);
        Channel channel = channelWith("https://zero", "https://one", "https://two");
        setLiveHome(home(channel.getGroup()));
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
        TestChannel channel = new TestChannel("News", "", new TestGroup("News"));
        channel.setUrls(List.of(urls));
        return channel;
    }

    private static Channel channel(String name, String number, Group group, String... urls) {
        TestChannel channel = new TestChannel(name, number, group);
        channel.setUrls(List.of(urls));
        return channel;
    }

    private static Live home(Group... groups) {
        Live home = new Live("source", "");
        home.getGroups().addAll(List.of(groups));
        return home;
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
        private final String number;
        private Group group;
        private List<String> urls = List.of();

        TestChannel(String name, String number, Group group) {
            this.name = name;
            this.number = number;
            this.group = group;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNumber() {
            return number;
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
        private final boolean keep;
        private final boolean recent;
        private final List<Channel> channels = new ArrayList<>();

        TestGroup(String name) {
            this(name, false, false);
        }

        TestGroup(String name, boolean keep, boolean recent) {
            super(name);
            this.name = name;
            this.keep = keep;
            this.recent = recent;
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
            return keep;
        }

        @Override
        public boolean isRecent() {
            return recent;
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
