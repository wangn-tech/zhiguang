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
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    IDENTIFIER_EXISTS(HttpStatus.CONFLICT, "账号已存在"),
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
