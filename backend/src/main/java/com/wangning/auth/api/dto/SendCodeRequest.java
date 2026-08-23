package com.wangning.auth.api.dto;

import com.wangning.auth.model.IdentifierType;
import com.wangning.auth.verification.VerificationScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发送验证码请求。
 *
 * @param scene 验证码场景
 * @param identifierType 账号类型
 * @param identifier 手机号或邮箱
 */
public record SendCodeRequest(
        @NotNull(message = "验证码场景不能为空") VerificationScene scene,
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier
) {
}
