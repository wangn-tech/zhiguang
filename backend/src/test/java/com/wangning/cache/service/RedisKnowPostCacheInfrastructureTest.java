package com.wangning.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.model.FeedItemSnapshot;
import com.wangning.cache.model.FeedPageSnapshot;
import com.wangning.cache.model.KnowPostDetailSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 Redis 验证知文两级缓存的读写及索引失效行为。
 */
@SpringBootTest
@Testcontainers
class RedisKnowPostCacheInfrastructureTest {

    private static final String REDIS_PASSWORD = "zhiguang_redis_test";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.4.5-alpine")
    )
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @Autowired
    private KnowPostDetailCacheService detailCacheService;

    @Autowired
    private KnowPostFeedCacheService feedCacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("knowPostDetailLocalCache")
    private Cache<String, String> detailLocalCache;

    @Autowired
    @Qualifier("publicFeedLocalCache")
    private Cache<String, String> publicFeedLocalCache;

    @Autowired
    @Qualifier("mineFeedLocalCache")
    private Cache<String, String> mineFeedLocalCache;

    /**
     * 为 Spring Redis 客户端注入 Testcontainers Redis 地址。
     *
     * @param registry 测试动态配置注册器
     */
    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    }

    @BeforeEach
    void resetCaches() {
        detailLocalCache.invalidateAll();
        publicFeedLocalCache.invalidateAll();
        mineFeedLocalCache.invalidateAll();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void shouldReadDetailFromRedisAfterLocalCacheEviction() {
        detailCacheService.put(detailSnapshot());
        detailLocalCache.invalidateAll();

        assertThat(detailCacheService.find(100L)).contains(detailSnapshot());
        assertThat(detailLocalCache.getIfPresent(CacheKeys.detailKey(100L))).isNotNull();
    }

    @Test
    void shouldInvalidateIndexedPublicAndAuthorFeedPages() {
        FeedPageSnapshot snapshot = feedSnapshot();
        feedCacheService.putPublic(snapshot);
        feedCacheService.putMine(7L, snapshot);

        assertThat(redisTemplate.hasKey(CacheKeys.publicFeedKey(1, 20))).isTrue();
        assertThat(redisTemplate.hasKey(CacheKeys.mineFeedKey(7L, 1, 20))).isTrue();

        feedCacheService.invalidatePublic();
        feedCacheService.invalidateMine(7L);

        assertThat(redisTemplate.hasKey(CacheKeys.publicFeedKey(1, 20))).isFalse();
        assertThat(redisTemplate.hasKey(CacheKeys.mineFeedKey(7L, 1, 20))).isFalse();
        assertThat(publicFeedLocalCache.getIfPresent(CacheKeys.publicFeedKey(1, 20))).isNull();
        assertThat(mineFeedLocalCache.getIfPresent(CacheKeys.mineFeedKey(7L, 1, 20))).isNull();
    }

    private KnowPostDetailSnapshot detailSnapshot() {
        return new KnowPostDetailSnapshot(
                "100", "标题", "摘要", "https://static.example.com/posts/100/content.md",
                List.of(), List.of("Java"), "7", null, "作者", "[]",
                false, "public", "image_text", null
        );
    }

    private FeedPageSnapshot feedSnapshot() {
        return new FeedPageSnapshot(
                List.of(new FeedItemSnapshot(
                        "100", "标题", "摘要", null, List.of("Java"),
                        null, "作者", "[]", false
                )),
                1,
                20,
                false
        );
    }
}
