package com.media.ibox.ui.dialog;

import com.media.ibox.R;
import com.media.ibox.databinding.DialogLiveSettingsBinding;

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
                assertTrue("duplicate action " + action, seen.add(action));
            }
        }

        assertEquals(R.string.live_menu_playback, owner(groups, LiveSettingsDialog.PLAY_TOGGLE));
        assertEquals(R.string.live_menu_display, owner(groups, LiveSettingsDialog.DISPLAY_INFO));
        assertEquals(R.string.live_menu_display, owner(groups, LiveSettingsDialog.DISPLAY_SCALE));
        assertEquals(R.string.live_menu_channel, owner(groups, LiveSettingsDialog.CHANNEL_SEARCH));
        for (int action : Arrays.asList(LiveSettingsDialog.SOURCE_BASE,
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

    @Test
    public void previousChannelActionLivesInBothChannelMenus() throws Exception {
        for (boolean tv : new boolean[]{true, false}) {
            Map<Integer, List<Integer>> groups = groups(tv);
            assertEquals(R.string.live_menu_channel, owner(groups, LiveSettingsDialog.CHANNEL_PREVIOUS));
        }
    }

    @Test
    public void mobileActionsAppearOnceInTheirRelevantGroups() throws Exception {
        Map<Integer, List<Integer>> groups = groups(false);
        assertEquals(Arrays.asList(
                R.string.live_menu_line_category,
                R.string.live_menu_playback,
                R.string.live_menu_category_manage,
                R.string.live_menu_channel,
                R.string.live_menu_display,
                R.string.live_menu_subscription,
                R.string.live_menu_epg_subscription), new ArrayList<>(groups.keySet()));

        Set<Integer> seen = new HashSet<>();
        for (List<Integer> actions : groups.values()) {
            for (int action : actions) assertTrue("duplicate action " + action, seen.add(action));
        }
        assertEquals(Arrays.asList(LiveSettingsDialog.LINE_BASE, LiveSettingsDialog.LINE_BASE + 1),
                groups.get(R.string.live_menu_line_category));
        assertEquals(Arrays.asList(LiveSettingsDialog.GROUP_BASE),
                groups.get(R.string.live_menu_category_manage));
        assertEquals(R.string.live_menu_display, owner(groups, LiveSettingsDialog.DISPLAY_SCALE));
        assertEquals(R.string.live_menu_playback, owner(groups, LiveSettingsDialog.PLAY_RECONNECT));
        assertEquals(R.string.live_menu_channel, owner(groups, LiveSettingsDialog.LIST_LOGO));
        assertFalse(groups.get(R.string.live_menu_channel).contains(LiveSettingsDialog.LIST_EPG));
        assertEquals(R.string.live_menu_epg_subscription, owner(groups, LiveSettingsDialog.MANAGE_EPG_MENU));
        assertEquals(R.string.live_menu_epg_subscription, owner(groups, LiveSettingsDialog.MANAGE_EPG));
        for (int action : Arrays.asList(LiveSettingsDialog.PLAY_RECONNECT,
                LiveSettingsDialog.PREFERENCE_CHANGE, LiveSettingsDialog.LIST_LOGO,
                LiveSettingsDialog.LIST_NUMBER)) {
            assertTrue("mobile switch needs off/on details " + action, hasChildren(action, false));
        }
        assertFalse(hasChildren(LiveSettingsDialog.LINE_BASE, false));
        assertFalse(hasChildren(LiveSettingsDialog.GROUP_BASE, false));
    }

    @Test
    public void emptyMobileGroupsDoNotDuplicateChannelList() throws Exception {
        Map<Integer, List<Integer>> groups = groups(false, false);
        assertFalse(groups.containsKey(R.string.live_menu_category_manage));
        assertEquals(R.string.live_menu_channel, owner(groups, LiveSettingsDialog.CHANNEL_LIST));
    }

    @Test
    public void onlyCurrentSummaryUsesCompactSelectionMarker() throws Exception {
        Method marker = LiveSettingsDialog.class.getDeclaredMethod("isCurrentSummary", String.class, String.class);
        marker.setAccessible(true);
        assertTrue((boolean) marker.invoke(null, "当前", "当前"));
        assertFalse((boolean) marker.invoke(null, "开启", "当前"));
        assertFalse((boolean) marker.invoke(null, "", "当前"));
    }

    @Test
    public void tvDirectSelectionsStayInSecondLevelWhileSwitchesOpenThirdLevel() throws Exception {
        Map<Integer, List<Integer>> groups = tvGroups();
        assertEquals(Arrays.asList(LiveSettingsDialog.LINE_BASE, LiveSettingsDialog.LINE_BASE + 1),
                groups.get(R.string.live_menu_line_category));
        assertEquals(Arrays.asList(LiveSettingsDialog.GROUP_BASE), groups.get(R.string.live_menu_category_manage));
        assertTrue(groups.get(R.string.live_menu_subscription).contains(LiveSettingsDialog.SOURCE_BASE));
        assertFalse(groups.get(R.string.live_menu_subscription).contains(LiveSettingsDialog.MANAGE_REQUEST));
        for (int action : Arrays.asList(LiveSettingsDialog.DISPLAY_CLOCK,
                LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR, LiveSettingsDialog.DISPLAY_NETWORK,
                LiveSettingsDialog.DISPLAY_BOTTOM, LiveSettingsDialog.DISPLAY_BOTTOM_LOGO,
                LiveSettingsDialog.PREFERENCE_DECODE, LiveSettingsDialog.PREFERENCE_INVERT,
                LiveSettingsDialog.PREFERENCE_ACROSS, LiveSettingsDialog.PREFERENCE_CHANGE,
                LiveSettingsDialog.PREFERENCE_BOOT, LiveSettingsDialog.LIST_LOGO,
                LiveSettingsDialog.LIST_NUMBER)) {
            assertTrue("switch needs off/on details " + action, hasChildren(action));
        }
        assertTrue(hasChildren(LiveSettingsDialog.PLAY_ENGINE));
        assertTrue(hasChildren(LiveSettingsDialog.LIST_EPG));
    }

    private boolean hasChildren(int actionId) throws Exception {
        return hasChildren(actionId, true);
    }

    private boolean hasChildren(int actionId, boolean tv) throws Exception {
        for (Object category : categories(tv)) {
            Field actions = category.getClass().getDeclaredField("actions");
            actions.setAccessible(true);
            for (Object action : (List<?>) actions.get(category)) {
                Field id = action.getClass().getDeclaredField("id");
                Field children = action.getClass().getDeclaredField("children");
                id.setAccessible(true);
                children.setAccessible(true);
                if (id.getInt(action) == actionId) return !((List<?>) children.get(action)).isEmpty();
            }
        }
        throw new AssertionError("missing action " + actionId);
    }

    private int owner(Map<Integer, List<Integer>> groups, int action) {
        for (Map.Entry<Integer, List<Integer>> entry : groups.entrySet()) {
            if (entry.getValue().contains(action)) return entry.getKey();
        }
        throw new AssertionError("missing action " + action);
    }

    private Map<Integer, List<Integer>> tvGroups() throws Exception {
        return groups(true);
    }

    private Map<Integer, List<Integer>> groups(boolean tv) throws Exception {
        return groups(tv, true);
    }

    private Map<Integer, List<Integer>> groups(boolean tv, boolean hasGroups) throws Exception {
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (Object category : categories(tv, hasGroups)) {
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

    private List<?> categories(boolean tv) throws Exception {
        return categories(tv, true);
    }

    private List<?> categories(boolean tv, boolean hasGroups) throws Exception {
        Class<?> panelClass = Class.forName("com.media.ibox.ui.dialog.LiveSettingsDialog$Panel");
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
                return hasGroups ? Arrays.asList("News") : List.of();
            }

            @Override
            public List<String> getLiveSettingLines() {
                return Arrays.asList("Line 1", "Line 2");
            }

            @Override
            public List<String> getLiveSettingSources() {
                return Arrays.asList("Source 1", "Source 2");
            }

            @Override
            public List<String> getLiveSettingOptions(int action) {
                return Arrays.asList("Off", "On");
            }
        };
        Object panel = constructor.newInstance(null, listener, tv, null, null, null, null);
        Method categories = panelClass.getDeclaredMethod("categories");
        categories.setAccessible(true);

        return (List<?>) categories.invoke(panel);
    }
}
