package com.wangning.auth.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 注册和登录审计日志持久化模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {

    /** 日志主键。 */
    private Long id;

    /** 用户 ID，账号不存在时为空。 */
    private Long userId;

    /** 登录或注册使用的标准化账号。 */
    private String identifier;

    /** 登录渠道。 */
    private String channel;

    /** 客户端 IP。 */
    private String ip;

    /** 客户端 User-Agent。 */
    private String userAgent;

    /** 登录结果。 */
    private String status;

    /** 记录时间。 */
    private Instant createdAt;
}
