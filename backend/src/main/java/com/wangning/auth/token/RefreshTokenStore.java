package com.wangning.auth.token;

import java.time.Duration;

/**
 * Refresh Token 白名单存储。
 */
public interface RefreshTokenStore {

    /**
     * 保存新的 Refresh Token 会话。
     *
     * @param userId 用户 ID
     * @param tokenId Refresh Token 的 jti
     * @param ttl 剩余有效期
     */
    void storeToken(long userId, String tokenId, Duration ttl);

    /**
     * 判断 Refresh Token 是否仍在白名单中。
     *
     * @param userId 用户 ID
     * @param tokenId Refresh Token 的 jti
     * @return 有效时返回 {@code true}
     */
    boolean isTokenValid(long userId, String tokenId);

    /**
     * 原子消费旧会话并保存新会话。
     *
     * @param userId 用户 ID
     * @param currentTokenId 旧 Refresh Token 的 jti
     * @param nextTokenId 新 Refresh Token 的 jti
     * @param nextTtl 新会话有效期
     * @return 旧会话存在且轮换成功时返回 {@code true}
     */
    boolean rotateToken(
            long userId,
            String currentTokenId,
            String nextTokenId,
            Duration nextTtl
    );

    /**
     * 撤销一个 Refresh Token 会话。
     *
     * @param userId 用户 ID
     * @param tokenId Refresh Token 的 jti
     */
    void revokeToken(long userId, String tokenId);

    /**
     * 撤销用户的全部 Refresh Token 会话。
     *
     * @param userId 用户 ID
     */
    void revokeAll(long userId);
}
