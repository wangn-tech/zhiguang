package com.wangning.auth.verification;

/**
 * 验证码校验状态。
 */
public enum VerificationCodeStatus {

    /** 校验成功，验证码已经被消费。 */
    SUCCESS,

    /** 验证码不存在或已经过期。 */
    NOT_FOUND,

    /** 验证码不匹配，仍可继续尝试。 */
    MISMATCH,

    /** 错误次数达到上限，验证码已经失效。 */
    TOO_MANY_ATTEMPTS
}
