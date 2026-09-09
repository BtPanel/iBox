package com.imedia.box.sync;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SyncMergeTest {

    @Test
    public void newestKeepsLatestRecordFromEitherSide() {
        List<Item> local = List.of(new Item("same", 20, "local"), new Item("local", 5, "only local"));
        List<Item> remote = List.of(new Item("same", 10, "remote"), new Item("remote", 8, "only remote"));

        List<Item> result = SyncMerge.newest(local, remote, Item::key, Item::modifiedAt);

        assertEquals(3, result.size());
        assertEquals("local", find(result, "same").value());
        assertEquals("only local", find(result, "local").value());
        assertEquals("only remote", find(result, "remote").value());
    }

    @Test
    public void newestKeepsRemoteWhenItIsNewer() {
        List<Item> result = SyncMerge.newest(
                List.of(new Item("same", 10, "local")),
                List.of(new Item("same", 20, "remote")),
                Item::key,
                Item::modifiedAt);

        assertEquals("remote", result.get(0).value());
    }

    private static Item find(List<Item> items, String key) {
        return items.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }

    private record Item(String key, long modifiedAt, String value) {
    }
}
