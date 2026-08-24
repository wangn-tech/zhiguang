package com.wangning.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.api.dto.FeedItemResponse;
import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;
import com.wangning.search.config.SearchProperties;
import com.wangning.search.index.KnowPostSearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Elasticsearch 的公开知文搜索服务。
 *
 * <p>关键词召回和高亮由 Elasticsearch 完成；点赞、收藏和当前用户互动状态则实时从 Redis 读取，
 * 不在 ES 文档中复制会持续变化的计数数据。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search", name = "enabled", havingValue = "true")
public class ElasticsearchSearchService implements SearchService {

    private static final String KNOWPOST = "knowpost";
    private static final List<String> COUNTER_METRICS = List.of("like", "fav");

    private final ElasticsearchClient elasticsearchClient;
    private final SearchProperties properties;
    private final CounterService counterService;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchResponse search(String keyword, int size, String tagsCsv, String after, Long currentUserId) {
        List<String> tags = parseTags(tagsCsv);
        SearchAfterCursor cursor = decodeCursor(after);
        List<SortOptions> sorts = searchSorts();

        co.elastic.clients.elasticsearch.core.SearchResponse<KnowPostSearchDocument> response;
        try {
            response = elasticsearchClient.search(request -> {
                request.index(properties.getIndexAlias())
                        .size(size + 1)
                        .query(query -> query.bool(bool -> {
                            bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                                    .query(keyword)
                                    .fields("title^3", "description^2", "body")
                            ));
                            bool.filter(filter -> filter.term(term -> term
                                    .field("status")
                                    .value(value -> value.stringValue("published"))
                            ));
                            if (!tags.isEmpty()) {
                                bool.filter(filter -> filter.terms(terms -> terms
                                        .field("tags")
                                        .terms(values -> values.value(tags.stream().map(FieldValue::of).toList()))
                                ));
                            }
                            return bool;
                        }))
                        .highlight(highlight -> highlight
                                .fields(new NamedValue<>("title", new HighlightField.Builder().build()))
                                .fields(new NamedValue<>("description", new HighlightField.Builder().build()))
                                .fields(new NamedValue<>("body", new HighlightField.Builder().build()))
                        )
                        .sort(sorts);
                if (cursor != null) {
                    request.searchAfter(cursor.toFieldValues());
                }
                return request;
            }, KnowPostSearchDocument.class);
        } catch (IOException | RuntimeException exception) {
            throw unavailable(exception);
        }

        List<Hit<KnowPostSearchDocument>> hits = response.hits() == null || response.hits().hits() == null
                ? Collections.emptyList()
                : response.hits().hits();
        boolean hasMore = hits.size() > size;
        List<Hit<KnowPostSearchDocument>> pageHits = hasMore ? hits.subList(0, size) : hits;
        List<FeedItemResponse> items = pageHits.stream()
                .map(hit -> toFeedItem(hit, currentUserId))
                .toList();
        String nextAfter = hasMore && !pageHits.isEmpty() ? encodeCursor(pageHits.getLast()) : null;
        return new SearchResponse(items, nextAfter, hasMore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SuggestResponse suggest(String prefix, int size) {
        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<KnowPostSearchDocument> response = elasticsearchClient.search(
                    request -> request.index(properties.getIndexAlias())
                            .suggest(suggest -> suggest.suggesters("titleSuggest", suggester -> suggester
                                    .prefix(prefix)
                                    .completion(completion -> completion.field("titleSuggest").size(size))
                            )),
                    KnowPostSearchDocument.class
            );
            return new SuggestResponse(extractSuggestions(response));
        } catch (IOException | RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    /**
     * 生成相关性、发布时间、ID 组成的稳定排序，供 {@code search_after} 使用。
     *
     * @return 排序规则
     */
    private List<SortOptions> searchSorts() {
        return List.of(
                SortOptions.of(sort -> sort.score(score -> score.order(SortOrder.Desc))),
                SortOptions.of(sort -> sort.field(field -> field.field("publishTime").order(SortOrder.Desc))),
                SortOptions.of(sort -> sort.field(field -> field.field("id").order(SortOrder.Desc)))
        );
    }

    /**
     * 将一个 ES 命中转换为前端兼容的 Feed 项，并补齐实时互动数据。
     *
     * @param hit Elasticsearch 命中
     * @param currentUserId 当前用户 ID；匿名时为 {@code null}
     * @return Feed 项
     */
    private FeedItemResponse toFeedItem(Hit<KnowPostSearchDocument> hit, Long currentUserId) {
        KnowPostSearchDocument document = hit.source();
        if (document == null || document.id() <= 0) {
            throw new IllegalStateException("搜索索引命中缺少知文数据");
        }
        String entityId = String.valueOf(document.id());
        Map<String, Long> counts = counterService.getCounts(KNOWPOST, entityId, COUNTER_METRICS);
        boolean liked = currentUserId != null && counterService.isLiked(KNOWPOST, entityId, currentUserId);
        boolean faved = currentUserId != null && counterService.isFaved(KNOWPOST, entityId, currentUserId);
        List<String> images = document.imgUrls() == null ? Collections.emptyList() : document.imgUrls();
        return new FeedItemResponse(
                entityId,
                document.title(),
                highlightedSnippet(hit, document.description()),
                images.isEmpty() ? null : images.getFirst(),
                document.tags() == null ? Collections.emptyList() : document.tags(),
                document.authorAvatar(),
                document.authorNickname(),
                document.authorTagJson(),
                counts.getOrDefault("like", 0L),
                counts.getOrDefault("fav", 0L),
                liked,
                faved,
                document.isTop()
        );
    }

    /**
     * 构造当前页最后一项的翻页游标。
     *
     * @param hit 当前页最后一个 ES 命中
     * @return Base64URL 编码游标
     */
    private String encodeCursor(Hit<KnowPostSearchDocument> hit) {
        List<FieldValue> sortValues = hit.sort();
        if (sortValues == null || sortValues.size() != 3) {
            throw new IllegalStateException("搜索索引未返回完整排序值");
        }
        SearchAfterCursor cursor = new SearchAfterCursor(
                fieldValueAsDouble(sortValues.get(0)),
                fieldValueAsLong(sortValues.get(1)),
                fieldValueAsLong(sortValues.get(2))
        );
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("搜索游标序列化失败", exception);
        }
    }

    /**
     * 解码并校验客户端提供的翻页游标。
     *
     * @param encodedCursor Base64URL 编码游标，可为空
     * @return 游标；首次查询时为 {@code null}
     */
    private SearchAfterCursor decodeCursor(String encodedCursor) {
        if (!StringUtils.hasText(encodedCursor)) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor);
            SearchAfterCursor cursor = objectMapper.readValue(json, SearchAfterCursor.class);
            if (!Double.isFinite(cursor.score()) || cursor.publishTime() < 0 || cursor.id() <= 0) {
                throw new IllegalArgumentException("invalid cursor fields");
            }
            return cursor;
        } catch (IllegalArgumentException | IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索游标无效");
        }
    }

    /**
     * 解析可选标签过滤条件。
     *
     * @param tagsCsv 逗号分隔标签
     * @return 去重后的标签列表
     */
    private List<String> parseTags(String tagsCsv) {
        if (!StringUtils.hasText(tagsCsv)) {
            return Collections.emptyList();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String rawTag : tagsCsv.split(",")) {
            String tag = rawTag.trim();
            if (StringUtils.hasText(tag)) {
                tags.add(tag);
            }
        }
        if (tags.size() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索标签最多 20 项");
        }
        return List.copyOf(tags);
    }

    /**
     * 取得前端可直接按纯文本渲染的高亮摘要。
     *
     * @param hit ES 命中
     * @param fallback 高亮不可用时的索引摘要
     * @return 摘要或高亮片段
     */
    private String highlightedSnippet(Hit<KnowPostSearchDocument> hit, String fallback) {
        if (hit.highlight() == null) {
            return fallback;
        }
        for (String field : List.of("title", "description", "body")) {
            List<String> fragments = hit.highlight().get(field);
            if (fragments != null && !fragments.isEmpty()) {
                return String.join(" ", fragments)
                        .replace("<em>", "")
                        .replace("</em>", "");
            }
        }
        return fallback;
    }

    /**
     * 提取并去重 Completion Suggester 的候选标题。
     *
     * @param response Elasticsearch 搜索响应
     * @return 候选标题列表
     */
    private List<String> extractSuggestions(
            co.elastic.clients.elasticsearch.core.SearchResponse<KnowPostSearchDocument> response
    ) {
        if (response.suggest() == null) {
            return Collections.emptyList();
        }
        List<Suggestion<KnowPostSearchDocument>> suggestions = response.suggest().get("titleSuggest");
        if (suggestions == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (Suggestion<KnowPostSearchDocument> suggestion : suggestions) {
            if (suggestion.completion() == null || suggestion.completion().options() == null) {
                continue;
            }
            suggestion.completion().options().forEach(option -> {
                if (StringUtils.hasText(option.text())) {
                    titles.add(option.text());
                }
            });
        }
        return List.copyOf(titles);
    }

    private double fieldValueAsDouble(FieldValue value) {
        if (value.isDouble()) {
            return value.doubleValue();
        }
        if (value.isLong()) {
            return value.longValue();
        }
        throw new IllegalStateException("搜索排序分数类型异常");
    }

    private long fieldValueAsLong(FieldValue value) {
        if (value.isLong()) {
            return value.longValue();
        }
        if (value.isDouble()) {
            return (long) value.doubleValue();
        }
        throw new IllegalStateException("搜索排序字段类型异常");
    }

    private BusinessException unavailable(Exception exception) {
        return new BusinessException(ErrorCode.SEARCH_UNAVAILABLE, "搜索服务暂时不可用");
    }

    /**
     * Base64URL JSON 游标的稳定载荷。
     *
     * @param score 最后命中的相关性分数
     * @param publishTime 最后命中的发布时间毫秒值
     * @param id 最后命中的知文 ID
     */
    private record SearchAfterCursor(double score, long publishTime, long id) {

        private List<FieldValue> toFieldValues() {
            return List.of(FieldValue.of(score), FieldValue.of(publishTime), FieldValue.of(id));
        }
    }
}
