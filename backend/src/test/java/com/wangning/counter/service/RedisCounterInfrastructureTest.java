package com.wangning.counter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.counter.config.CounterEventProperties;
import com.wangning.counter.event.CounterAggregationConsumer;
import com.wangning.counter.event.CounterEvent;
import com.wangning.counter.schema.CounterMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RedisCounterInfrastructureTest {

    private static final String REDIS_PASSWORD = "zhiguang_redis_test";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.4.5-alpine")
    )
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    }

    @Autowired
    private CounterService counterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void shouldToggleLikeAndFavoriteIndependently() {
        assertThat(counterService.like("knowpost", "100", 1L)).isTrue();
        assertThat(counterService.like("knowpost", "100", 1L)).isFalse();
        assertThat(counterService.isLiked("knowpost", "100", 1L)).isTrue();
        assertThat(counterService.isFaved("knowpost", "100", 1L)).isFalse();

        assertThat(counterService.fav("knowpost", "100", 1L)).isTrue();
        assertThat(counterService.unlike("knowpost", "100", 1L)).isTrue();
        assertThat(counterService.unlike("knowpost", "100", 1L)).isFalse();
        assertThat(counterService.isLiked("knowpost", "100", 1L)).isFalse();
        assertThat(counterService.isFaved("knowpost", "100", 1L)).isTrue();
    }

    @Test
    void shouldAllowOnlyOneConcurrentLikeStateChange() throws Exception {
        List<Boolean> results = runConcurrently(8, () -> counterService.like("knowpost", "101", 2L));

        assertThat(results).contains(true, false);
        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(counterService.isLiked("knowpost", "101", 2L)).isTrue();
    }

    @Test
    void shouldFoldDeduplicatedEventsIntoEntitySds() {
        CounterAggregationConsumer consumer = new CounterAggregationConsumer(
                new ObjectMapper(),
                redisTemplate,
                new CounterEventProperties()
        );
        CounterEvent like = CounterEvent.of("knowpost", "102", CounterMetric.LIKE, 1L, 1);
        CounterEvent fav = CounterEvent.of("knowpost", "102", CounterMetric.FAV, 1L, 1);

        consumer.aggregate(like);
        consumer.aggregate(like);
        consumer.aggregate(fav);
        consumer.flush();

        assertThat(counterService.getCounts("knowpost", "102", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 1L));
    }

    private <T> List<T> runConcurrently(int taskCount, CheckedSupplier<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < taskCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.get();
                }));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>(taskCount);
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }
}
