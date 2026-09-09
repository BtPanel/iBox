package com.imedia.box.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.imedia.box.bean.Keep;
import com.imedia.box.setting.Setting;

import org.junit.Test;

import java.util.List;

public class KeepLibraryTest {

    @Test
    public void searchMatchesTitleAndSourceIgnoringCase() {
        List<Keep> items = List.of(
                item("1", "Alpha", "Cinema One", 10),
                item("2", "Beta", "Drama Two", 20));

        assertEquals("Alpha", KeepLibrary.filterAndSort(items, "CINEMA", Setting.KEEP_SORT_RECENT).get(0).getVodName());
        assertEquals("Beta", KeepLibrary.filterAndSort(items, "beta", Setting.KEEP_SORT_RECENT).get(0).getVodName());
        assertTrue(KeepLibrary.filterAndSort(items, "missing", Setting.KEEP_SORT_RECENT).isEmpty());
    }

    @Test
    public void sortModesUseRecentTitleAndSource() {
        Keep alpha = item("1", "Alpha", "Source Z", 10);
        Keep beta = item("2", "Beta", "Source A", 20);
        List<Keep> items = List.of(alpha, beta);

        assertEquals("Beta", KeepLibrary.filterAndSort(items, "", Setting.KEEP_SORT_RECENT).get(0).getVodName());
        assertEquals("Alpha", KeepLibrary.filterAndSort(items, "", Setting.KEEP_SORT_NAME).get(0).getVodName());
        assertEquals("Beta", KeepLibrary.filterAndSort(items, "", Setting.KEEP_SORT_SOURCE).get(0).getVodName());
    }

    @Test
    public void nullInputReturnsEmptyList() {
        assertTrue(KeepLibrary.filterAndSort(null, null, Setting.KEEP_SORT_RECENT).isEmpty());
    }

    private static Keep item(String key, String name, String source, long createdAt) {
        Keep item = new Keep();
        item.setKey(key);
        item.setVodName(name);
        item.setSiteName(source);
        item.setVodPic("");
        item.setCreateTime(createdAt);
        return item;
    }
}
