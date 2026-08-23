package com.wangning.auth.verification;

import java.time.Duration;

/**
 * 验证码存储接口。
 */
public interface VerificationCodeStore {

    /**
     * 保存验证码并设置有效期和最大尝试次数。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param code 明文验证码
     * @param ttl 有效期
     * @param maxAttempts 最大错误次数
     */
    void saveCode(
            VerificationScene scene,
            String identifier,
            String code,
            Duration ttl,
            int maxAttempts
    );

    /**
     * 原子校验验证码，并更新尝试次数或消费正确验证码。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param code 待校验验证码
     * @return 校验结果
     */
    VerificationCheckResult verify(VerificationScene scene, String identifier, String code);

    /**
     * 删除指定验证码。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     */
    void invalidate(VerificationScene scene, String identifier);
}
