package com.wangning.counter.service;

/**
 * 面向 HTTP 写操作的知文互动服务。
 *
 * <p>在切换 Redis 位图状态前校验知文是否存在、是否可互动以及操作者是否为作者。</p>
 */
public interface CounterActionService {

    /**
     * 点赞知文。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 当前用户 ID
     * @return 操作结果
     */
    CounterActionResult like(String entityType, String entityId, long userId);

    /**
     * 取消点赞知文。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 当前用户 ID
     * @return 操作结果
     */
    CounterActionResult unlike(String entityType, String entityId, long userId);

    /**
     * 收藏知文。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 当前用户 ID
     * @return 操作结果
     */
    CounterActionResult fav(String entityType, String entityId, long userId);

    /**
     * 取消收藏知文。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 当前用户 ID
     * @return 操作结果
     */
    CounterActionResult unfav(String entityType, String entityId, long userId);
}
