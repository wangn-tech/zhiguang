package com.wangning.counter.service;

import com.wangning.counter.config.CounterRecoveryProperties;
import com.wangning.counter.schema.CounterKeys;
import com.wangning.counter.schema.CounterMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 实体互动计数 SDS 的按需恢复服务。
 *
 * <p>位图是互动状态的事实来源；SDS 缺失或长度异常时，本服务在 Redisson 锁内从位图重建 SDS。
 * 恢复脚本同时写入事件序号围栏并清理既有聚合桶，使恢复前的延迟 Kafka 事件不会再次折叠到
 * 已重建的计数中。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CounterRecoveryService {

    /** SDS 的字段宽度（字节）。 */
    public static final int SDS_FIELD_SIZE = 4;

    /** SDS 的字段数量。 */
    public static final int SDS_FIELD_COUNT = 5;

    /** SDS 的合法字节长度。 */
    public static final int SDS_LENGTH = SDS_FIELD_SIZE * SDS_FIELD_COUNT;

    private static final RedisScript<Long> RECOVER_SCRIPT = RedisScript.of("""
            local function countBits(indexKey)
                local total = 0
                local bitmapKeys = redis.call('SMEMBERS', indexKey)
                for _, bitmapKey in ipairs(bitmapKeys) do
                    total = total + redis.call('BITCOUNT', bitmapKey)
                end
                return total
            end

            local function write32be(number)
                local bytes = {}
                for index = 4, 1, -1 do
                    bytes[index] = number % 256
                    number = math.floor(number / 256)
                end
                return string.char(unpack(bytes))
            end

            local likeCount = countBits(KEYS[1])
            local favCount = countBits(KEYS[2])
            local value = write32be(0) .. write32be(likeCount) .. write32be(favCount)
                    .. write32be(0) .. write32be(0)
            local sequence = redis.call('GET', KEYS[4]) or '0'
            redis.call('SET', KEYS[3], value)
            redis.call('SET', KEYS[5], sequence)
            redis.call('DEL', KEYS[6])
            redis.call('SREM', KEYS[7], KEYS[6])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final CounterRecoveryProperties properties;

    /**
     * 在 SDS 不存在或损坏时尝试恢复。
     *
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @return 恢复完成或 SDS 原本有效时为 {@code true}；被限流、退避或恢复失败时为 {@code false}
     */
    public boolean recoverIfNecessary(String entityType, String entityId) {
        if (hasValidSds(entityType, entityId)) {
            return true;
        }
        if (!properties.isEnabled()) {
            return false;
        }
        if (isInBackoff(entityType, entityId)) {
            return false;
        }

        RLock lock = redissonClient.getLock(CounterKeys.recoveryLockKey(entityType, entityId));
        boolean acquired = false;
        try {
            acquired = lock.tryLock(1, TimeUnit.SECONDS);
            if (!acquired) {
                return hasValidSds(entityType, entityId);
            }
            if (hasValidSds(entityType, entityId)) {
                return true;
            }
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(
                    CounterKeys.recoveryRateLimiterKey(entityType, entityId)
            );
            rateLimiter.trySetRate(RateType.OVERALL, properties.getRatePermits(), properties.getRateWindow());
            rateLimiter.expire(properties.getRateLimiterIdle());
            if (!rateLimiter.tryAcquire()) {
                recordBackoff(entityType, entityId);
                log.warn("Counter SDS recovery is rate limited: {}:{}", entityType, entityId);
                return false;
            }
            redisTemplate.execute(
                    RECOVER_SCRIPT,
                    List.of(
                            CounterKeys.bitmapIndexKey(CounterMetric.LIKE, entityType, entityId),
                            CounterKeys.bitmapIndexKey(CounterMetric.FAV, entityType, entityId),
                            CounterKeys.sdsKey(entityType, entityId),
                            CounterKeys.sequenceKey(entityType, entityId),
                            CounterKeys.recoveryFenceKey(entityType, entityId),
                            CounterKeys.aggregationKey(entityType, entityId),
                            CounterKeys.aggregationIndexKey()
                    )
            );
            clearBackoff(entityType, entityId);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException exception) {
            recordBackoff(entityType, entityId);
            log.warn("Counter SDS recovery failed: {}:{}", entityType, entityId, exception);
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean hasValidSds(String entityType, String entityId) {
        String key = CounterKeys.sdsKey(entityType, entityId);
        byte[] value = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8))
        );
        return value != null && value.length == SDS_LENGTH;
    }

    private boolean isInBackoff(String entityType, String entityId) {
        Long until = redissonClient.<Long>getBucket(CounterKeys.recoveryBackoffUntilKey(entityType, entityId)).get();
        return until != null && until > System.currentTimeMillis();
    }

    private void recordBackoff(String entityType, String entityId) {
        RBucket<Integer> exponentBucket = redissonClient.getBucket(
                CounterKeys.recoveryBackoffExponentKey(entityType, entityId)
        );
        Integer previous = exponentBucket.get();
        int exponent = Math.min(previous == null ? 0 : previous + 1, 16);
        Duration delay = backoffDelay(exponent);
        Duration ttl = delay.plus(properties.getBackoffMax());
        exponentBucket.set(exponent, ttl);
        redissonClient.<Long>getBucket(CounterKeys.recoveryBackoffUntilKey(entityType, entityId))
                .set(System.currentTimeMillis() + delay.toMillis(), delay);
    }

    private void clearBackoff(String entityType, String entityId) {
        redissonClient.getBucket(CounterKeys.recoveryBackoffExponentKey(entityType, entityId)).delete();
        redissonClient.getBucket(CounterKeys.recoveryBackoffUntilKey(entityType, entityId)).delete();
    }

    private Duration backoffDelay(int exponent) {
        long multiplier = 1L << exponent;
        long millis = Math.min(properties.getBackoffMax().toMillis(),
                properties.getBackoffBase().toMillis() * multiplier);
        return Duration.ofMillis(millis);
    }
}
