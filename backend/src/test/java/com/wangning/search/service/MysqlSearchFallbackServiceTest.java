package com.wangning.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MysqlSearchFallbackServiceTest {

    @Mock
    private KnowPostMapper knowPostMapper;

    @Mock
    private CounterService counterService;

    @Test
    void shouldReturnMysqlCursorAndRealtimeCounterData() {
        when(knowPostMapper.searchPublicFallback(eq("Java"), eq("[\"Java\"]"), isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(row(100L, true), row(99L, false), row(98L, false)));
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 3L, "fav", 2L));
        when(counterService.getCounts("knowpost", "99", List.of("like", "fav")))
                .thenReturn(Map.of());
        MysqlSearchFallbackService service = service();

        var response = service.search("Java", 2, "Java", null, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().id()).isEqualTo("100");
        assertThat(response.items().getFirst().likeCount()).isEqualTo(3L);
        assertThat(response.hasMore()).isTrue();
        assertThat(new SearchCursorCodec(new ObjectMapper()).sourceOf(response.nextAfter()))
                .isEqualTo(SearchCursorCodec.MYSQL_SOURCE);
    }

    @Test
    void shouldDecodeMysqlCursorIntoKeysetArguments() {
        SearchCursorCodec codec = new SearchCursorCodec(new ObjectMapper());
        String cursor = codec.encodeMysql(true, 1_724_457_600_000L, 100L);
        MysqlSearchFallbackService service = service();

        service.search("Java", 20, null, cursor, null);

        ArgumentCaptor<Boolean> topCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(knowPostMapper).searchPublicFallback(
                eq("Java"),
                isNull(),
                topCaptor.capture(),
                timeCaptor.capture(),
                idCaptor.capture(),
                eq(21)
        );
        assertThat(topCaptor.getValue()).isTrue();
        assertThat(timeCaptor.getValue()).isEqualTo(Instant.ofEpochMilli(1_724_457_600_000L));
        assertThat(idCaptor.getValue()).isEqualTo(100L);
    }

    private MysqlSearchFallbackService service() {
        return new MysqlSearchFallbackService(
                knowPostMapper,
                counterService,
                new ObjectMapper(),
                new SearchCursorCodec(new ObjectMapper())
        );
    }

    private KnowPostFeedRow row(long id, boolean isTop) {
        KnowPostFeedRow row = new KnowPostFeedRow();
        row.setId(id);
        row.setTitle("Java 标题");
        row.setDescription("Java 摘要");
        row.setTags("[\"Java\"]");
        row.setImgUrls("[]");
        row.setAuthorNickname("作者");
        row.setAuthorTagJson("[]");
        row.setIsTop(isTop);
        row.setPublishTime(Instant.parse("2026-08-24T00:00:00Z"));
        return row;
    }
}
