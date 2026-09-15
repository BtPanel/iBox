package com.imedia.box.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class AsyncSnapshotSaverTest {

    @Test
    public void queuesAnIndependentSnapshotWithoutSavingInline() {
        List<Runnable> queue = new ArrayList<>();
        AtomicReference<Value> saved = new AtomicReference<>();
        AsyncSnapshotSaver<Value> saver = new AsyncSnapshotSaver<>(Value::copy, saved::set, queue::add);
        Value source = new Value("episode-1");

        saver.save(source);
        source.name = "episode-2";

        assertTrue(saved.get() == null);
        queue.get(0).run();
        assertEquals("episode-1", saved.get().name);
    }

    private static final class Value {
        private String name;

        private Value(String name) {
            this.name = name;
        }

        private Value copy() {
            return new Value(name);
        }
    }
}
