package com.wangning.auth.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 登录审计日志服务。
 *
 * <p>审计日志属于认证结果的附属记录。写入异常会产生服务端告警，但不会把已经完成的
 * 注册或登录改判为失败。告警日志不输出账号、密码、验证码或令牌。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final LoginLogMapper loginLogMapper;

    /**
     * 记录一次注册或登录结果。
     *
     * @param userId 用户 ID，账号不存在时允许为空
     * @param identifier 标准化后的手机号或邮箱
     * @param channel 注册或登录渠道
     * @param ip 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @param status 认证结果
     */
    public void record(
            Long userId,
            String identifier,
            LoginChannel channel,
            String ip,
            String userAgent,
            LoginStatus status
    ) {
        if (!StringUtils.hasText(identifier) || channel == null || status == null) {
            log.warn("Skip invalid login audit event userId={} channel={} status={}",
                    validUserId(userId), channel, status);
            return;
        }

        LoginLog loginLog = LoginLog.builder()
                .userId(validUserId(userId))
                .identifier(truncate(identifier.trim(), MAX_IDENTIFIER_LENGTH))
                .channel(channel.name())
                .ip(normalizeAndTruncate(ip, MAX_IP_LENGTH))
                .userAgent(normalizeAndTruncate(userAgent, MAX_USER_AGENT_LENGTH))
                .status(status.name())
                .createdAt(Instant.now())
                .build();

        try {
            int affectedRows = loginLogMapper.insert(loginLog);
            if (affectedRows != 1) {
                log.warn("Login audit insert affected unexpected rows userId={} channel={} status={}",
                        loginLog.getUserId(), channel, status);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to persist login audit userId={} channel={} status={}",
                    loginLog.getUserId(), channel, status, exception);
        }
    }

    /**
     * 过滤无效用户 ID。
     *
     * @param userId 原始用户 ID
     * @return 正整数用户 ID，无效时返回 {@code null}
     */
    private Long validUserId(Long userId) {
        return userId != null && userId > 0 ? userId : null;
    }

    /**
     * 将可空字段标准化并截断到数据库长度。
     *
     * @param value 原始字段
     * @param maxLength 最大长度
     * @return 标准化字段，空白值返回 {@code null}
     */
    private String normalizeAndTruncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return truncate(value.trim(), maxLength);
    }

    /**
     * 截断字符串。
     *
     * @param value 原始字符串
     * @param maxLength 最大长度
     * @return 不超过最大长度的字符串
     */
    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
