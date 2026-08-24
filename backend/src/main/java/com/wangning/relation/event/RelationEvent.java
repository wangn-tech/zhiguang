package com.wangning.relation.event;

/**
 * 写入 Outbox 的用户关系变更事件。
 *
 * @param type 事件类型，例如 {@code FollowCreated} 或 {@code FollowCanceled}
 * @param fromUserId 关注发起者用户 ID
 * @param toUserId 被关注者用户 ID
 * @param relationId 正向关系记录 ID
 */
public record RelationEvent(
        String type,
        long fromUserId,
        long toUserId,
        long relationId
) {
}
