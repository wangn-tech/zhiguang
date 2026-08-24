package com.wangning.relation.service;

import com.wangning.relation.api.dto.PublicProfileResponse;
import com.wangning.relation.api.dto.RelationCountersResponse;

import java.util.List;

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

    /**
     * 查询用户关注的公开资料列表。
     *
     * @param userId 被查询用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量；仅在 {@code cursor} 为空时生效
     * @param cursor 旧前端传入的毫秒时间游标，可为空
     * @return 按最近关注时间倒序的公开资料
     */
    List<PublicProfileResponse> listFollowings(long userId, int limit, int offset, Long cursor);

    /**
     * 查询用户粉丝的公开资料列表。
     *
     * @param userId 被查询用户 ID
     * @param limit 最大返回数量
     * @param offset 偏移量；仅在 {@code cursor} 为空时生效
     * @param cursor 旧前端传入的毫秒时间游标，可为空
     * @return 按最近关注时间倒序的公开资料
     */
    List<PublicProfileResponse> listFollowers(long userId, int limit, int offset, Long cursor);

    /**
     * 查询用户主页所需的关系计数。
     *
     * @param userId 被查询用户 ID
     * @return 关系计数；尚未实现的计数项为 0
     */
    RelationCountersResponse getCounters(long userId);
}
