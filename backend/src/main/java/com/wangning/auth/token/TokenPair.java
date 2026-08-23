package com.wangning.auth.token;

import java.time.Instant;

/**
 * Access Token 与 Refresh Token 组合。
 *
 * @param accessToken Access Token JWT
 * @param accessTokenExpiresAt Access Token 过期时间
 * @param refreshToken Refresh Token JWT
 * @param refreshTokenExpiresAt Refresh Token 过期时间
 * @param refreshTokenId Refresh Token 的唯一标识 jti
 */
public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String refreshTokenId
) {
}
