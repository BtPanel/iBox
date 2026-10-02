package com.media.ibox.live;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** In-memory persistence shared by controller integration tests. */
public final class LiveHealthTestFixture implements AutoCloseable {
    public final LiveLineHealthStore store;
    public final Map<String, String> values = new HashMap<>();

    public LiveHealthTestFixture(LongSupplier clock) {
        store = storeForSource("test", clock);
        LiveChannelStore.setBackendForTest(new LiveChannelStore.Backend() {
            public String get(String key) { return values.getOrDefault(key, ""); }
            public void put(String key, String value) { values.put(key, value); }
        });
    }

    public LiveLineHealthStore storeForSource(String source, LongSupplier clock) {
        return new LiveLineHealthStore(source, new LiveLineHealthStore.Backend() {
            public String get(String key) { return values.getOrDefault(key, ""); }
            public void put(String key, String value) { values.put(key, value); }
        }, clock);
    }

    @Override public void close() { LiveChannelStore.setBackendForTest(null); }
}
