package com.wangning.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求。
 *
 * @param refreshToken Refresh Token JWT
 */
public record TokenRefreshRequest(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken
) {
}
