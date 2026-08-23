package com.wangning.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 白名单的 Refresh Token 会话存储。
 *
 * <p>轮换通过 Lua 脚本原子完成，用户全部会话撤销使用 {@code SCAN} 分批处理，
 * 避免使用会阻塞 Redis 的 {@code KEYS} 命令。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:rt:";
    private static final int SCAN_BATCH_SIZE = 100;

    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) ~= '1' then
                return 0
            end

            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeToken(long userId, String tokenId, Duration ttl) {
        validateToken(userId, tokenId);
        validateTtl(ttl);
        redisTemplate.opsForValue().set(key(userId, tokenId), "1", ttl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        validateToken(userId, tokenId);
        return Objects.equals("1", redisTemplate.opsForValue().get(key(userId, tokenId)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean rotateToken(
            long userId,
            String currentTokenId,
            String nextTokenId,
            Duration nextTtl
    ) {
        validateToken(userId, currentTokenId);
        validateToken(userId, nextTokenId);
        Assert.isTrue(
                !currentTokenId.equals(nextTokenId),
                "currentTokenId and nextTokenId must be different"
        );
        validateTtl(nextTtl);

        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId, currentTokenId), key(userId, nextTokenId)),
                String.valueOf(nextTtl.toMillis())
        );
        return Objects.equals(result, 1L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revokeToken(long userId, String tokenId) {
        validateToken(userId, tokenId);
        redisTemplate.delete(key(userId, tokenId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revokeAll(long userId) {
        Assert.isTrue(userId > 0, "userId must be positive");
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + userId + ":*")
                .count(SCAN_BATCH_SIZE)
                .build();
        List<String> batch = new ArrayList<>(SCAN_BATCH_SIZE);

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == SCAN_BATCH_SIZE) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            redisTemplate.delete(batch);
        }
    }

    /**
     * 校验会话标识参数。
     *
     * @param userId 用户 ID
     * @param tokenId Refresh Token 的 jti
     */
    private void validateToken(long userId, String tokenId) {
        Assert.isTrue(userId > 0, "userId must be positive");
        Assert.hasText(tokenId, "tokenId must not be blank");
    }

    /**
     * 校验 Redis TTL。
     *
     * @param ttl 会话有效期
     */
    private void validateTtl(Duration ttl) {
        Assert.notNull(ttl, "ttl must not be null");
        Assert.isTrue(!ttl.isZero() && !ttl.isNegative(), "ttl must be positive");
    }

    /**
     * 构造 Refresh Token 白名单键。
     *
     * @param userId 用户 ID
     * @param tokenId Refresh Token 的 jti
     * @return Redis 键
     */
    private String key(long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }
}
