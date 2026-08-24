package com.wangning.knowpost.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.cache.model.FeedItemSnapshot;
import com.wangning.cache.model.FeedPageSnapshot;
import com.wangning.cache.key.CacheKeys;
import com.wangning.cache.service.KnowPostFeedCacheService;
import com.wangning.cache.singleflight.CacheSingleFlight;
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
    private final KnowPostFeedCacheService knowPostFeedCacheService;
    private final CacheSingleFlight cacheSingleFlight;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserId) {
        PageRequest pageRequest = normalizePage(page, size);
        var cached = knowPostFeedCacheService.findPublic(pageRequest.page(), pageRequest.size());
        if (cached.isPresent()) {
            return enrichPage(cached.get(), currentUserId);
        }

        FeedPageSnapshot snapshot = cacheSingleFlight.execute(
                CacheKeys.publicFeedKey(pageRequest.page(), pageRequest.size()),
                () -> loadPublicSnapshot(pageRequest)
        );
        return enrichPage(snapshot, currentUserId);
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
        var cached = knowPostFeedCacheService.findMine(creatorId, pageRequest.page(), pageRequest.size());
        if (cached.isPresent()) {
            return enrichPage(cached.get(), creatorId);
        }

        FeedPageSnapshot snapshot = cacheSingleFlight.execute(
                CacheKeys.mineFeedKey(creatorId, pageRequest.page(), pageRequest.size()),
                () -> loadMineSnapshot(creatorId, pageRequest)
        );
        return enrichPage(snapshot, creatorId);
    }

    private FeedPageSnapshot loadPublicSnapshot(PageRequest pageRequest) {
        var cached = knowPostFeedCacheService.findPublic(pageRequest.page(), pageRequest.size());
        if (cached.isPresent()) {
            return cached.get();
        }
        List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(
                pageRequest.size() + 1,
                pageRequest.offset()
        );
        FeedPageSnapshot snapshot = toSnapshot(rows, pageRequest);
        knowPostFeedCacheService.putPublic(snapshot);
        return snapshot;
    }

    private FeedPageSnapshot loadMineSnapshot(long creatorId, PageRequest pageRequest) {
        var cached = knowPostFeedCacheService.findMine(creatorId, pageRequest.page(), pageRequest.size());
        if (cached.isPresent()) {
            return cached.get();
        }
        List<KnowPostFeedRow> rows = knowPostMapper.listMyPublished(
                creatorId,
                pageRequest.size() + 1,
                pageRequest.offset()
        );
        FeedPageSnapshot snapshot = toSnapshot(rows, pageRequest);
        knowPostFeedCacheService.putMine(creatorId, snapshot);
        return snapshot;
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
    private FeedPageSnapshot toSnapshot(
            List<KnowPostFeedRow> rows,
            PageRequest pageRequest
    ) {
        List<KnowPostFeedRow> safeRows = rows == null ? Collections.emptyList() : rows;
        boolean hasMore = safeRows.size() > pageRequest.size();
        List<KnowPostFeedRow> currentPage = hasMore
                ? safeRows.subList(0, pageRequest.size())
                : safeRows;
        List<FeedItemSnapshot> items = currentPage.stream()
                .map(this::toItemSnapshot)
                .toList();
        return new FeedPageSnapshot(items, pageRequest.page(), pageRequest.size(), hasMore);
    }

    /**
     * 转换单条 Feed 行。
     *
     * @param row Mapper 查询行
     * @return 前端兼容的 Feed 响应
     */
    private FeedItemSnapshot toItemSnapshot(KnowPostFeedRow row) {
        List<String> images = parseStringArray(row.getImgUrls());
        String coverImage = images.isEmpty() ? null : images.getFirst();
        return new FeedItemSnapshot(
                String.valueOf(row.getId()),
                row.getTitle(),
                row.getDescription(),
                coverImage,
                parseStringArray(row.getTags()),
                row.getAuthorAvatar(),
                row.getAuthorNickname(),
                row.getAuthorTagJson(),
                row.getIsTop()
        );
    }

    /**
     * 将共享 Feed 快照补齐为当前访问者的接口响应。
     *
     * @param snapshot 不含互动数据的 Feed 页面快照
     * @param currentUserId 当前登录用户 ID；匿名访问时为 {@code null}
     * @return 具有实时互动数据的 Feed 响应
     */
    private FeedPageResponse enrichPage(FeedPageSnapshot snapshot, Long currentUserId) {
        List<FeedItemResponse> items = snapshot.items().stream()
                .map(item -> enrichItem(item, currentUserId))
                .toList();
        return new FeedPageResponse(items, snapshot.page(), snapshot.size(), snapshot.hasMore());
    }

    /**
     * 为单条稳定 Feed 快照补齐实时互动数据。
     *
     * @param snapshot 不含互动数据的 Feed 项快照
     * @param currentUserId 当前登录用户 ID；匿名访问时为 {@code null}
     * @return 前端兼容的 Feed 响应项
     */
    private FeedItemResponse enrichItem(FeedItemSnapshot snapshot, Long currentUserId) {
        Map<String, Long> counts = counterService.getCounts(KNOWPOST, snapshot.id(), COUNTER_METRICS);
        boolean liked = currentUserId != null && counterService.isLiked(KNOWPOST, snapshot.id(), currentUserId);
        boolean faved = currentUserId != null && counterService.isFaved(KNOWPOST, snapshot.id(), currentUserId);
        return snapshot.toResponse(
                counts.getOrDefault("like", 0L),
                counts.getOrDefault("fav", 0L),
                liked,
                faved
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
