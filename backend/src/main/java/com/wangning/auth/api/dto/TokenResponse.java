package com.wangning.auth.api.dto;

import java.time.Instant;

/**
 * Access Token 和 Refresh Token 响应。
 *
 * @param accessToken Access Token JWT
 * @param accessTokenExpiresAt Access Token 过期时间
 * @param refreshToken Refresh Token JWT
 * @param refreshTokenExpiresAt Refresh Token 过期时间
 */
public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
