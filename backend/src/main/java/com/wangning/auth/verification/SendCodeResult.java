package com.wangning.auth.verification;

/**
 * 验证码发送结果。
 *
 * @param identifier 标准化后的手机号或邮箱
 * @param scene 验证码场景
 * @param expireSeconds 验证码有效秒数
 */
public record SendCodeResult(
        String identifier,
        VerificationScene scene,
        int expireSeconds
) {
}
