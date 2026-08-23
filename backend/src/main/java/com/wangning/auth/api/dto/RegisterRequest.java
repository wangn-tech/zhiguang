package com.wangning.auth.api.dto;

import com.wangning.auth.model.IdentifierType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户注册请求。
 *
 * @param identifierType 账号类型
 * @param identifier 手机号或邮箱
 * @param code 注册验证码
 * @param password 原始密码
 * @param agreeTerms 是否同意用户协议
 */
public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        @NotBlank(message = "密码不能为空") String password,
        @AssertTrue(message = "请先同意用户协议") boolean agreeTerms
) {
}
