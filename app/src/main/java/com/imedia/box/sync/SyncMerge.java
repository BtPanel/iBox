package com.imedia.box.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class SyncMerge {

    private SyncMerge() {
    }

    public static <T> List<T> newest(List<T> local, List<T> remote, Function<T, String> key, ToLongFunction<T> modifiedAt) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T item : remote) result.put(key.apply(item), item);
        for (T item : local) {
            String itemKey = key.apply(item);
            T current = result.get(itemKey);
            if (current == null || modifiedAt.applyAsLong(item) >= modifiedAt.applyAsLong(current)) result.put(itemKey, item);
        }
        return new ArrayList<>(result.values());
    }
}
