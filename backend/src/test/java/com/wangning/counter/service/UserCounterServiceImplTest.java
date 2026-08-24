package com.wangning.counter.service;

import com.wangning.counter.service.impl.UserCounterServiceImpl;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.relation.mapper.RelationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCounterServiceImplTest {

    @Test
    void shouldRebuildAllUserCountersFromFacts() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RelationMapper relationMapper = mock(RelationMapper.class);
        KnowPostMapper knowPostMapper = mock(KnowPostMapper.class);
        CounterService counterService = mock(CounterService.class);
        UserCounterService userCounterService = new UserCounterServiceImpl(
                redisTemplate, relationMapper, knowPostMapper, counterService
        );
        when(relationMapper.countFollowings(1L)).thenReturn(2L);
        when(relationMapper.countFollowers(1L)).thenReturn(3L);
        when(knowPostMapper.listPublishedIdsByCreator(1L)).thenReturn(List.of(100L, 101L));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 4L, "fav", 5L));
        when(counterService.getCounts("knowpost", "101", List.of("like", "fav")))
                .thenReturn(Map.of("like", 6L, "fav", 7L));

        UserCounters counters = userCounterService.rebuildCounters(1L);

        assertThat(counters).isEqualTo(new UserCounters(2L, 3L, 2L, 10L, 12L));
        verify(counterService).getCounts("knowpost", "100", List.of("like", "fav"));
        verify(counterService).getCounts("knowpost", "101", List.of("like", "fav"));
    }
}
