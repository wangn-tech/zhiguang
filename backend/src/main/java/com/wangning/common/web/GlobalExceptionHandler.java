package com.wangning.common.web;

import com.wangning.common.exception.BusinessException;
import com.wangning.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * 处理 JSON 格式错误、枚举值无效等无法读取请求体的情况。
     *
     * @param exception 请求体读取异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BAD_REQUEST.getDefaultMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理缺少 multipart 字段、请求格式错误或上传体积超过容器限制的情况。
     *
     * @param exception multipart 请求异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ErrorResponse> handleMultipartException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.BAD_REQUEST,
                "上传文件缺失、格式错误或大小超过限制",
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理不存在的接口或静态资源，避免被兜底处理器误报为服务器错误。
     *
     * @param exception 资源不存在异常
     * @param request 当前 HTTP 请求
     * @return HTTP 404 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理请求路径存在但 HTTP 方法不受支持的情况。
     *
     * @param exception 请求方法不受支持异常
     * @param request 当前 HTTP 请求
     * @return HTTP 405 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
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
