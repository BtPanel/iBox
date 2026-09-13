package com.imedia.box.ui.dialog;

import com.imedia.box.R;
import com.imedia.box.databinding.DialogLiveSettingsBinding;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveSettingsDialogTest {

    @Test
    public void tvActionsAppearOnceInTheirRelevantGroups() throws Exception {
        Map<Integer, List<Integer>> groups = tvGroups();
        assertEquals(Arrays.asList(
                R.string.live_menu_line_category,
                R.string.live_menu_playback,
                R.string.live_menu_category_manage,
                R.string.live_menu_subscription,
                R.string.live_menu_display,
                R.string.live_menu_preference,
                R.string.live_menu_channel), new ArrayList<>(groups.keySet()));

        Set<Integer> seen = new HashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : groups.entrySet()) {
            for (int action : entry.getValue()) {
                if (entry.getKey() == R.string.live_menu_category_manage && action == LiveSettingsDialog.CHANNEL_LIST) continue;
                assertTrue("duplicate action " + action, seen.add(action));
            }
        }

        assertEquals(R.string.live_menu_playback, owner(groups, LiveSettingsDialog.PLAY_TOGGLE));
        assertEquals(R.string.live_menu_display, owner(groups, LiveSettingsDialog.DISPLAY_INFO));
        assertEquals(R.string.live_menu_display, owner(groups, LiveSettingsDialog.DISPLAY_SCALE));
        assertEquals(R.string.live_menu_channel, owner(groups, LiveSettingsDialog.CHANNEL_SEARCH));
        for (int action : Arrays.asList(LiveSettingsDialog.MANAGE_SELECT,
                LiveSettingsDialog.MANAGE_SOURCE, LiveSettingsDialog.MANAGE_ADD,
                LiveSettingsDialog.MANAGE_FILE, LiveSettingsDialog.MANAGE_REFRESH)) {
            assertEquals(R.string.live_menu_subscription, owner(groups, action));
        }
        assertEquals(R.string.live_menu_preference, owner(groups, LiveSettingsDialog.MANAGE_APP_SETTINGS));
    }

    @Test
    public void tvAddSubscriptionDoesNotAlsoOfferTheSamePushDialog() throws Exception {
        Map<Integer, List<Integer>> groups = tvGroups();
        assertTrue(groups.get(R.string.live_menu_subscription).contains(LiveSettingsDialog.MANAGE_ADD));
        assertFalse(groups.get(R.string.live_menu_subscription).contains(LiveSettingsDialog.MANAGE_PUSH));
    }

    @Test
    public void tvEpgDoesNotAlsoAppearAsCatchup() throws Exception {
        Map<Integer, List<Integer>> groups = tvGroups();
        assertTrue(groups.get(R.string.live_menu_channel).contains(LiveSettingsDialog.CHANNEL_EPG));
        assertFalse(groups.get(R.string.live_menu_playback).contains(LiveSettingsDialog.PLAY_CATCHUP));
    }

    private int owner(Map<Integer, List<Integer>> groups, int action) {
        for (Map.Entry<Integer, List<Integer>> entry : groups.entrySet()) {
            if (entry.getValue().contains(action)) return entry.getKey();
        }
        throw new AssertionError("missing action " + action);
    }

    private Map<Integer, List<Integer>> tvGroups() throws Exception {
        Class<?> panelClass = Class.forName("com.imedia.box.ui.dialog.LiveSettingsDialog$Panel");
        Constructor<?> constructor = panelClass.getDeclaredConstructor(DialogLiveSettingsBinding.class,
                LiveSettingsDialog.Listener.class, boolean.class, Runnable.class, Runnable.class,
                Runnable.class, Runnable.class);
        constructor.setAccessible(true);
        LiveSettingsDialog.Listener listener = new LiveSettingsDialog.Listener() {
            @Override
            public void onLiveSettingAction(int action) {
            }

            @Override
            public String getLiveSettingSummary(int action) {
                return "";
            }

            @Override
            public List<String> getLiveSettingGroups() {
                return Arrays.asList("News");
            }
        };
        Object panel = constructor.newInstance(null, listener, true, null, null, null, null);
        Method categories = panelClass.getDeclaredMethod("categories");
        categories.setAccessible(true);

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (Object category : (List<?>) categories.invoke(panel)) {
            Field title = category.getClass().getDeclaredField("title");
            Field actions = category.getClass().getDeclaredField("actions");
            title.setAccessible(true);
            actions.setAccessible(true);
            List<Integer> ids = new ArrayList<>();
            for (Object action : (List<?>) actions.get(category)) {
                Field id = action.getClass().getDeclaredField("id");
                id.setAccessible(true);
                ids.add(id.getInt(action));
            }
            groups.put(title.getInt(category), ids);
        }
        return groups;
    }
}
