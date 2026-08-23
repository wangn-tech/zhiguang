package com.wangning.common.web;

import com.wangning.common.exception.ErrorCode;

import java.time.Instant;

/**
 * API 统一错误响应。
 *
 * @param timestamp 发生错误的时间
 * @param status HTTP 状态码
 * @param code 供客户端稳定识别的错误码
 * @param message 面向调用方的错误提示
 * @param path 发生错误的请求路径
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {

    /**
     * 根据错误码创建统一错误响应。
     *
     * @param errorCode 业务错误码
     * @param message 返回给客户端的错误提示
     * @param path 发生错误的请求路径
     * @return 统一错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                Instant.now(),
                errorCode.getHttpStatus().value(),
                errorCode.name(),
                message,
                path
        );
    }
}
