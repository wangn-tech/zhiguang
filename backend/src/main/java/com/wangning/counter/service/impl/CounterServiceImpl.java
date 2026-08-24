package com.wangning.counter.service.impl;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import com.wangning.counter.schema.BitmapShard;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.counter.schema.CounterMetric;
import com.wangning.counter.event.CounterEvent;
import com.wangning.counter.event.CounterEventPublisher;
import com.wangning.counter.service.CounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Redis 分片位图的互动状态服务实现。
 *
 * <p>Lua 在 Redis 内原子读取并写入单个位，只有状态实际变化时才返回成功。
 * 每次实际变化都会发布一条 Kafka 计数事件，由聚合器异步更新实体计数 SDS。</p>
 */
@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    private static final String KNOWPOST = "knowpost";
    private static final int SDS_FIELD_SIZE = 4;
    private static final int SDS_FIELD_COUNT = 5;
    private static final RedisScript<Long> TOGGLE_SCRIPT = RedisScript.of("""
            local current = redis.call('GETBIT', KEYS[1], ARGV[1])
            local expected = tonumber(ARGV[2])
            if current ~= expected then
                return 0
            end
            redis.call('SETBIT', KEYS[1], ARGV[1], ARGV[3])
            return 1
            """, Long.class);
    private static final RedisScript<List> READ_COUNTS_SCRIPT = RedisScript.of("""
            local fieldSize = tonumber(ARGV[1])
            local fieldCount = tonumber(ARGV[2])
            local value = redis.call('GET', KEYS[1])
            if not value then
                value = string.rep(string.char(0), fieldSize * fieldCount)
            end

            local function read32be(source, offset)
                local b1, b2, b3, b4 = string.byte(source, offset + 1, offset + 4)
                return ((b1 or 0) * 16777216) + ((b2 or 0) * 65536) + ((b3 or 0) * 256) + (b4 or 0)
            end

            local result = {}
            for index = 3, #ARGV do
                result[#result + 1] = read32be(value, tonumber(ARGV[index]) * fieldSize)
            end
            return result
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final CounterEventPublisher eventPublisher;

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
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        validateEntity(entityType, entityId, 1L);
        if (metrics == null || metrics.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少指定一个计数指标");
        }

        List<CounterMetric> counterMetrics = metrics.stream().map(this::parseMetric).toList();
        List<String> arguments = new ArrayList<>();
        arguments.add(String.valueOf(SDS_FIELD_SIZE));
        arguments.add(String.valueOf(SDS_FIELD_COUNT));
        counterMetrics.forEach(metric -> arguments.add(String.valueOf(metric.index())));
        List<?> values = redisTemplate.execute(
                READ_COUNTS_SCRIPT,
                List.of(CounterKeys.sdsKey(entityType, entityId)),
                arguments.toArray()
        );

        Map<String, Long> counts = new LinkedHashMap<>();
        for (int index = 0; index < counterMetrics.size(); index++) {
            Object value = values != null && index < values.size() ? values.get(index) : 0L;
            counts.put(counterMetrics.get(index).value(), ((Number) value).longValue());
        }
        return counts;
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
        boolean stateChanged = Objects.equals(changed, 1L);
        if (stateChanged) {
            eventPublisher.publish(CounterEvent.of(
                    entityType,
                    entityId,
                    metric,
                    userId,
                    set ? 1 : -1
            ));
        }
        return stateChanged;
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

    private CounterMetric parseMetric(String metric) {
        if (CounterMetric.LIKE.value().equals(metric)) {
            return CounterMetric.LIKE;
        }
        if (CounterMetric.FAV.value().equals(metric)) {
            return CounterMetric.FAV;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的计数指标: " + metric);
    }
}
