package handler

import (
	"context"
	"encoding/json"
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

	var body map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body["status"] != "ok" {
		t.Fatalf("status field = %v, want ok", body["status"])
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

	var body map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body["status"] != "ready" {
		t.Fatalf("status field = %v, want ready", body["status"])
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

	resp := decodeResponse(t, w.Body.Bytes())
	if resp.Code != "INTERNAL_ERROR" {
		t.Fatalf("code = %s, want INTERNAL_ERROR", resp.Code)
	}
	if resp.Message != "依赖未就绪" {
		t.Fatalf("message = %s, want 依赖未就绪", resp.Message)
	}
}

type testResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func decodeResponse(t *testing.T, raw []byte) testResponse {
	t.Helper()

	var resp testResponse
	if err := json.Unmarshal(raw, &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	return resp
}
