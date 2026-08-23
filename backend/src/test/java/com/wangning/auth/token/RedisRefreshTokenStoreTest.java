package com.wangning.auth.token;

import org.awaitility.Awaitility;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RedisRefreshTokenStoreTest {

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
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void shouldStoreExpireAndRevokeToken() {
        refreshTokenStore.storeToken(1L, "short-lived", Duration.ofMillis(100));
        assertThat(refreshTokenStore.isTokenValid(1L, "short-lived")).isTrue();

        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(
                        refreshTokenStore.isTokenValid(1L, "short-lived")
                ).isFalse());

        refreshTokenStore.storeToken(1L, "revoked", Duration.ofMinutes(5));
        refreshTokenStore.revokeToken(1L, "revoked");
        assertThat(refreshTokenStore.isTokenValid(1L, "revoked")).isFalse();
    }

    @Test
    void shouldAllowOnlyOneConcurrentRotation() throws Exception {
        long userId = 2L;
        String currentTokenId = "current-token";
        refreshTokenStore.storeToken(userId, currentTokenId, Duration.ofMinutes(5));

        List<RotationAttempt> attempts = runConcurrentRotations(userId, currentTokenId, 8);

        assertThat(attempts.stream().filter(RotationAttempt::success)).hasSize(1);
        assertThat(refreshTokenStore.isTokenValid(userId, currentTokenId)).isFalse();
        assertThat(attempts.stream().filter(attempt ->
                refreshTokenStore.isTokenValid(userId, attempt.nextTokenId())
        )).hasSize(1);
    }

    @Test
    void shouldRevokeAllUserTokensWithoutAffectingOtherUsers() {
        for (int index = 0; index < 205; index++) {
            refreshTokenStore.storeToken(3L, "user-3-token-" + index, Duration.ofMinutes(5));
        }
        refreshTokenStore.storeToken(4L, "user-4-token", Duration.ofMinutes(5));

        refreshTokenStore.revokeAll(3L);

        for (int index = 0; index < 205; index++) {
            assertThat(refreshTokenStore.isTokenValid(3L, "user-3-token-" + index)).isFalse();
        }
        assertThat(refreshTokenStore.isTokenValid(4L, "user-4-token")).isTrue();
    }

    /**
     * 并发使用同一个旧 jti 尝试轮换到不同的新 jti。
     *
     * @param userId 用户 ID
     * @param currentTokenId 旧 jti
     * @param taskCount 并发任务数
     * @return 每次轮换的结果
     * @throws Exception 并发任务执行失败时抛出
     */
    private List<RotationAttempt> runConcurrentRotations(
            long userId,
            String currentTokenId,
            int taskCount
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RotationAttempt>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < taskCount; index++) {
                String nextTokenId = "next-token-" + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    boolean success = refreshTokenStore.rotateToken(
                            userId,
                            currentTokenId,
                            nextTokenId,
                            Duration.ofMinutes(5)
                    );
                    return new RotationAttempt(nextTokenId, success);
                }));
            }
            ready.await();
            start.countDown();

            List<RotationAttempt> results = new ArrayList<>(taskCount);
            for (Future<RotationAttempt> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 一次 Refresh Token 轮换尝试。
     *
     * @param nextTokenId 新 jti
     * @param success 是否轮换成功
     */
    private record RotationAttempt(String nextTokenId, boolean success) {
    }
}
