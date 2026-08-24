package com.wangning.knowpost.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.service.CounterService;
import com.wangning.knowpost.api.dto.FeedItemResponse;
import com.wangning.knowpost.api.dto.FeedPageResponse;
import com.wangning.knowpost.domain.KnowPostFeedRow;
import com.wangning.knowpost.mapper.KnowPostMapper;
import com.wangning.knowpost.service.KnowPostFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 沿用原项目分页方式的知文 Feed 查询服务。
 */
@Service
@RequiredArgsConstructor
public class KnowPostFeedServiceImpl implements KnowPostFeedService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String KNOWPOST = "knowpost";
    private static final List<String> COUNTER_METRICS = List.of("like", "fav");

    private final KnowPostMapper knowPostMapper;
    private final ObjectMapper objectMapper;
    private final CounterService counterService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserId) {
        PageRequest pageRequest = normalizePage(page, size);
        List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(
                pageRequest.size() + 1,
                pageRequest.offset()
        );
        return toPageResponse(rows, pageRequest, currentUserId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public FeedPageResponse getMyPublished(long creatorId, int page, int size) {
        if (creatorId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        PageRequest pageRequest = normalizePage(page, size);
        List<KnowPostFeedRow> rows = knowPostMapper.listMyPublished(
                creatorId,
                pageRequest.size() + 1,
                pageRequest.offset()
        );
        return toPageResponse(rows, pageRequest, creatorId);
    }

    /**
     * 规范化分页参数，保持旧项目的边界收敛行为。
     *
     * @param page 原始页码
     * @param size 原始页大小
     * @return 已规范化的分页参数
     */
    private PageRequest normalizePage(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return new PageRequest(safePage, safeSize, (safePage - 1) * safeSize);
    }

    /**
     * 将多查询的一条记录用于计算 hasMore，并转换为接口响应。
     *
     * @param rows Mapper 查询结果
     * @param pageRequest 分页参数
     * @return 分页响应
     */
    private FeedPageResponse toPageResponse(
            List<KnowPostFeedRow> rows,
            PageRequest pageRequest,
            Long currentUserId
    ) {
        List<KnowPostFeedRow> safeRows = rows == null ? Collections.emptyList() : rows;
        boolean hasMore = safeRows.size() > pageRequest.size();
        List<KnowPostFeedRow> currentPage = hasMore
                ? safeRows.subList(0, pageRequest.size())
                : safeRows;
        List<FeedItemResponse> items = currentPage.stream()
                .map(row -> toItemResponse(row, currentUserId))
                .toList();
        return new FeedPageResponse(items, pageRequest.page(), pageRequest.size(), hasMore);
    }

    /**
     * 转换单条 Feed 行。
     *
     * @param row Mapper 查询行
     * @return 前端兼容的 Feed 响应
     */
    private FeedItemResponse toItemResponse(KnowPostFeedRow row, Long currentUserId) {
        List<String> images = parseStringArray(row.getImgUrls());
        String coverImage = images.isEmpty() ? null : images.getFirst();
        String entityId = String.valueOf(row.getId());
        Map<String, Long> counts = counterService.getCounts(KNOWPOST, entityId, COUNTER_METRICS);
        boolean liked = currentUserId != null && counterService.isLiked(KNOWPOST, entityId, currentUserId);
        boolean faved = currentUserId != null && counterService.isFaved(KNOWPOST, entityId, currentUserId);
        return new FeedItemResponse(
                entityId,
                row.getTitle(),
                row.getDescription(),
                coverImage,
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

    /**
     * 解析数据库中的字符串数组 JSON。历史数据异常时返回空列表，避免影响整个 Feed。
     *
     * @param json JSON 数组文本
     * @return 字符串列表
     */
    private List<String> parseStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? Collections.emptyList() : values;
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }

    /**
     * 已规范化的分页参数。
     *
     * @param page 页码
     * @param size 页大小
     * @param offset SQL 偏移量
     */
    private record PageRequest(int page, int size, int offset) {
    }
}
