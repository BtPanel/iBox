package com.imedia.box.model;

import com.imedia.box.Constant;
import com.imedia.box.bean.Result;
import com.imedia.box.bean.Site;
import com.imedia.box.bean.Vod;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class ViewModelSearchRunner {

    private final Set<Future<?>> futures;
    private final AtomicInteger epoch;
    private final int searchThreads;
    private ListeningExecutorService executor;

    ViewModelSearchRunner() {
        this(0);
    }

    ViewModelSearchRunner(int searchThreads) {
        futures = ConcurrentHashMap.newKeySet();
        epoch = new AtomicInteger(0);
        this.searchThreads = searchThreads;
    }

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult) {
        int current = nextEpoch();
        resetExecutor();
        sites.forEach(site -> execute(taskFactory.apply(site), current, onResult));
    }

    void startAggregated(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult) {
        int current = nextEpoch();
        resetExecutor();
        if (sites.isEmpty()) {
            onResult.accept(Result.empty());
            return;
        }
        List<Vod> items = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(sites.size());
        sites.forEach(site -> executeAggregated(taskFactory.apply(site), current, items, remaining, onResult));
    }

    void stop() {
        nextEpoch();
        cancelFutures();
        shutdownExecutor();
    }

    private int nextEpoch() {
        return epoch.incrementAndGet();
    }

    private void cancelFutures() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void resetExecutor() {
        cancelFutures();
        shutdownExecutor();
        executor = Task.limitedExecutor(getSearchThreads());
    }

    private void shutdownExecutor() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private void execute(Callable<Result> callable, int current, Consumer<Result> onResult) {
        FluentFuture<Result> future = FluentFuture.from(executor.submit(callable)).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    futures.remove(future);
                    if (epoch.get() == current) onResult.accept(result);
                },
                error -> futures.remove(future)
        ), MoreExecutors.directExecutor());
    }

    private int getSearchThreads() {
        return Math.max(1, searchThreads > 0 ? searchThreads : Setting.getSearchThreads());
    }

    private void executeAggregated(Callable<Result> callable, int current, List<Vod> items, AtomicInteger remaining, Consumer<Result> onResult) {
        FluentFuture<Result> future = FluentFuture.from(executor.submit(callable)).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    futures.remove(future);
                    completeAggregated(result, current, items, remaining, onResult);
                },
                error -> {
                    futures.remove(future);
                    completeAggregated(null, current, items, remaining, onResult);
                }
        ), MoreExecutors.directExecutor());
    }

    int pendingCount() {
        return futures.size();
    }

    private void completeAggregated(Result result, int current, List<Vod> items, AtomicInteger remaining, Consumer<Result> onResult) {
        if (epoch.get() != current) return;
        if (result != null) items.addAll(result.getList());
        if (remaining.decrementAndGet() != 0) return;
        List<Vod> snapshot;
        synchronized (items) {
            snapshot = new ArrayList<>(items);
        }
        if (epoch.get() == current) onResult.accept(Result.list(snapshot));
    }
}
