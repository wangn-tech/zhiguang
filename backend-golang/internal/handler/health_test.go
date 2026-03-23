package handler

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

type fakeChecker struct {
	name string
	err  error
}

func (f fakeChecker) Name() string { return f.name }

func (f fakeChecker) Check(ctx context.Context) error { return f.err }

func TestHealthz_OK(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	h := NewHealthHandler(nil)
	h.Register(r)

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", w.Code)
	}
}

func TestReadyz_OK(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	h := NewHealthHandler([]Checker{
		fakeChecker{name: "mysql", err: nil},
		fakeChecker{name: "redis", err: nil},
	})
	h.Register(r)

	req := httptest.NewRequest(http.MethodGet, "/readyz", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", w.Code)
	}
}

func TestReadyz_Unavailable(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	h := NewHealthHandler([]Checker{
		fakeChecker{name: "mysql", err: errors.New("dial timeout")},
		fakeChecker{name: "redis", err: nil},
	})
	h.Register(r)

	req := httptest.NewRequest(http.MethodGet, "/readyz", nil)
	w := httptest.NewRecorder()

	r.ServeHTTP(w, req)

	if w.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want 503", w.Code)
	}
}
