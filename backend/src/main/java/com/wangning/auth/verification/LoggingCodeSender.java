package com.wangning.auth.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将验证码写入日志的开发阶段发送器。
 *
 * <p><strong>生产风险：</strong>该实现不会真正发送短信或邮件，并且会在日志中记录
 * 明文验证码，仅用于当前开发和联调。正式生产环境必须替换为真实发送器，且不得继续
 * 输出验证码明文。</p>
 */
@Slf4j
@Component
public class LoggingCodeSender implements CodeSender {

    /**
     * 记录验证码发送信息。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param code 验证码
     * @param expireMinutes 验证码有效分钟数
     */
    @Override
    public void sendCode(
            VerificationScene scene,
            String identifier,
            String code,
            int expireMinutes
    ) {
        log.info(
                "Send verification code scene={} identifier={} code={} expireMinutes={}",
                scene,
                identifier,
                code,
                expireMinutes
        );
    }
}
