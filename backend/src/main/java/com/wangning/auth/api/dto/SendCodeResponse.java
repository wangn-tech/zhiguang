package com.wangning.auth.api.dto;

import com.wangning.auth.verification.VerificationScene;

/**
 * 发送验证码响应。
 *
 * @param identifier 标准化后的手机号或邮箱
 * @param scene 验证码场景
 * @param expireSeconds 验证码有效秒数
 */
public record SendCodeResponse(
        String identifier,
        VerificationScene scene,
        int expireSeconds
) {
}
