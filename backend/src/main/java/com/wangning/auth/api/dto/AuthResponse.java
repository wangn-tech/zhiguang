package com.wangning.auth.api.dto;

/**
 * 注册或登录成功后的组合响应。
 *
 * @param user 当前用户信息
 * @param token 新签发的令牌对
 */
public record AuthResponse(
        AuthUserResponse user,
        TokenResponse token
) {
}
