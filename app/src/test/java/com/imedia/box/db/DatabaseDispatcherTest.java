package com.imedia.box.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseDispatcherTest {

    @Test
    public void dispatchesDatabaseWorkWhenRequired() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            long callerThread = Thread.currentThread().threadId();
            DatabaseDispatcher dispatcher = new DatabaseDispatcher(executor, () -> true);
            long databaseThread = dispatcher.call(() -> Thread.currentThread().threadId());
            assertNotEquals(callerThread, databaseThread);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void keepsWorkerThreadCallsInline() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            long callerThread = Thread.currentThread().threadId();
            DatabaseDispatcher dispatcher = new DatabaseDispatcher(executor, () -> false);
            assertEquals(callerThread, dispatcher.call(() -> Thread.currentThread().threadId()).longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void preservesRuntimeFailure() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            IllegalArgumentException failure = new IllegalArgumentException("failure");
            DatabaseDispatcher dispatcher = new DatabaseDispatcher(executor, () -> true);
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> dispatcher.call(() -> { throw failure; }));
            assertSame(failure, thrown);
        } finally {
            executor.shutdownNow();
        }
    }
}
