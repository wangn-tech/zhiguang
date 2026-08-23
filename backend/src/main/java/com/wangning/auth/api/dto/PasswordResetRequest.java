package com.wangning.auth.api.dto;

import com.wangning.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 重置密码请求。
 *
 * @param identifierType 账号类型
 * @param identifier 手机号或邮箱
 * @param code 重置密码验证码
 * @param newPassword 新密码
 */
public record PasswordResetRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
