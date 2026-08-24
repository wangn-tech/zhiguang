package com.wangning.counter.service;

import java.util.List;
import java.util.Map;

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
     * 读取指定实体的互动计数。
     *
     * <p>计数由 Kafka 消费链路异步聚合，因此与刚完成的互动状态切换存在短暂延迟。</p>
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param metrics 需要读取的指标，只支持 {@code like}、{@code fav}
     * @return 指标名到计数值的映射
     */
    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);

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
