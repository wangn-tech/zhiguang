package com.wangning.auth.audit;

/**
 * 注册或登录渠道。
 */
public enum LoginChannel {

    /** 注册。 */
    REGISTER,

    /** 验证码登录。 */
    CODE,

    /** 密码登录。 */
    PASSWORD
}
