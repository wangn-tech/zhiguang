package com.wangning.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigurationTest {

    private final CacheConfiguration configuration = new CacheConfiguration();
    private final CacheProperties properties = new CacheProperties();

    @Test
    void shouldCreateIndependentLocalCaches() {
        Cache<String, String> detailCache = configuration.knowPostDetailLocalCache(properties);
        Cache<String, String> publicFeedCache = configuration.publicFeedLocalCache(properties);
        Cache<String, String> mineFeedCache = configuration.mineFeedLocalCache(properties);

        detailCache.put("detail", "value");
        publicFeedCache.put("feed", "value");
        mineFeedCache.put("mine", "value");

        assertThat(detailCache.getIfPresent("detail")).isEqualTo("value");
        assertThat(publicFeedCache.getIfPresent("feed")).isEqualTo("value");
        assertThat(mineFeedCache.getIfPresent("mine")).isEqualTo("value");
    }
}
