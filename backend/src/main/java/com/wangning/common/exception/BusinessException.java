package com.wangning.common.exception;

import lombok.Getter;

import java.util.Objects;

/**
 * 可预期的业务异常。
 *
 * <p>业务层只需抛出该异常，由全局异常处理器统一转换为 HTTP 响应，
 * 从而避免在每个 Controller 中重复编写异常响应逻辑。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码的默认提示创建业务异常。
     *
     * @param errorCode 业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode must not be null").getDefaultMessage());
    }

    /**
     * 使用自定义提示创建业务异常，错误码仍作为客户端判断异常类型的依据。
     *
     * @param errorCode 业务错误码
     * @param message 返回给客户端的错误提示
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
