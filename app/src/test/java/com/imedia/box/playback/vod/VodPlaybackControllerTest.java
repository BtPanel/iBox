package com.imedia.box.playback.vod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import android.text.TextUtils;

import com.imedia.box.bean.Episode;
import com.imedia.box.bean.Flag;
import com.imedia.box.bean.History;

import org.junit.After;
import org.junit.Before;
import org.mockito.MockedStatic;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VodPlaybackControllerTest {

    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        textUtils = mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.isEmpty(any()))
                .thenAnswer(call -> call.getArgument(0) == null || call.getArgument(0).toString().isEmpty());
    }

    @After
    public void tearDown() {
        textUtils.close();
    }

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

    @Test
    public void endingAndPlaybackEndOfferTheSameNextEpisodeOnlyOnce() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackState state = twoEpisodes();
        VodPlaybackController controller = new VodPlaybackController(host(calls, false, true), state);

        controller.onTimeChanged(1, 91_000, 100_000);
        controller.onTimeChanged(2, 92_000, 100_000);
        controller.playbackEnded();

        assertEquals(1, calls.getOrDefault("showAutoNext", 0).intValue());
        assertFalse(state.getFlag().getEpisodes().get(1).isSelected());
    }

    @Test
    public void cancellingAutoNextSuppressesBothEndSignalsForCurrentEpisode() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackState state = twoEpisodes();
        VodPlaybackController controller = new VodPlaybackController(host(calls, false, true), state);

        controller.onTimeChanged(1, 91_000, 100_000);
        invoke(controller, "cancelAutoNext");
        controller.onTimeChanged(2, 95_000, 100_000);
        controller.playbackEnded();

        assertEquals(1, calls.getOrDefault("showAutoNext", 0).intValue());
        assertFalse(state.getFlag().getEpisodes().get(1).isSelected());
    }

    @Test
    public void suspendingAutoNextAllowsPromptAgainWhenPlaybackReturns() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackState state = twoEpisodes();
        VodPlaybackController controller = new VodPlaybackController(host(calls, false, true), state);

        controller.onTimeChanged(1, 91_000, 100_000);
        controller.suspendAutoNext();
        controller.onTimeChanged(2, 92_000, 100_000);

        assertEquals(2, calls.getOrDefault("showAutoNext", 0).intValue());
        assertFalse(state.getFlag().getEpisodes().get(1).isSelected());
    }

    @Test
    public void disabledAutoNextLeavesEpisodeSelectionUntouched() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackState state = twoEpisodes();
        VodPlaybackController controller = new VodPlaybackController(host(calls, false, false), state);

        controller.onTimeChanged(1, 91_000, 100_000);
        controller.playbackEnded();

        assertEquals(0, calls.getOrDefault("showAutoNext", 0).intValue());
        assertFalse(state.getFlag().getEpisodes().get(1).isSelected());
    }

    @Test
    public void confirmingAutoNextSelectsTheOfferedEpisode() {
        Map<String, Integer> calls = new HashMap<>();
        VodPlaybackState state = twoEpisodes();
        VodPlaybackController controller = new VodPlaybackController(host(calls, false, true), state);

        controller.onTimeChanged(1, 91_000, 100_000);
        invoke(controller, "confirmAutoNext");

        assertTrue(state.getFlag().getEpisodes().get(1).isSelected());
        assertEquals(1, calls.getOrDefault("requestPlayer", 0).intValue());
    }

    private static VodPlaybackState twoEpisodes() {
        VodPlaybackState state = new VodPlaybackState();
        Flag flag = Flag.create("line");
        Episode first = Episode.create("01", "https://example.com/1");
        Episode second = Episode.create("02", "https://example.com/2");
        flag.getEpisodes().addAll(List.of(first, second));
        flag.toggle(true, first);
        state.setFlags(List.of(flag));
        History history = new History();
        history.setEnding(10_000);
        state.setHistory(history);
        return state;
    }

    private static void invoke(VodPlaybackController controller, String method) {
        try {
            VodPlaybackController.class.getMethod(method).invoke(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing auto-next action " + method, e);
        }
    }

    private static VodPlaybackHost host(Map<String, Integer> calls, boolean handlesSearch) {
        return host(calls, handlesSearch, false);
    }

    private static VodPlaybackHost host(Map<String, Integer> calls, boolean handlesSearch, boolean autoNext) {
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
                        case "isAutoNextEnabled" -> autoNext;
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
