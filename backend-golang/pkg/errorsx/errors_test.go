package errorsx

import (
	"errors"
	"net/http"
	"testing"
)

func TestNormalize_AppError(t *testing.T) {
	status, body := Normalize(New(CodeIdentifierExists, ""))
	if status != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", status)
	}
	if body.Code != string(CodeIdentifierExists) {
		t.Fatalf("code = %s, want %s", body.Code, CodeIdentifierExists)
	}
	if body.Message != "账号已存在" {
		t.Fatalf("message = %s, want 账号已存在", body.Message)
	}
}

func TestNormalize_AppErrorWithStatus(t *testing.T) {
	status, body := Normalize(NewWithStatus(CodeInternalError, "依赖未就绪", http.StatusServiceUnavailable))
	if status != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want 503", status)
	}
	if body.Code != string(CodeInternalError) {
		t.Fatalf("code = %s, want %s", body.Code, CodeInternalError)
	}
	if body.Message != "依赖未就绪" {
		t.Fatalf("message = %s, want 依赖未就绪", body.Message)
	}
}

func TestNormalize_GenericError(t *testing.T) {
	status, body := Normalize(errors.New("unknown"))
	if status != http.StatusInternalServerError {
		t.Fatalf("status = %d, want 500", status)
	}
	if body.Code != string(CodeInternalError) {
		t.Fatalf("code = %s, want %s", body.Code, CodeInternalError)
	}
	if body.Message != "服务异常，请稍后重试" {
		t.Fatalf("message = %s, want 服务异常，请稍后重试", body.Message)
	}
}
