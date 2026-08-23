package com.wangning.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis Hash 的验证码存储。
 *
 * <p>验证码按已确认的原项目方案明文短期保存。保存和校验均通过 Lua 脚本执行，确保
 * 字段、TTL、尝试次数和成功删除在并发场景下保持原子性。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String KEY_PREFIX = "auth:code:";

    private static final RedisScript<Long> SAVE_SCRIPT = RedisScript.of("""
            redis.call('HSET', KEYS[1],
                'code', ARGV[1],
                'attempts', '0',
                'maxAttempts', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    private static final RedisScript<String> VERIFY_SCRIPT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 'NOT_FOUND:0:0'
            end

            local storedCode = redis.call('HGET', KEYS[1], 'code')
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            local maxAttempts = tonumber(redis.call('HGET', KEYS[1], 'maxAttempts') or '0')

            if maxAttempts <= 0 then
                redis.call('DEL', KEYS[1])
                return 'NOT_FOUND:0:0'
            end

            if attempts >= maxAttempts then
                redis.call('DEL', KEYS[1])
                return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end

            if storedCode == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 'SUCCESS:' .. attempts .. ':' .. maxAttempts
            end

            attempts = attempts + 1
            if attempts >= maxAttempts then
                redis.call('DEL', KEYS[1])
                return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end

            redis.call('HSET', KEYS[1], 'attempts', attempts)
            return 'MISMATCH:' .. attempts .. ':' .. maxAttempts
            """, String.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveCode(
            VerificationScene scene,
            String identifier,
            String code,
            Duration ttl,
            int maxAttempts
    ) {
        validateKeyParts(scene, identifier);
        Assert.hasText(code, "code must not be blank");
        Assert.notNull(ttl, "ttl must not be null");
        Assert.isTrue(!ttl.isZero() && !ttl.isNegative(), "ttl must be positive");
        Assert.isTrue(maxAttempts > 0, "maxAttempts must be positive");

        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(buildKey(scene, identifier)),
                code,
                String.valueOf(maxAttempts),
                String.valueOf(ttl.toMillis())
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VerificationCheckResult verify(
            VerificationScene scene,
            String identifier,
            String code
    ) {
        validateKeyParts(scene, identifier);
        Assert.hasText(code, "code must not be blank");

        String encodedResult = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(buildKey(scene, identifier)),
                code
        );
        return parseResult(encodedResult);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate(VerificationScene scene, String identifier) {
        validateKeyParts(scene, identifier);
        redisTemplate.delete(buildKey(scene, identifier));
    }

    /**
     * 解析 Lua 脚本返回的状态、次数和上限。
     *
     * @param encodedResult 脚本返回值
     * @return 验证码校验结果
     */
    private VerificationCheckResult parseResult(String encodedResult) {
        if (encodedResult == null) {
            throw new IllegalStateException("Redis did not return a verification result");
        }

        String[] parts = encodedResult.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid verification result returned by Redis");
        }

        try {
            return new VerificationCheckResult(
                    VerificationCodeStatus.valueOf(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid verification result returned by Redis", exception);
        }
    }

    /**
     * 生成验证码 Redis 键。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @return Redis 键
     */
    private String buildKey(VerificationScene scene, String identifier) {
        return KEY_PREFIX + scene.name() + ":" + identifier;
    }

    /**
     * 校验 Redis 键组成部分。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     */
    private void validateKeyParts(VerificationScene scene, String identifier) {
        Objects.requireNonNull(scene, "scene must not be null");
        Assert.hasText(identifier, "identifier must not be blank");
    }
}
