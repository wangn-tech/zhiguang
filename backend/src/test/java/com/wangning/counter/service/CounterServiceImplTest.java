package com.wangning.counter.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.counter.event.CounterEvent;
import com.wangning.counter.event.CounterEventPublisher;
import com.wangning.counter.service.impl.CounterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class CounterServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CounterEventPublisher eventPublisher;

    @Test
    void shouldToggleLikeUsingExpectedBitmapState() {
        CounterService counterService = new CounterServiceImpl(redisTemplate, eventPublisher);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of("bm:like:knowpost:100:1")),
                eq("7232"), eq("0"), eq("1"))).thenReturn(1L);

        assertThat(counterService.like("knowpost", "100", 40_000L)).isTrue();

        verify(redisTemplate).execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of("bm:like:knowpost:100:1")),
                eq("7232"), eq("0"), eq("1"));
        verify(eventPublisher).publish(argThat(event -> event.getMetric().equals("like")
                && event.getIndex() == 1 && event.getDelta() == 1 && event.getUserId() == 40_000L));
    }

    @Test
    void shouldUseIndependentBitmapForFavorite() {
        CounterService counterService = new CounterServiceImpl(redisTemplate, eventPublisher);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of("bm:fav:knowpost:100:0")),
                eq("1"), eq("1"), eq("0"))).thenReturn(0L);

        assertThat(counterService.unfav("knowpost", "100", 1L)).isFalse();
    }

    @Test
    void shouldRejectUnsupportedOrInvalidIdentifiers() {
        CounterService counterService = new CounterServiceImpl(redisTemplate, eventPublisher);

        assertThatThrownBy(() -> counterService.like("comment", "100", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持知文互动");
        assertThatThrownBy(() -> counterService.like("knowpost", "0", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("实体 ID 必须为正整数");
        assertThatThrownBy(() -> counterService.like("knowpost", "100", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户 ID 必须为正整数");
    }
}
