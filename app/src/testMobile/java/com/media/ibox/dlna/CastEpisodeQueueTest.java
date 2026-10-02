package com.media.ibox.dlna;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class CastEpisodeQueueTest {

    private final List<CastEpisodeQueue.Item> episodes = List.of(
            new CastEpisodeQueue.Item("1", "url1"),
            new CastEpisodeQueue.Item("2", "url2"),
            new CastEpisodeQueue.Item("3", "url3"));

    @Test
    public void advancesOnlyAfterSuccessfulCast() {
        CastEpisodeQueue queue = new CastEpisodeQueue("site", "line", episodes, 1, false);
        assertEquals("url3", queue.next().item().url());
        assertEquals("url2", queue.current().url());
        queue.select(queue.next());
        assertEquals("url3", queue.current().url());
        assertNull(queue.next());
        assertEquals("url2", queue.previous().item().url());
    }

    @Test
    public void reversePlayChangesDirectionWithoutChangingListOrder() {
        CastEpisodeQueue queue = new CastEpisodeQueue("site", "line", episodes, 1, true);
        assertEquals("url1", queue.next().item().url());
        assertEquals("url3", queue.previous().item().url());
        queue.select(queue.next());
        assertNull(queue.next());
        assertTrue(queue.hasPrevious());
        assertFalse(queue.hasNext());
    }
}
