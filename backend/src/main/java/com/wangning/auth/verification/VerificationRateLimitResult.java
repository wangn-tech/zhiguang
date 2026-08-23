package com.wangning.auth.verification;

/**
 * 验证码发送限流结果。
 */
public enum VerificationRateLimitResult {

    /** 允许发送。 */
    ALLOWED,

    /** 未达到最小发送间隔。 */
    TOO_FREQUENT,

    /** 已达到当天发送上限。 */
    DAILY_LIMIT_REACHED
}
