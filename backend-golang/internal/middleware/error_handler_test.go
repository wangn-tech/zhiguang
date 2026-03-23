package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestErrorHandler_BusinessError(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(ErrorHandler())
	r.GET("/business", func(c *gin.Context) {
		_ = c.Error(errorsx.New(errorsx.CodeBadRequest, "参数错误"))
	})

	req := httptest.NewRequest(http.MethodGet, "/business", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", w.Code)
	}
	if got := w.Body.String(); got != "{\"code\":\"BAD_REQUEST\",\"message\":\"参数错误\"}" {
		t.Fatalf("body = %s", got)
	}
}

func TestErrorHandler_Panic(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(ErrorHandler())
	r.GET("/panic", func(c *gin.Context) {
		panic("boom")
	})

	req := httptest.NewRequest(http.MethodGet, "/panic", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want 500", w.Code)
	}
	if got := w.Body.String(); got != "{\"code\":\"INTERNAL_ERROR\",\"message\":\"服务异常，请稍后重试\"}" {
		t.Fatalf("body = %s", got)
	}
}
