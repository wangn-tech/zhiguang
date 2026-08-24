package com.wangning.relation.processor;

import com.wangning.relation.config.RelationEventProperties;
import com.wangning.counter.service.UserCounterService;
import com.wangning.relation.domain.UserRelation;
import com.wangning.relation.event.RelationEvent;
import com.wangning.relation.mapper.RelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * 关系 Outbox 事件处理器。
 *
 * <p>关注正向表由写请求同步维护；本处理器异步维护反向粉丝表和 Redis 关系缓存。
 * 去重键使用 Outbox ID 而非关系 ID，因此取消后重新关注不会被误判为重复事件。</p>
 */
@Service
@RequiredArgsConstructor
public class RelationEventProcessor {

    private static final String FOLLOW_CREATED = "FollowCreated";
    private static final String FOLLOW_CANCELED = "FollowCanceled";
    private static final String DEDUP_KEY_PREFIX = "relation:event:";
    private static final String FOLLOWING_CACHE_PREFIX = "relation:following:";
    private static final String FOLLOWER_CACHE_PREFIX = "relation:follower:";

    private final RelationMapper relationMapper;
    private final StringRedisTemplate redisTemplate;
    private final RelationEventProperties properties;
    private final UserCounterService userCounterService;

    /**
     * 幂等处理一条已提交的 Outbox 关系事件。
     *
     * @param outboxId Outbox 主键
     * @param event 关系事件
     * @throws IllegalArgumentException 事件参数不合法或类型不支持时抛出
     */
    public void process(long outboxId, RelationEvent event) {
        validateEvent(outboxId, event);
        String dedupKey = DEDUP_KEY_PREFIX + outboxId;
        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(
                dedupKey,
                "1",
                properties.getDedupTtl()
        );
        if (!Boolean.TRUE.equals(firstDelivery)) {
            return;
        }

        try {
            if (FOLLOW_CREATED.equals(event.type())) {
                processFollowCreated(event);
            } else {
                processFollowCanceled(event);
            }
        } catch (RuntimeException exception) {
            // 当前处理未完成时移除去重键，使 Kafka 的重投能够再次执行该事件。
            redisTemplate.delete(dedupKey);
            throw exception;
        }
    }

    /**
     * 处理关注创建或恢复事件。
     *
     * @param event 关系事件
     */
    private void processFollowCreated(RelationEvent event) {
        Instant now = Instant.now();
        relationMapper.upsertFollower(UserRelation.builder()
                .id(event.relationId())
                .fromUserId(event.fromUserId())
                .toUserId(event.toUserId())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        redisTemplate.opsForZSet().add(
                followingCacheKey(event.fromUserId()),
                String.valueOf(event.toUserId()),
                now.toEpochMilli()
        );
        redisTemplate.opsForZSet().add(
                followerCacheKey(event.toUserId()),
                String.valueOf(event.fromUserId()),
                now.toEpochMilli()
        );
        refreshCacheTtl(event.fromUserId(), event.toUserId());
        if (userCounterService.isInitialized(event.fromUserId())) {
            userCounterService.incrementFollowings(event.fromUserId(), 1);
        }
        if (userCounterService.isInitialized(event.toUserId())) {
            userCounterService.incrementFollowers(event.toUserId(), 1);
        }
    }

    /**
     * 处理取消关注事件。
     *
     * @param event 关系事件
     */
    private void processFollowCanceled(RelationEvent event) {
        relationMapper.deactivateFollower(event.toUserId(), event.fromUserId(), Instant.now());
        redisTemplate.opsForZSet().remove(
                followingCacheKey(event.fromUserId()),
                String.valueOf(event.toUserId())
        );
        redisTemplate.opsForZSet().remove(
                followerCacheKey(event.toUserId()),
                String.valueOf(event.fromUserId())
        );
        refreshCacheTtl(event.fromUserId(), event.toUserId());
        if (userCounterService.isInitialized(event.fromUserId())) {
            userCounterService.incrementFollowings(event.fromUserId(), -1);
        }
        if (userCounterService.isInitialized(event.toUserId())) {
            userCounterService.incrementFollowers(event.toUserId(), -1);
        }
    }

    /**
     * 刷新两份关系缓存的过期时间。
     *
     * @param fromUserId 关注发起者 ID
     * @param toUserId 被关注者 ID
     */
    private void refreshCacheTtl(long fromUserId, long toUserId) {
        redisTemplate.expire(followingCacheKey(fromUserId), properties.getCacheTtl());
        redisTemplate.expire(followerCacheKey(toUserId), properties.getCacheTtl());
    }

    /**
     * 校验消息不可缺少的关联信息。
     *
     * @param outboxId Outbox 主键
     * @param event 关系事件
     */
    private void validateEvent(long outboxId, RelationEvent event) {
        if (outboxId <= 0 || event == null || event.fromUserId() <= 0 || event.toUserId() <= 0
                || event.fromUserId() == event.toUserId() || event.relationId() <= 0) {
            throw new IllegalArgumentException("无效的关系 Outbox 事件");
        }
        if (!Objects.equals(FOLLOW_CREATED, event.type()) && !Objects.equals(FOLLOW_CANCELED, event.type())) {
            throw new IllegalArgumentException("不支持的关系事件类型: " + event.type());
        }
    }

    private String followingCacheKey(long userId) {
        return FOLLOWING_CACHE_PREFIX + userId;
    }

    private String followerCacheKey(long userId) {
        return FOLLOWER_CACHE_PREFIX + userId;
    }
}
