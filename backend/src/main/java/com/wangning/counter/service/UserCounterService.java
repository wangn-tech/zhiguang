package com.wangning.counter.service;

/**
 * 用户维度 SDS 计数服务。
 */
public interface UserCounterService {

    /**
     * 增量更新用户关注数。
     *
     * @param userId 用户 ID
     * @param delta 增量
     */
    void incrementFollowings(long userId, int delta);

    /**
     * 增量更新用户粉丝数。
     *
     * @param userId 用户 ID
     * @param delta 增量
     */
    void incrementFollowers(long userId, int delta);

    /**
     * 增量更新用户已发布知文数。
     *
     * @param userId 用户 ID
     * @param delta 增量
     */
    void incrementPosts(long userId, int delta);

    /**
     * 增量更新用户获赞数。
     *
     * @param userId 用户 ID
     * @param delta 增量
     */
    void incrementLikesReceived(long userId, int delta);

    /**
     * 增量更新用户获收藏数。
     *
     * @param userId 用户 ID
     * @param delta 增量
     */
    void incrementFavsReceived(long userId, int delta);

    /**
     * 读取用户计数快照。不存在的 SDS 按全零处理。
     *
     * @param userId 用户 ID
     * @return 用户计数快照
     */
    UserCounters getCounters(long userId);

    /**
     * 判断用户计数 SDS 是否已经完成过事实数据回填且结构有效。
     *
     * @param userId 用户 ID
     * @return 可安全接收增量事件时返回 {@code true}
     */
    boolean isInitialized(long userId);

    /**
     * 获取用户计数；若 Redis SDS 缺失或损坏，则由 MySQL 和实体计数 SDS 回填。
     *
     * @param userId 用户 ID
     * @return 可用于主页展示的用户计数快照
     */
    UserCounters getOrRebuildCounters(long userId);

    /**
     * 强制从关系表、已发布知文和实体计数 SDS 重建用户计数。
     *
     * @param userId 用户 ID
     * @return 重建后的用户计数快照
     */
    UserCounters rebuildCounters(long userId);
}
