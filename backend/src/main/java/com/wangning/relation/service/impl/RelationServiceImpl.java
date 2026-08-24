package com.wangning.relation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.relation.domain.UserRelation;
import com.wangning.relation.event.RelationEvent;
import com.wangning.relation.mapper.RelationMapper;
import com.wangning.relation.outbox.OutboxMapper;
import com.wangning.relation.outbox.OutboxRecord;
import com.wangning.relation.service.RelationService;
import com.wangning.relation.service.RelationStatus;
import com.wangning.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户关系核心服务实现。
 *
 * <p>关注表更新和 Outbox 写入必须在同一 MySQL 事务内完成。事务提交后，Canal 才会从
 * binlog 中读取事件并转发给 Kafka；本类不直接发送 Kafka 消息。</p>
 */
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private static final String FOLLOW_CREATED = "FollowCreated";
    private static final String FOLLOW_CANCELED = "FollowCanceled";
    private static final String FOLLOWING_AGGREGATE = "following";

    private final RelationMapper relationMapper;
    private final OutboxMapper outboxMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public boolean follow(long fromUserId, long toUserId) {
        validateUserPair(fromUserId, toUserId);
        requireUserExists(toUserId);

        Instant now = Instant.now();
        UserRelation relation = UserRelation.builder()
                .id(nextId())
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        int affectedRows = relationMapper.insertFollowingIgnore(relation);
        if (affectedRows == 0) {
            affectedRows = relationMapper.reactivateFollowing(fromUserId, toUserId, now, now);
        }
        if (affectedRows == 0) {
            return false;
        }

        UserRelation stored = relationMapper.findFollowing(fromUserId, toUserId);
        if (stored == null || stored.getId() == null) {
            throw new IllegalStateException("关注关系写入后无法读取");
        }
        writeOutbox(FOLLOW_CREATED, fromUserId, toUserId, stored.getId(), now);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public boolean unfollow(long fromUserId, long toUserId) {
        validateUserPair(fromUserId, toUserId);
        requireUserExists(toUserId);

        Instant now = Instant.now();
        int affectedRows = relationMapper.deactivateFollowing(fromUserId, toUserId, now);
        if (affectedRows == 0) {
            return false;
        }

        UserRelation stored = relationMapper.findFollowing(fromUserId, toUserId);
        if (stored == null || stored.getId() == null) {
            throw new IllegalStateException("取消关注后无法读取关系");
        }
        writeOutbox(FOLLOW_CANCELED, fromUserId, toUserId, stored.getId(), now);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RelationStatus getStatus(long userId, long otherUserId) {
        validateUserPair(userId, otherUserId);
        boolean following = relationMapper.existsFollowing(userId, otherUserId);
        boolean followedBy = relationMapper.existsFollowing(otherUserId, userId);
        return new RelationStatus(following, followedBy, following && followedBy);
    }

    /**
     * 验证两个用户 ID 均为正数且不相同。
     *
     * @param fromUserId 发起操作的用户 ID
     * @param toUserId 目标用户 ID
     */
    private void validateUserPair(long fromUserId, long toUserId) {
        if (fromUserId <= 0 || toUserId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户 ID 必须为正整数");
        }
        if (fromUserId == toUserId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能关注自己");
        }
    }

    /**
     * 确认目标用户存在。
     *
     * @param userId 目标用户 ID
     */
    private void requireUserExists(long userId) {
        if (userService.findById(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标用户不存在");
        }
    }

    /**
     * 事务内保存一条 Outbox 事件。
     *
     * @param type 事件类型
     * @param fromUserId 关注发起者用户 ID
     * @param toUserId 被关注者用户 ID
     * @param relationId 正向关系 ID
     * @param occurredAt 事件发生时间
     */
    private void writeOutbox(
            String type,
            long fromUserId,
            long toUserId,
            long relationId,
            Instant occurredAt
    ) {
        String payload = serializeEvent(new RelationEvent(type, fromUserId, toUserId, relationId));
        int affectedRows = outboxMapper.insert(OutboxRecord.builder()
                .id(nextId())
                .aggregateType(FOLLOWING_AGGREGATE)
                .aggregateId(relationId)
                .type(type)
                .payload(payload)
                .createdAt(occurredAt)
                .build());
        if (affectedRows != 1) {
            throw new IllegalStateException("Outbox 事件写入失败");
        }
    }

    /**
     * 将事件序列化为 JSON。
     *
     * @param event 关系事件
     * @return JSON 字符串
     */
    private String serializeEvent(RelationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("关系事件序列化失败", exception);
        }
    }

    /**
     * 生成正的随机长整型 ID，沿用旧项目的关系与 Outbox ID 策略。
     *
     * @return 正整数 ID
     */
    private long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
