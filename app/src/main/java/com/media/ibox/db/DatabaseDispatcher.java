package com.media.ibox.db;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

final class DatabaseDispatcher {

    private final ExecutorService executor;
    private final BooleanSupplier dispatchRequired;

    DatabaseDispatcher(ExecutorService executor, BooleanSupplier dispatchRequired) {
        this.executor = executor;
        this.dispatchRequired = dispatchRequired;
    }

    <T> T call(Callable<T> action) {
        if (!dispatchRequired.getAsBoolean()) return invoke(action);
        try {
            return executor.submit(action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database operation interrupted", e);
        } catch (ExecutionException e) {
            return rethrow(e.getCause());
        }
    }

    private <T> T invoke(Callable<T> action) {
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Database operation failed", e);
        }
    }

    private <T> T rethrow(Throwable error) {
        if (error instanceof RuntimeException runtimeException) throw runtimeException;
        if (error instanceof Error fatalError) throw fatalError;
        throw new IllegalStateException("Database operation failed", error);
    }
}
