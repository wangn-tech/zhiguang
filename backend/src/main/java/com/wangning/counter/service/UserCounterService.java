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
}
