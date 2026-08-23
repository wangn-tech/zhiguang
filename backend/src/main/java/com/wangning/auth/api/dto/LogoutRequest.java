package com.wangning.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 退出登录请求。
 *
 * @param refreshToken 需要撤销的 Refresh Token JWT
 */
public record LogoutRequest(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken
) {
}
