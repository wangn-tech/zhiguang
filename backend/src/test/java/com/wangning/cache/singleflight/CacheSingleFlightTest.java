package com.wangning.cache.singleflight;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheSingleFlightTest {

    @Test
    void shouldExecuteLoaderOnlyOnceForConcurrentSameKey() throws Exception {
        CacheSingleFlight singleFlight = new CacheSingleFlight();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            Future<String> leader = executor.submit(() -> singleFlight.execute("cache:key", () -> {
                calls.incrementAndGet();
                loaderStarted.countDown();
                await(releaseLoader);
                return "value";
            }));
            assertThat(loaderStarted.await(3, TimeUnit.SECONDS)).isTrue();

            List<AtomicReference<String>> followerValues = new ArrayList<>();
            List<AtomicReference<Throwable>> followerFailures = new ArrayList<>();
            List<Thread> followers = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                AtomicReference<String> value = new AtomicReference<>();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread follower = new Thread(() -> {
                    try {
                        value.set(singleFlight.execute("cache:key", () -> "unexpected"));
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                });
                follower.start();
                awaitWaiting(follower);
                followerValues.add(value);
                followerFailures.add(failure);
                followers.add(follower);
            }
            releaseLoader.countDown();

            assertThat(leader.get(3, TimeUnit.SECONDS)).isEqualTo("value");
            for (int index = 0; index < followers.size(); index++) {
                followers.get(index).join(3_000);
                assertThat(followers.get(index).isAlive()).isFalse();
                assertThat(followerFailures.get(index).get()).isNull();
                assertThat(followerValues.get(index).get()).isEqualTo("value");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldRemoveFailedFlightSoNextRequestCanRetry() {
        CacheSingleFlight singleFlight = new CacheSingleFlight();

        assertThatThrownBy(() -> singleFlight.execute("cache:key", () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("failed");

        assertThat(singleFlight.execute("cache:key", () -> "recovered")).isEqualTo("recovered");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("等待并发测试协调信号超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待并发测试协调信号被中断", exception);
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("singleflight follower 未进入等待状态");
    }
}
