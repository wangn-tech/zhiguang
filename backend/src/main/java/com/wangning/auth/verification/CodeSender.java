package com.wangning.auth.verification;

/**
 * 验证码发送器。
 *
 * <p>认证业务只依赖该接口，后续可以分别接入短信和邮件发送实现。</p>
 */
public interface CodeSender {

    /**
     * 向指定账号发送验证码。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param code 验证码
     * @param expireMinutes 验证码有效分钟数
     */
    void sendCode(
            VerificationScene scene,
            String identifier,
            String code,
            int expireMinutes
    );
}
