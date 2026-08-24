package com.wangning.counter.service;

import com.wangning.common.exception.BusinessException;
import com.wangning.counter.service.impl.CounterActionServiceImpl;
import com.wangning.knowpost.domain.KnowPost;
import com.wangning.knowpost.mapper.KnowPostMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterActionServiceImplTest {

    @Mock
    private CounterService counterService;

    @Mock
    private KnowPostMapper knowPostMapper;

    @Test
    void shouldValidatePublicKnowPostBeforeLiking() {
        when(knowPostMapper.findById(100L)).thenReturn(publicPost(2L));
        when(counterService.like("knowpost", "100", 1L)).thenReturn(true);
        when(counterService.isLiked("knowpost", "100", 1L)).thenReturn(true);

        CounterActionResult result = service().like("knowpost", "100", 1L);

        assertThat(result).isEqualTo(new CounterActionResult(true, true));
        verify(counterService).like("knowpost", "100", 1L);
        verify(counterService).isLiked("knowpost", "100", 1L);
    }

    @Test
    void shouldRejectSelfInteractionAndUnavailablePost() {
        when(knowPostMapper.findById(100L)).thenReturn(publicPost(1L));

        assertThatThrownBy(() -> service().fav("knowpost", "100", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能与自己的知文互动");
        verify(counterService, never()).fav("knowpost", "100", 1L);

        when(knowPostMapper.findById(101L)).thenReturn(KnowPost.builder()
                .id(101L)
                .creatorId(2L)
                .status("draft")
                .visible("public")
                .build());
        assertThatThrownBy(() -> service().fav("knowpost", "101", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知文不存在或不可互动");
    }

    private CounterActionService service() {
        return new CounterActionServiceImpl(counterService, knowPostMapper);
    }

    private KnowPost publicPost(long creatorId) {
        return KnowPost.builder()
                .id(100L)
                .creatorId(creatorId)
                .status("published")
                .visible("public")
                .build();
    }
}
