package com.wangning.auth.verification;

import com.wangning.auth.config.AuthProperties;
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
class RedisVerificationInfrastructureTest {

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
    private VerificationCodeStore codeStore;

    @Autowired
    private VerificationRateLimiter rateLimiter;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetRedisAndConfiguration() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        authProperties.getVerification().setSendInterval(Duration.ofSeconds(60));
        authProperties.getVerification().setDailyLimit(10);
    }

    @Test
    void shouldSaveAndConsumeCorrectCode() {
        codeStore.saveCode(
                VerificationScene.REGISTER,
                "13800138000",
                "123456",
                Duration.ofMinutes(5),
                3
        );

        VerificationCheckResult success = codeStore.verify(
                VerificationScene.REGISTER,
                "13800138000",
                "123456"
        );
        VerificationCheckResult reused = codeStore.verify(
                VerificationScene.REGISTER,
                "13800138000",
                "123456"
        );

        assertThat(success.status()).isEqualTo(VerificationCodeStatus.SUCCESS);
        assertThat(success.attempts()).isZero();
        assertThat(success.maxAttempts()).isEqualTo(3);
        assertThat(reused.status()).isEqualTo(VerificationCodeStatus.NOT_FOUND);
    }

    @Test
    void shouldCountMismatchesAndInvalidateAtAttemptLimit() {
        codeStore.saveCode(
                VerificationScene.LOGIN,
                "13800138001",
                "654321",
                Duration.ofMinutes(5),
                2
        );

        VerificationCheckResult firstMismatch = codeStore.verify(
                VerificationScene.LOGIN,
                "13800138001",
                "000000"
        );
        VerificationCheckResult attemptLimit = codeStore.verify(
                VerificationScene.LOGIN,
                "13800138001",
                "111111"
        );
        VerificationCheckResult afterInvalidation = codeStore.verify(
                VerificationScene.LOGIN,
                "13800138001",
                "654321"
        );

        assertThat(firstMismatch.status()).isEqualTo(VerificationCodeStatus.MISMATCH);
        assertThat(firstMismatch.attempts()).isEqualTo(1);
        assertThat(attemptLimit.status()).isEqualTo(VerificationCodeStatus.TOO_MANY_ATTEMPTS);
        assertThat(attemptLimit.attempts()).isEqualTo(2);
        assertThat(afterInvalidation.status()).isEqualTo(VerificationCodeStatus.NOT_FOUND);
    }

    @Test
    void shouldExpireAndInvalidateCode() {
        codeStore.saveCode(
                VerificationScene.RESET_PASSWORD,
                "user@example.com",
                "123456",
                Duration.ofMillis(100),
                3
        );

        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(codeStore.verify(
                        VerificationScene.RESET_PASSWORD,
                        "user@example.com",
                        "123456"
                ).status()).isEqualTo(VerificationCodeStatus.NOT_FOUND));

        codeStore.saveCode(
                VerificationScene.RESET_PASSWORD,
                "user@example.com",
                "654321",
                Duration.ofMinutes(5),
                3
        );
        codeStore.invalidate(VerificationScene.RESET_PASSWORD, "user@example.com");

        assertThat(codeStore.verify(
                VerificationScene.RESET_PASSWORD,
                "user@example.com",
                "654321"
        ).status()).isEqualTo(VerificationCodeStatus.NOT_FOUND);
    }

    @Test
    void shouldAllowOnlyOneConcurrentCodeConsumption() throws Exception {
        codeStore.saveCode(
                VerificationScene.LOGIN,
                "13800138002",
                "123456",
                Duration.ofMinutes(5),
                10
        );

        List<VerificationCheckResult> results = runConcurrently(8, () -> codeStore.verify(
                VerificationScene.LOGIN,
                "13800138002",
                "123456"
        ));

        assertThat(results)
                .extracting(VerificationCheckResult::status)
                .containsOnly(VerificationCodeStatus.SUCCESS, VerificationCodeStatus.NOT_FOUND);
        assertThat(results.stream().filter(VerificationCheckResult::isSuccess)).hasSize(1);
    }

    @Test
    void shouldEnforceSendCooldown() {
        VerificationRateLimitResult first = rateLimiter.tryAcquire(
                VerificationScene.LOGIN,
                "13800138003"
        );
        VerificationRateLimitResult second = rateLimiter.tryAcquire(
                VerificationScene.LOGIN,
                "13800138003"
        );

        assertThat(first).isEqualTo(VerificationRateLimitResult.ALLOWED);
        assertThat(second).isEqualTo(VerificationRateLimitResult.TOO_FREQUENT);
    }

    @Test
    void shouldEnforceDailyLimitWithoutCooldown() {
        authProperties.getVerification().setSendInterval(Duration.ZERO);
        authProperties.getVerification().setDailyLimit(2);

        assertThat(rateLimiter.tryAcquire(VerificationScene.REGISTER, "13800138004"))
                .isEqualTo(VerificationRateLimitResult.ALLOWED);
        assertThat(rateLimiter.tryAcquire(VerificationScene.REGISTER, "13800138004"))
                .isEqualTo(VerificationRateLimitResult.ALLOWED);
        assertThat(rateLimiter.tryAcquire(VerificationScene.REGISTER, "13800138004"))
                .isEqualTo(VerificationRateLimitResult.DAILY_LIMIT_REACHED);
    }

    @Test
    void shouldAllowOnlyOneConcurrentSendPermit() throws Exception {
        List<VerificationRateLimitResult> results = runConcurrently(8, () -> rateLimiter.tryAcquire(
                VerificationScene.LOGIN,
                "13800138005"
        ));

        assertThat(results.stream().filter(result -> result == VerificationRateLimitResult.ALLOWED))
                .hasSize(1);
        assertThat(results.stream().filter(result -> result == VerificationRateLimitResult.TOO_FREQUENT))
                .hasSize(7);
    }

    /**
     * 同时执行多个 Redis 操作并收集结果。
     *
     * @param taskCount 并发任务数
     * @param task 待执行任务
     * @param <T> 结果类型
     * @return 全部执行结果
     * @throws Exception 任务执行失败时抛出
     */
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

    /**
     * 允许测试任务抛出受检异常的结果提供器。
     *
     * @param <T> 结果类型
     */
    @FunctionalInterface
    private interface CheckedSupplier<T> {

        /**
         * 执行测试任务。
         *
         * @return 执行结果
         * @throws Exception 执行失败时抛出
         */
        T get() throws Exception;
    }
}
