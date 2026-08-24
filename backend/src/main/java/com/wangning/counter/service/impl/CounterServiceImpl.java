package com.wangning.counter.service.impl;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.schema.BitmapShard;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.counter.schema.CounterMetric;
import com.wangning.counter.service.CounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 分片位图的互动状态服务实现。
 *
 * <p>Lua 在 Redis 内原子读取并写入单个位，只有状态实际变化时才返回成功。
 * 后续 Kafka 聚合器只需消费这些成功变化产生的事件，即可保证点赞和收藏操作幂等。</p>
 */
@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    private static final String KNOWPOST = "knowpost";
    private static final RedisScript<Long> TOGGLE_SCRIPT = RedisScript.of("""
            local current = redis.call('GETBIT', KEYS[1], ARGV[1])
            local expected = tonumber(ARGV[2])
            if current ~= expected then
                return 0
            end
            redis.call('SETBIT', KEYS[1], ARGV[1], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** {@inheritDoc} */
    @Override
    public boolean like(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, CounterMetric.LIKE, true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, CounterMetric.LIKE, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, CounterMetric.FAV, true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, CounterMetric.FAV, false);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLiked(String entityType, String entityId, long userId) {
        return isSet(entityType, entityId, userId, CounterMetric.LIKE);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFaved(String entityType, String entityId, long userId) {
        return isSet(entityType, entityId, userId, CounterMetric.FAV);
    }

    private boolean toggle(
            String entityType,
            String entityId,
            long userId,
            CounterMetric metric,
            boolean set
    ) {
        String key = bitmapKey(entityType, entityId, userId, metric);
        Long changed = redisTemplate.execute(
                TOGGLE_SCRIPT,
                List.of(key),
                String.valueOf(BitmapShard.bitOf(userId)),
                set ? "0" : "1",
                set ? "1" : "0"
        );
        return Objects.equals(changed, 1L);
    }

    private boolean isSet(String entityType, String entityId, long userId, CounterMetric metric) {
        String key = bitmapKey(entityType, entityId, userId, metric);
        Boolean value = redisTemplate.opsForValue().getBit(key, BitmapShard.bitOf(userId));
        return Boolean.TRUE.equals(value);
    }

    private String bitmapKey(String entityType, String entityId, long userId, CounterMetric metric) {
        validateEntity(entityType, entityId, userId);
        return CounterKeys.bitmapKey(metric, entityType, entityId, BitmapShard.chunkOf(userId));
    }

    private void validateEntity(String entityType, String entityId, long userId) {
        if (!KNOWPOST.equals(entityType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持知文互动");
        }
        if (!StringUtils.hasText(entityId) || !entityId.matches("[1-9]\\d*")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实体 ID 必须为正整数");
        }
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户 ID 必须为正整数");
        }
    }
}
