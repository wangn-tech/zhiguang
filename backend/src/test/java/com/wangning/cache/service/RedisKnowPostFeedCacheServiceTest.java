package com.wangning.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wangning.cache.config.CacheProperties;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.model.FeedItemSnapshot;
import com.wangning.cache.model.FeedPageSnapshot;
import com.wangning.cache.service.impl.RedisKnowPostFeedCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RedisKnowPostFeedCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisKnowPostFeedCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        cacheService = new RedisKnowPostFeedCacheService(
                new ObjectMapper().findAndRegisterModules(),
                redisTemplate,
                new CacheProperties(),
                Caffeine.newBuilder().build(),
                Caffeine.newBuilder().build()
        );
    }

    @Test
    void shouldWriteAndReadPublicPageFromLocalCache() {
        FeedPageSnapshot snapshot = snapshot();

        cacheService.putPublic(snapshot);

        assertThat(cacheService.findPublic(1, 20)).contains(snapshot);
        verify(valueOperations).set(
                eq(CacheKeys.publicFeedKey(1, 20)),
                any(String.class),
                eq(new CacheProperties().getL2().getPublicFeedTtl())
        );
        verify(setOperations).add(CacheKeys.publicFeedIndexKey(), CacheKeys.publicFeedKey(1, 20));
        verify(valueOperations, never()).get(CacheKeys.publicFeedKey(1, 20));
    }

    @Test
    void shouldPromoteAuthorFeedRedisHitIntoLocalCache() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(valueOperations.get(CacheKeys.mineFeedKey(7L, 1, 20)))
                .thenReturn(objectMapper.writeValueAsString(snapshot()));

        assertThat(cacheService.findMine(7L, 1, 20)).contains(snapshot());
        assertThat(cacheService.findMine(7L, 1, 20)).contains(snapshot());

        verify(valueOperations).get(CacheKeys.mineFeedKey(7L, 1, 20));
    }

    @Test
    void shouldNotCacheEmptyPage() {
        cacheService.putPublic(new FeedPageSnapshot(List.of(), 1, 20, false));

        verify(valueOperations, never()).set(
                eq(CacheKeys.publicFeedKey(1, 20)), any(String.class), any()
        );
    }

    private FeedPageSnapshot snapshot() {
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
