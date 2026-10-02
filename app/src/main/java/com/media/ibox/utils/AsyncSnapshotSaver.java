package com.media.ibox.utils;

import java.util.function.Consumer;
import java.util.function.Function;

public final class AsyncSnapshotSaver<T> {

    private final Function<T, T> snapshotter;
    private final Consumer<T> saver;
    private final Consumer<Runnable> dispatcher;

    public AsyncSnapshotSaver(Function<T, T> snapshotter, Consumer<T> saver, Consumer<Runnable> dispatcher) {
        this.snapshotter = snapshotter;
        this.saver = saver;
        this.dispatcher = dispatcher;
    }

    public void save(T source) {
        T snapshot = snapshotter.apply(source);
        dispatcher.accept(() -> saver.accept(snapshot));
    }
}
