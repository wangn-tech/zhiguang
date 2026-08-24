package com.wangning.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wangning.cache.config.CacheProperties;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.model.KnowPostDetailSnapshot;
import com.wangning.cache.service.impl.RedisKnowPostDetailCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisKnowPostDetailCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisKnowPostDetailCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new RedisKnowPostDetailCacheService(
                new ObjectMapper().findAndRegisterModules(),
                redisTemplate,
                new CacheProperties(),
                Caffeine.newBuilder().build()
        );
    }

    @Test
    void shouldWriteAndReadDetailSnapshotFromLocalCache() {
        KnowPostDetailSnapshot snapshot = snapshot();

        cacheService.put(snapshot);
        var found = cacheService.find(100L);

        assertThat(found).contains(snapshot);
        verify(valueOperations).set(
                eq(CacheKeys.detailKey(100L)),
                any(String.class),
                eq(new CacheProperties().getL2().getDetailTtl())
        );
        verify(valueOperations, never()).get(CacheKeys.detailKey(100L));
    }

    @Test
    void shouldPromoteRedisHitIntoLocalCache() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(valueOperations.get(CacheKeys.detailKey(100L)))
                .thenReturn(objectMapper.writeValueAsString(snapshot()));

        assertThat(cacheService.find(100L)).contains(snapshot());
        assertThat(cacheService.find(100L)).contains(snapshot());

        verify(valueOperations).get(CacheKeys.detailKey(100L));
    }

    @Test
    void shouldDiscardMalformedCachedJson() {
        when(valueOperations.get(CacheKeys.detailKey(100L))).thenReturn("not-json");

        assertThat(cacheService.find(100L)).isEmpty();

        verify(redisTemplate).delete(CacheKeys.detailKey(100L));
    }

    private KnowPostDetailSnapshot snapshot() {
        return new KnowPostDetailSnapshot(
                "100", "标题", "摘要", "https://static.example.com/posts/100/content.md",
                List.of("https://static.example.com/posts/100/image.png"), List.of("Java"),
                "1", "https://static.example.com/avatars/1.png", "作者", "[]",
                false, "public", "image_text", null
        );
    }
}
