package com.wangning.knowpost.service;

import com.wangning.knowpost.api.dto.FeedPageResponse;

/**
 * 知文列表查询服务。
 */
public interface KnowPostFeedService {

    /**
     * 查询公开 Feed。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @param currentUserId 当前登录用户 ID；匿名访问时为 {@code null}
     * @return 分页结果
     */
    FeedPageResponse getPublicFeed(int page, int size, Long currentUserId);

    /**
     * 查询当前用户的已发布知文。
     *
     * @param creatorId 当前作者用户 ID
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 分页结果
     */
    FeedPageResponse getMyPublished(long creatorId, int page, int size);
}
