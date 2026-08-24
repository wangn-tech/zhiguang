package com.wangning.counter.service;

/**
 * 内容点赞和收藏状态服务。
 */
public interface CounterService {

    /**
     * 为内容点赞。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 操作用户 ID
     * @return 状态由未点赞变为已点赞时返回 {@code true}
     */
    boolean like(String entityType, String entityId, long userId);

    /**
     * 取消内容点赞。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 操作用户 ID
     * @return 状态由已点赞变为未点赞时返回 {@code true}
     */
    boolean unlike(String entityType, String entityId, long userId);

    /**
     * 收藏内容。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 操作用户 ID
     * @return 状态由未收藏变为已收藏时返回 {@code true}
     */
    boolean fav(String entityType, String entityId, long userId);

    /**
     * 取消内容收藏。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 操作用户 ID
     * @return 状态由已收藏变为未收藏时返回 {@code true}
     */
    boolean unfav(String entityType, String entityId, long userId);

    /**
     * 判断用户是否已点赞内容。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 用户 ID
     * @return 已点赞时返回 {@code true}
     */
    boolean isLiked(String entityType, String entityId, long userId);

    /**
     * 判断用户是否已收藏内容。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 用户 ID
     * @return 已收藏时返回 {@code true}
     */
    boolean isFaved(String entityType, String entityId, long userId);
}
