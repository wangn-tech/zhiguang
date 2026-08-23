package com.wangning.auth.api.dto;

import com.wangning.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户登录请求。
 *
 * <p>{@code code} 和 {@code password} 必须且只能提供一个，该关联规则由认证服务校验。</p>
 *
 * @param identifierType 账号类型
 * @param identifier 手机号或邮箱
 * @param code 登录验证码
 * @param password 原始密码
 */
public record LoginRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,
        String password
) {
}
