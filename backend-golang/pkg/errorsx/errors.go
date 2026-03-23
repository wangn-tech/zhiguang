package errorsx

import (
	"errors"
	"net/http"

	"github.com/go-playground/validator/v10"
)

// Code 与 backend-java 的 ErrorCode 枚举保持一致。
type Code string

const (
	CodeIdentifierExists           Code = "IDENTIFIER_EXISTS"
	CodeIdentifierNotFound         Code = "IDENTIFIER_NOT_FOUND"
	CodeZgIDExists                 Code = "ZGID_EXISTS"
	CodeVerificationRateLimit      Code = "VERIFICATION_RATE_LIMIT"
	CodeVerificationDailyLimit     Code = "VERIFICATION_DAILY_LIMIT"
	CodeVerificationNotFound       Code = "VERIFICATION_NOT_FOUND"
	CodeVerificationMismatch       Code = "VERIFICATION_MISMATCH"
	CodeVerificationTooManyAttempt Code = "VERIFICATION_TOO_MANY_ATTEMPTS"
	CodeInvalidCredentials         Code = "INVALID_CREDENTIALS"
	CodePasswordPolicyViolation    Code = "PASSWORD_POLICY_VIOLATION"
	CodeTermsNotAccepted           Code = "TERMS_NOT_ACCEPTED"
	CodeRefreshTokenInvalid        Code = "REFRESH_TOKEN_INVALID"
	CodeBadRequest                 Code = "BAD_REQUEST"
	CodeInternalError              Code = "INTERNAL_ERROR"
)

var defaultMessages = map[Code]string{
	CodeIdentifierExists:           "账号已存在",
	CodeIdentifierNotFound:         "账号不存在",
	CodeZgIDExists:                 "知光号已存在",
	CodeVerificationRateLimit:      "验证码发送过于频繁",
	CodeVerificationDailyLimit:     "验证码发送次数超限",
	CodeVerificationNotFound:       "验证码不存在或已过期",
	CodeVerificationMismatch:       "验证码错误",
	CodeVerificationTooManyAttempt: "验证码尝试次数过多",
	CodeInvalidCredentials:         "登录凭证错误",
	CodePasswordPolicyViolation:    "密码强度不足",
	CodeTermsNotAccepted:           "请先同意服务条款",
	CodeRefreshTokenInvalid:        "刷新令牌无效",
	CodeBadRequest:                 "请求参数错误",
	CodeInternalError:              "服务器内部错误",
}

const genericInternalMessage = "服务异常，请稍后重试"

// AppError 表示可控业务异常。
// 默认映射为 HTTP 400，可通过 Status 指定其他状态码。
type AppError struct {
	Code    Code
	Message string
	Status  int
}

// Error 实现 error 接口。
func (e *AppError) Error() string {
	if e == nil {
		return defaultMessages[CodeInternalError]
	}
	if e.Message != "" {
		return e.Message
	}
	if msg, ok := defaultMessages[e.Code]; ok {
		return msg
	}
	return defaultMessages[CodeInternalError]
}

// New 创建业务异常。
func New(code Code, message string) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  http.StatusBadRequest,
	}
}

// NewWithStatus 创建带明确 HTTP 状态码的业务异常。
func NewWithStatus(code Code, message string, status int) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  status,
	}
}

// ErrorResponse 是统一异常响应体：仅 code/message。
type ErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Normalize 将任意错误规范化为 HTTP 状态码与标准异常响应体。
func Normalize(err error) (int, ErrorResponse) {
	if err == nil {
		return http.StatusInternalServerError, ErrorResponse{
			Code:    string(CodeInternalError),
			Message: genericInternalMessage,
		}
	}

	var appErr *AppError
	if errors.As(err, &appErr) {
		status := appErr.Status
		if status == 0 {
			status = http.StatusBadRequest
		}
		return status, ErrorResponse{
			Code:    string(appErr.Code),
			Message: appErr.Error(),
		}
	}

	var validationErr validator.ValidationErrors
	if errors.As(err, &validationErr) {
		message := defaultMessages[CodeBadRequest]
		if len(validationErr) > 0 {
			message = validationErr[0].Error()
		}
		return http.StatusBadRequest, ErrorResponse{
			Code:    string(CodeBadRequest),
			Message: message,
		}
	}

	return http.StatusInternalServerError, ErrorResponse{
		Code:    string(CodeInternalError),
		Message: genericInternalMessage,
	}
}
