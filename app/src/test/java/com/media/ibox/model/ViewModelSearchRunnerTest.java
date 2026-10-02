package com.media.ibox.model;

import com.media.ibox.bean.Result;
import com.media.ibox.bean.Site;
import com.media.ibox.bean.Vod;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ViewModelSearchRunnerTest {

    @Test
    public void aggregatedSearchKeepsSuccessfulResultsAndCallsBackOnce() throws Exception {
        ViewModelSearchRunner runner = new ViewModelSearchRunner(4);
        Site first = new Site();
        Site second = new Site();
        Site third = new Site();
        Site fourth = new Site();
        Vod firstVod = new Vod();
        Vod thirdVod = new Vod();
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Result> result = new AtomicReference<>();

        try {
            runner.startAggregated(Arrays.asList(first, second, third, fourth), site -> () -> {
                if (site == first) {
                    Thread.sleep(20);
                    return Result.vod(firstVod);
                }
                if (site == second) {
                    Thread.sleep(50);
                    return Result.empty();
                }
                if (site == third) {
                    Thread.sleep(10);
                    return Result.vod(thirdVod);
                }
                Thread.sleep(30);
                throw new IllegalStateException("search failed");
            }, value -> {
                callbacks.incrementAndGet();
                result.set(value);
                done.countDown();
            });

            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(1, callbacks.get());
            List<Vod> items = result.get().getList();
            assertEquals(2, items.size());
            assertTrue(items.stream().anyMatch(item -> item == firstVod));
            assertTrue(items.stream().anyMatch(item -> item == thirdVod));
            assertEquals(0, runner.pendingCount());
        } finally {
            runner.stop();
        }
    }

    @Test
    public void aggregatedSearchWithNoSitesReturnsEmptyImmediately() {
        ViewModelSearchRunner runner = new ViewModelSearchRunner(1);
        AtomicReference<Result> result = new AtomicReference<>();

        runner.startAggregated(List.of(), site -> Result::empty, result::set);

        assertNotNull(result.get());
        assertTrue(result.get().getList().isEmpty());
        assertEquals(0, runner.pendingCount());
    }

    @Test
    public void searchUsesConfiguredWorkerLimit() throws Exception {
        ViewModelSearchRunner runner = new ViewModelSearchRunner(2);
        List<Site> sites = Arrays.asList(new Site(), new Site(), new Site(), new Site(), new Site(), new Site());
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(sites.size());

        try {
            runner.start(sites, site -> () -> {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(40);
                    return Result.empty();
                } finally {
                    active.decrementAndGet();
                }
            }, result -> done.countDown());

            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertTrue(maximum.get() <= 2);
            assertEquals(0, runner.pendingCount());
        } finally {
            runner.stop();
        }
    }
}
