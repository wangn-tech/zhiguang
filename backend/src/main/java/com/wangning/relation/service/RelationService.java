package com.wangning.relation.service;

/**
 * 用户关系核心服务。
 */
public interface RelationService {

    /**
     * 关注目标用户。
     *
     * @param fromUserId 关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @return 新建或恢复关系时返回 {@code true}，已关注时返回 {@code false}
     */
    boolean follow(long fromUserId, long toUserId);

    /**
     * 取消关注目标用户。
     *
     * @param fromUserId 取消关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @return 成功取消有效关系时返回 {@code true}，未关注时返回 {@code false}
     */
    boolean unfollow(long fromUserId, long toUserId);

    /**
     * 查询当前用户与目标用户的双向关系状态。
     *
     * @param userId 当前用户 ID
     * @param otherUserId 目标用户 ID
     * @return 双向关系状态
     */
    RelationStatus getStatus(long userId, long otherUserId);
}
