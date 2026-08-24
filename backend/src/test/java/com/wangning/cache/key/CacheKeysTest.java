package com.wangning.cache.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheKeysTest {

    @Test
    void shouldBuildStableCacheKeys() {
        assertThat(CacheKeys.detailKey(100L)).isEqualTo("cache:kp:detail:100");
        assertThat(CacheKeys.detailLockKey(100L)).isEqualTo("cache:lock:kp:detail:100");
        assertThat(CacheKeys.publicFeedKey(2, 20)).isEqualTo("cache:kp:feed:public:2:20");
        assertThat(CacheKeys.mineFeedKey(7L, 2, 20)).isEqualTo("cache:kp:feed:mine:7:2:20");
        assertThat(CacheKeys.publicFeedIndexKey()).isEqualTo("cache:kp:feed:public:index");
        assertThat(CacheKeys.mineFeedIndexKey(7L)).isEqualTo("cache:kp:feed:mine:index:7");
    }

    @Test
    void shouldRejectInvalidKeyArguments() {
        assertThatThrownBy(() -> CacheKeys.detailKey(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheKeys.publicFeedKey(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheKeys.mineFeedKey(1L, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
