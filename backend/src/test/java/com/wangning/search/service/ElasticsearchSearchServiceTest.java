package com.wangning.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.search.config.SearchProperties;
import com.wangning.search.index.KnowPostSearchDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class ElasticsearchSearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private CounterService counterService;

    @Test
    void shouldReturnFrontEndCompatibleItemsAndCursor() throws Exception {
        SearchResponse<KnowPostSearchDocument> esResponse = mock(SearchResponse.class);
        HitsMetadata<KnowPostSearchDocument> metadata = mock(HitsMetadata.class);
        Hit<KnowPostSearchDocument> firstHit = hit(100L);
        Hit<KnowPostSearchDocument> secondHit = mock(Hit.class);
        when(esResponse.hits()).thenReturn(metadata);
        when(metadata.hits()).thenReturn(List.of(firstHit, secondHit));
        when(elasticsearchClient.search(anySearchRequest(), eq(KnowPostSearchDocument.class))).thenReturn(esResponse);
        when(counterService.getCounts("knowpost", "100", List.of("like", "fav")))
                .thenReturn(Map.of("like", 8L, "fav", 3L));
        when(counterService.isLiked("knowpost", "100", 7L)).thenReturn(true);
        when(counterService.isFaved("knowpost", "100", 7L)).thenReturn(false);
        ElasticsearchSearchService service = service();

        var response = service.search("Java", 1, "Java, MyBatis", null, 7L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("100");
        assertThat(response.items().getFirst().description()).isEqualTo("Java 搜索摘要");
        assertThat(response.items().getFirst().likeCount()).isEqualTo(8L);
        assertThat(response.items().getFirst().liked()).isTrue();
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextAfter()).isNotBlank();
        verify(counterService).getCounts("knowpost", "100", List.of("like", "fav"));
    }

    @Test
    void shouldRejectMalformedCursor() {
        ElasticsearchSearchService service = service();

        assertThatThrownBy(() -> service.search("Java", 20, null, "not-a-cursor", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void shouldExposeElasticsearchFailureAsServiceUnavailable() throws Exception {
        when(elasticsearchClient.search(anySearchRequest(), eq(KnowPostSearchDocument.class)))
                .thenThrow(new IOException("offline"));
        ElasticsearchSearchService service = service();

        assertThatThrownBy(() -> service.search("Java", 20, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_UNAVAILABLE);
    }

    private Function anySearchRequest() {
        return any(Function.class);
    }

    private ElasticsearchSearchService service() {
        return new ElasticsearchSearchService(
                elasticsearchClient,
                new SearchProperties(),
                counterService,
                new ObjectMapper()
        );
    }

    private Hit<KnowPostSearchDocument> hit(long id) {
        Hit<KnowPostSearchDocument> hit = mock(Hit.class);
        when(hit.source()).thenReturn(new KnowPostSearchDocument(
                id,
                "Java 标题",
                "Java 搜索摘要",
                "完整正文",
                List.of("Java", "MyBatis"),
                1L,
                "https://static.example.com/avatar.png",
                "作者",
                "[\"Java\"]",
                List.of("https://static.example.com/cover.png"),
                false,
                Instant.parse("2026-08-24T00:00:00Z"),
                "published",
                "Java 标题"
        ));
        when(hit.sort()).thenReturn(List.of(FieldValue.of(1.2d), FieldValue.of(1_724_457_600_000L), FieldValue.of(id)));
        return hit;
    }
}
