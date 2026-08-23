package com.wangning.auth.verification;

/**
 * 验证码使用场景，不同场景的验证码不能混用。
 */
public enum VerificationScene {

    /** 注册。 */
    REGISTER,

    /** 登录。 */
    LOGIN,

    /** 重置密码。 */
    RESET_PASSWORD
}
