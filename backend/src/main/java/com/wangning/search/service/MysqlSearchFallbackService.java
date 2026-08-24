package com.wangning.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.api.dto.FeedItemResponse;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.search.api.dto.SearchResponse;
import com.wangning.search.api.dto.SuggestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elasticsearch 查询异常时的 MySQL 有限降级搜索。
 *
 * <p>仅检索 MySQL 中的标题、摘要和标签，不能检索存放于 OSS 的正文，也不提供分词相关性排序。
 * 单页至多读取 20 项，避免降级期间对主库造成过高压力。</p>
 */
@Service
@RequiredArgsConstructor
public class MysqlSearchFallbackService {

    private static final int MAX_PAGE_SIZE = 20;
    private static final String KNOWPOST = "knowpost";
    private static final List<String> COUNTER_METRICS = List.of("like", "fav");

    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final ObjectMapper objectMapper;
    private final SearchCursorCodec cursorCodec;

    /**
     * 以 MySQL 执行有限的公开知文检索。
     *
     * @param keyword 搜索关键词
     * @param requestedSize 前端请求数量
     * @param tagsCsv 可选逗号分隔标签
     * @param after MySQL 数据源游标，可为空
     * @param currentUserId 当前用户 ID；匿名时为 {@code null}
     * @return 前端兼容的搜索结果页
     */
    public SearchResponse search(String keyword, int requestedSize, String tagsCsv, String after, Long currentUserId) {
        List<String> tags = parseTags(tagsCsv);
        SearchCursorCodec.MysqlCursor cursor = StringUtils.hasText(after) ? cursorCodec.decodeMysql(after) : null;
        int pageSize = Math.min(requestedSize, MAX_PAGE_SIZE);
        List<KnowPostFeedRow> rows = knowPostMapper.searchPublicFallback(
                keyword,
                tags.isEmpty() ? null : serializeTags(tags),
                cursor == null ? null : cursor.isTop(),
                cursor == null ? null : Instant.ofEpochMilli(cursor.publishTime()),
                cursor == null ? null : cursor.id(),
                pageSize + 1
        );
        List<KnowPostFeedRow> safeRows = rows == null ? Collections.emptyList() : rows;
        boolean hasMore = safeRows.size() > pageSize;
        List<KnowPostFeedRow> pageRows = hasMore ? safeRows.subList(0, pageSize) : safeRows;
        List<FeedItemResponse> items = pageRows.stream()
                .map(row -> toFeedItem(row, currentUserId))
                .toList();
        String nextAfter = hasMore && !pageRows.isEmpty() ? encodeCursor(pageRows.getLast()) : null;
        return new SearchResponse(items, nextAfter, hasMore);
    }

    /**
     * 查询 MySQL 中的标题前缀联想。
     *
     * @param prefix 标题前缀
     * @param requestedSize 前端请求数量
     * @return 去重后的标题列表
     */
    public SuggestResponse suggest(String prefix, int requestedSize) {
        int limit = Math.min(requestedSize, MAX_PAGE_SIZE);
        List<String> titles = knowPostMapper.listPublicTitleSuggestionsFallback(prefix, limit);
        if (titles == null) {
            return new SuggestResponse(Collections.emptyList());
        }
        return new SuggestResponse(titles.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList());
    }

    private FeedItemResponse toFeedItem(KnowPostFeedRow row, Long currentUserId) {
        String entityId = String.valueOf(row.getId());
        Map<String, Long> counts = counterService.getCounts(KNOWPOST, entityId, COUNTER_METRICS);
        boolean liked = currentUserId != null && counterService.isLiked(KNOWPOST, entityId, currentUserId);
        boolean faved = currentUserId != null && counterService.isFaved(KNOWPOST, entityId, currentUserId);
        List<String> images = parseStringArray(row.getImgUrls());
        return new FeedItemResponse(
                entityId,
                row.getTitle(),
                row.getDescription(),
                images.isEmpty() ? null : images.getFirst(),
                parseStringArray(row.getTags()),
                row.getAuthorAvatar(),
                row.getAuthorNickname(),
                row.getAuthorTagJson(),
                counts.getOrDefault("like", 0L),
                counts.getOrDefault("fav", 0L),
                liked,
                faved,
                row.getIsTop()
        );
    }

    private String encodeCursor(KnowPostFeedRow row) {
        if (row.getId() == null || row.getPublishTime() == null || row.getIsTop() == null) {
            throw new IllegalStateException("MySQL 搜索结果缺少翻页字段");
        }
        return cursorCodec.encodeMysql(row.getIsTop(), row.getPublishTime().toEpochMilli(), row.getId());
    }

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

    private String serializeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("搜索标签序列化失败", exception);
        }
    }

    private List<String> parseStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? Collections.emptyList() : List.copyOf(values);
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }
}
