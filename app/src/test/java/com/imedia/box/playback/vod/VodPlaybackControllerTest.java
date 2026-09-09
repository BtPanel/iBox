package com.imedia.box.playback.vod;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class VodPlaybackControllerTest {

    @Test
    public void initialEmptyDetailUsesSearchFallback() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackHost host = host(calls, true);
        VodPlaybackController controller = new VodPlaybackController(host, new VodPlaybackState());

        controller.detailEmpty(false);

        assertEquals(1, calls.getOrDefault("openInitialDetailSearch", 0).intValue());
        assertEquals(0, calls.getOrDefault("renderEmptyDetail", 0).intValue());
    }

    @Test
    public void unhandledDetailErrorStillFinishesPlayback() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackHost host = host(calls, false);
        VodPlaybackController controller = new VodPlaybackController(host, new VodPlaybackState());

        controller.detailEmpty(true);

        assertEquals(1, calls.getOrDefault("openInitialDetailSearch", 0).intValue());
        assertEquals(1, calls.getOrDefault("finishVod", 0).intValue());
    }

    private static VodPlaybackHost host(Map<String, Integer> calls, boolean handlesSearch) {
        return (VodPlaybackHost) Proxy.newProxyInstance(
                VodPlaybackHost.class.getClassLoader(),
                new Class<?>[]{VodPlaybackHost.class},
                (proxy, method, args) -> {
                    calls.merge(method.getName(), 1, Integer::sum);
                    return switch (method.getName()) {
                        case "getVodKey" -> "site";
                        case "getVodId" -> "id";
                        case "getVodName" -> "name";
                        case "openInitialDetailSearch" -> handlesSearch;
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0F;
        if (type == Double.TYPE) return 0D;
        return 0;
    }
}
