package com.wangning.auth.verification;

import com.wangning.auth.config.AuthProperties;
import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 验证码业务服务。
 *
 * <p>负责申请发送许可、生成并保存验证码、调用发送器，以及委托存储层原子校验验证码。
 * 账号格式和账号是否存在等规则由上层认证服务处理。</p>
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeStore codeStore;
    private final CodeSender codeSender;
    private final VerificationRateLimiter rateLimiter;
    private final AuthProperties authProperties;

    /**
     * 生成并发送验证码。
     *
     * <p>发送失败时删除本次保存的验证码，但已经取得的冷却许可和每日次数不会回退，
     * 避免通过反复触发发送异常绕过限流。</p>
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @return 验证码发送结果
     * @throws BusinessException 参数无效或触发发送限制时抛出
     */
    public SendCodeResult sendCode(VerificationScene scene, String identifier) {
        validateSceneAndIdentifier(scene, identifier, "请提供正确的验证码发送参数");

        VerificationRateLimitResult rateLimitResult = rateLimiter.tryAcquire(scene, identifier);
        if (rateLimitResult != VerificationRateLimitResult.ALLOWED) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMIT);
        }

        AuthProperties.Verification properties = authProperties.getVerification();
        String code = generateNumericCode(properties.getCodeLength());
        codeStore.saveCode(
                scene,
                identifier,
                code,
                properties.getTtl(),
                properties.getMaxAttempts()
        );

        try {
            codeSender.sendCode(
                    scene,
                    identifier,
                    code,
                    Math.toIntExact(properties.getTtl().toMinutes())
            );
        } catch (RuntimeException exception) {
            codeStore.invalidate(scene, identifier);
            throw exception;
        }

        return new SendCodeResult(
                identifier,
                scene,
                Math.toIntExact(properties.getTtl().toSeconds())
        );
    }

    /**
     * 原子校验并消费验证码。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param code 用户提交的验证码
     * @return 验证码校验结果
     * @throws BusinessException 参数不完整时抛出
     */
    public VerificationCheckResult verify(
            VerificationScene scene,
            String identifier,
            String code
    ) {
        validateSceneAndIdentifier(scene, identifier, "验证码校验参数不完整");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验参数不完整");
        }
        return codeStore.verify(scene, identifier, code);
    }

    /**
     * 使指定验证码失效。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @throws BusinessException 参数不完整时抛出
     */
    public void invalidate(VerificationScene scene, String identifier) {
        validateSceneAndIdentifier(scene, identifier, "验证码失效参数不完整");
        codeStore.invalidate(scene, identifier);
    }

    /**
     * 校验场景和账号参数。
     *
     * @param scene 验证码场景
     * @param identifier 标准化后的手机号或邮箱
     * @param message 参数无效时的错误提示
     */
    private void validateSceneAndIdentifier(
            VerificationScene scene,
            String identifier,
            String message
    ) {
        if (scene == null || !StringUtils.hasText(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 使用安全随机数生成指定长度的纯数字验证码。
     *
     * @param length 验证码位数
     * @return 可能包含前导零的数字验证码
     */
    private String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
