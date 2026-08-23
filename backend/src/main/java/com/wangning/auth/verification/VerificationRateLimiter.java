package com.wangning.auth.verification;

import com.wangning.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的验证码发送限流器。
 *
 * <p>发送冷却、每日次数检查、计数和 TTL 设置在同一个 Lua 脚本中完成。</p>
 */
@Component
@RequiredArgsConstructor
public class VerificationRateLimiter {

    private static final String COOLDOWN_KEY_PREFIX = "auth:code:last:";
    private static final String DAILY_KEY_PREFIX = "auth:code:count:";

    private static final RedisScript<Long> ACQUIRE_SCRIPT = RedisScript.of("""
            local cooldownMillis = tonumber(ARGV[1])
            local dailyLimit = tonumber(ARGV[2])
            local dailyTtlSeconds = tonumber(ARGV[3])

            if cooldownMillis > 0 and redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end

            local currentCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if currentCount >= dailyLimit then
                return -1
            end

            if cooldownMillis > 0 then
                redis.call('SET', KEYS[1], '1', 'PX', cooldownMillis)
            end

            local updatedCount = redis.call('INCR', KEYS[2])
            local currentTtl = redis.call('TTL', KEYS[2])
            if updatedCount == 1 or currentTtl < 0 then
                redis.call('EXPIRE', KEYS[2], dailyTtlSeconds)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    /**
     * 尝试获取一次验证码发送许可。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @return 限流结果
     */
    public VerificationRateLimitResult tryAcquire(
            VerificationScene scene,
            String identifier
    ) {
        Objects.requireNonNull(scene, "scene must not be null");
        Assert.hasText(identifier, "identifier must not be blank");

        AuthProperties.Verification properties = authProperties.getVerification();
        Duration sendInterval = properties.getSendInterval();
        Assert.notNull(sendInterval, "sendInterval must not be null");
        Assert.isTrue(!sendInterval.isNegative(), "sendInterval must not be negative");
        Assert.isTrue(properties.getDailyLimit() > 0, "dailyLimit must be positive");

        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();
        Long result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(
                        cooldownKey(scene, identifier),
                        dailyKey(today, scene, identifier)
                ),
                String.valueOf(sendInterval.toMillis()),
                String.valueOf(properties.getDailyLimit()),
                String.valueOf(secondsUntilDailyKeyExpires(now))
        );

        if (Objects.equals(result, 1L)) {
            return VerificationRateLimitResult.ALLOWED;
        }
        if (Objects.equals(result, 0L)) {
            return VerificationRateLimitResult.TOO_FREQUENT;
        }
        if (Objects.equals(result, -1L)) {
            return VerificationRateLimitResult.DAILY_LIMIT_REACHED;
        }
        throw new IllegalStateException("Redis did not return a valid rate limit result");
    }

    /**
     * 计算每日计数键的剩余有效秒数。
     *
     * <p>键在次日零点后额外保留五分钟，避免服务实例时间存在轻微偏差。</p>
     *
     * @param now 当前时间
     * @return Redis TTL 秒数
     */
    private long secondsUntilDailyKeyExpires(ZonedDateTime now) {
        ZonedDateTime expiration = now.toLocalDate()
                .plusDays(1)
                .atStartOfDay(now.getZone())
                .plusMinutes(5);
        return Math.max(1L, Duration.between(now, expiration).toSeconds());
    }

    /**
     * 生成发送冷却键。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @return Redis 键
     */
    private String cooldownKey(VerificationScene scene, String identifier) {
        return COOLDOWN_KEY_PREFIX + scene.name() + ":" + identifier;
    }

    /**
     * 生成每日计数键。
     *
     * @param date 当前日期
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @return Redis 键
     */
    private String dailyKey(LocalDate date, VerificationScene scene, String identifier) {
        return DAILY_KEY_PREFIX + scene.name() + ":" + identifier + ":" + date;
    }
}
