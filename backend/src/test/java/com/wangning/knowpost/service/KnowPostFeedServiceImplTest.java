package com.wangning.knowpost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.cache.model.FeedItemSnapshot;
import com.wangning.cache.model.FeedPageSnapshot;
import com.wangning.cache.service.KnowPostFeedCacheService;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.impl.KnowPostFeedServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class KnowPostFeedServiceImplTest {

    @Mock
    private KnowPostMapper knowPostMapper;

    @Mock
    private CounterService counterService;

    @Mock
    private KnowPostFeedCacheService knowPostFeedCacheService;

    private KnowPostFeedService knowPostFeedService;

    @BeforeEach
    void setUp() {
        knowPostFeedService = new KnowPostFeedServiceImpl(
                knowPostMapper,
                new ObjectMapper(),
                counterService,
                knowPostFeedCacheService
        );
        lenient().when(knowPostFeedCacheService.findPublic(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()
                ))
                .thenReturn(Optional.empty());
        lenient().when(knowPostFeedCacheService.findMine(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()
                ))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldQueryOneExtraRowAndReturnCompatibleFeedItem() {
        when(knowPostMapper.listFeedPublic(3, 2)).thenReturn(List.of(
                row(100L, "第一篇"),
                row(101L, "第二篇"),
                row(102L, "第三篇")
        ));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 5L, "fav", 2L));
        when(counterService.getCounts("knowpost", "101", List.of("like", "fav")))
                .thenReturn(Map.of("like", 1L, "fav", 0L));

        var response = knowPostFeedService.getPublicFeed(2, 2, null);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().id()).isEqualTo("100");
        assertThat(response.items().getFirst().coverImage())
                .isEqualTo("https://static.example.com/posts/100/images/a.png");
        assertThat(response.items().getFirst().tagJson()).isEqualTo("[\"Java\"]");
        assertThat(response.items().getFirst().likeCount()).isEqualTo(5L);
        assertThat(response.items().getFirst().liked()).isFalse();
        verify(knowPostMapper).listFeedPublic(3, 2);
        verify(counterService).getCounts("knowpost", "100", List.of("like", "fav"));
    }

    @Test
    void shouldClampPublicFeedPageAndSize() {
        when(knowPostMapper.listFeedPublic(51, 0)).thenReturn(List.of());

        var response = knowPostFeedService.getPublicFeed(0, 100, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.items()).isEmpty();
        verify(knowPostMapper).listFeedPublic(51, 0);
    }

    @Test
    void shouldQueryPublishedPostsForCurrentAuthor() {
        when(knowPostMapper.listMyPublished(1L, 21, 0)).thenReturn(List.of(row(100L, "我的知文")));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        var response = knowPostFeedService.getMyPublished(1L, 1, 20);

        assertThat(response.items()).extracting(item -> item.id()).containsExactly("100");
        assertThat(response.hasMore()).isFalse();
        verify(knowPostMapper).listMyPublished(1L, 21, 0);
    }

    @Test
    void shouldRejectAnonymousMineRequest() {
        assertThatThrownBy(() -> knowPostFeedService.getMyPublished(0L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void shouldUseSharedPublicFeedSnapshotAndKeepInteractionStatePerUser() {
        FeedPageSnapshot snapshot = new FeedPageSnapshot(
                List.of(new FeedItemSnapshot(
                        "100", "缓存标题", "缓存摘要", null, List.of("Java"),
                        null, "作者", "[]", false
                )),
                1,
                20,
                false
        );
        when(knowPostFeedCacheService.findPublic(1, 20)).thenReturn(Optional.of(snapshot));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 7L, "fav", 3L));
        when(counterService.isLiked("knowpost", "100", 2L)).thenReturn(true);

        var response = knowPostFeedService.getPublicFeed(1, 20, 2L);

        assertThat(response.items().getFirst().title()).isEqualTo("缓存标题");
        assertThat(response.items().getFirst().likeCount()).isEqualTo(7L);
        assertThat(response.items().getFirst().liked()).isTrue();
        verify(knowPostMapper, org.mockito.Mockito.never()).listFeedPublic(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    private KnowPostFeedRow row(long id, String title) {
        KnowPostFeedRow row = new KnowPostFeedRow();
        row.setId(id);
        row.setTitle(title);
        row.setDescription("摘要");
        row.setTags("[\"Java\",\"MyBatis\"]");
        row.setImgUrls("[\"https://static.example.com/posts/%d/images/a.png\"]".formatted(id));
        row.setAuthorAvatar("https://static.example.com/avatars/1.png");
        row.setAuthorNickname("作者");
        row.setAuthorTagJson("[\"Java\"]");
        row.setIsTop(false);
        return row;
    }
}
