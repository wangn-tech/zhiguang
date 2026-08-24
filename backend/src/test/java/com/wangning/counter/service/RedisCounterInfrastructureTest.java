package com.wangning.counter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.counter.config.CounterEventProperties;
import com.wangning.counter.event.CounterAggregationConsumer;
import com.wangning.counter.event.CounterEvent;
import com.wangning.counter.schema.CounterMetric;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.mapper.KnowPostMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private UserCounterService userCounterService;

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
        KnowPostMapper knowPostMapper = mock(KnowPostMapper.class);
        when(knowPostMapper.findById(102L)).thenReturn(KnowPost.builder().id(102L).creatorId(9L).build());
        redisTemplate.opsForValue().set(CounterKeys.userCounterInitializedKey(9L), "1");
        CounterAggregationConsumer consumer = new CounterAggregationConsumer(
                new ObjectMapper(),
                redisTemplate,
                new CounterEventProperties(),
                knowPostMapper
        );
        CounterEvent like = CounterEvent.of("knowpost", "102", CounterMetric.LIKE, 1L, 1);
        CounterEvent fav = CounterEvent.of("knowpost", "102", CounterMetric.FAV, 1L, 1);

        consumer.aggregate(like);
        consumer.aggregate(like);
        consumer.aggregate(fav);
        consumer.flush();

        assertThat(counterService.getCounts("knowpost", "102", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 1L));
        assertThat(userCounterService.getCounters(9L))
                .isEqualTo(new UserCounters(0L, 0L, 0L, 1L, 1L));
    }

    @Test
    void shouldRecoverMissingEntitySdsFromRegisteredBitmapShards() {
        assertThat(counterService.like("knowpost", "103", 5L)).isTrue();
        assertThat(counterService.fav("knowpost", "103", 6L)).isTrue();

        assertThat(counterService.getCounts("knowpost", "103", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 1L));
        assertThat(redisTemplate.opsForValue().get(CounterKeys.recoveryFenceKey("knowpost", "103")))
                .isEqualTo("2");
    }

    @Test
    void shouldIgnoreDelayedEntityEventBeforeRecoveryFence() {
        KnowPostMapper knowPostMapper = mock(KnowPostMapper.class);
        when(knowPostMapper.findById(104L)).thenReturn(KnowPost.builder().id(104L).creatorId(9L).build());
        CounterAggregationConsumer consumer = new CounterAggregationConsumer(
                new ObjectMapper(), redisTemplate, new CounterEventProperties(), knowPostMapper
        );

        assertThat(counterService.like("knowpost", "104", 7L)).isTrue();
        assertThat(counterService.getCounts("knowpost", "104", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 0L));

        consumer.aggregate(CounterEvent.of("knowpost", "104", CounterMetric.LIKE, 7L, 1, 1L));
        consumer.flush();

        assertThat(counterService.getCounts("knowpost", "104", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 0L));

        assertThat(counterService.fav("knowpost", "104", 8L)).isTrue();
        consumer.aggregate(CounterEvent.of("knowpost", "104", CounterMetric.FAV, 8L, 1, 2L));
        consumer.flush();

        assertThat(counterService.getCounts("knowpost", "104", List.of("like", "fav")))
                .isEqualTo(Map.of("like", 1L, "fav", 1L));
    }

    @Test
    void shouldMaintainIndependentUserSdsCountersWithoutNegativeValues() {
        userCounterService.incrementFollowings(1L, 2);
        userCounterService.incrementFollowers(1L, 3);
        userCounterService.incrementPosts(1L, 4);
        userCounterService.incrementLikesReceived(1L, 5);
        userCounterService.incrementFavsReceived(1L, 6);
        userCounterService.incrementFollowers(1L, -10);

        assertThat(userCounterService.getCounters(1L))
                .isEqualTo(new UserCounters(2L, 0L, 4L, 5L, 6L));
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
