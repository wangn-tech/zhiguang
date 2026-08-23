package com.wangning.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 对外暴露的稳定错误码。
 *
 * <p>客户端应根据错误码处理异常分支，不应依赖可能调整的错误提示文案。
 * 后续增加用户、认证等业务时，可以在此补充更具体的业务错误码。</p>
 */
@Getter
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求参数错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行该操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不受支持"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    IDENTIFIER_EXISTS(HttpStatus.CONFLICT, "账号已存在"),
    IDENTIFIER_NOT_FOUND(HttpStatus.NOT_FOUND, "账号不存在"),
    ZGID_EXISTS(HttpStatus.CONFLICT, "知光号已存在"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或凭证错误"),
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "验证码无效或已过期"),
    VERIFICATION_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多"),
    VERIFICATION_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "密码不符合安全要求"),
    TERMS_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "请先同意用户协议"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期"),
    STORAGE_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "对象存储配置错误"),
    STORAGE_OPERATION_FAILED(HttpStatus.BAD_GATEWAY, "对象存储操作失败"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    /**
     * 创建错误码。
     *
     * @param httpStatus 对应的 HTTP 状态
     * @param defaultMessage 默认错误提示
     */
    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
