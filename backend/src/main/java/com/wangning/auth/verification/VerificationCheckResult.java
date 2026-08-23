package com.wangning.auth.verification;

/**
 * 验证码校验结果。
 *
 * @param status 校验状态
 * @param attempts 已失败次数
 * @param maxAttempts 最大失败次数
 */
public record VerificationCheckResult(
        VerificationCodeStatus status,
        int attempts,
        int maxAttempts
) {

    /**
     * 判断验证码是否校验成功。
     *
     * @return 成功时返回 {@code true}
     */
    public boolean isSuccess() {
        return status == VerificationCodeStatus.SUCCESS;
    }
}
