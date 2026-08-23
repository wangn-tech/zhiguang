package com.wangning.common.web;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 接口的统一异常出口。
 *
 * <p>可预期异常返回稳定的错误码和安全提示；未知异常仅在服务端记录完整堆栈，
 * 对客户端统一隐藏内部实现细节。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务层主动抛出的可预期异常。
     *
     * @param exception 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    /**
     * 处理使用 {@code @Valid} 校验请求体失败的异常。
     *
     * <p>当前只返回第一个字段错误，保持响应简单；如果后续前端需要一次展示
     * 全部字段错误，再扩展错误响应结构。</p>
     *
     * @param exception 请求体参数校验异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? ErrorCode.BAD_REQUEST.getDefaultMessage()
                : fieldError.getDefaultMessage();

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.BAD_REQUEST,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 兜底处理未预期异常。日志保留堆栈，响应不暴露数据库、文件路径等内部信息。
     *
     * @param exception 未被处理的异常
     * @param request 当前 HTTP 请求
     * @return HTTP 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception for request {}", request.getRequestURI(), exception);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
