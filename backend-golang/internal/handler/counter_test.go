package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestCounterHandler_GetCounts_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewCounterHandler(&fakeCounterService{counts: map[string]int64{"like": 3, "fav": 2}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/counter/:entityType/:entityId", h.GetCounts)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/123?metrics=like,fav", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got, _ := resp["entityType"].(string); got != "knowpost" {
		t.Fatalf("entityType = %s, want knowpost", got)
	}
	counts, ok := resp["counts"].(map[string]any)
	if !ok {
		t.Fatalf("counts type assertion failed")
	}
	if got := int64(counts["like"].(float64)); got != 3 {
		t.Fatalf("like count = %d, want 3", got)
	}
}

func TestCounterHandler_GetCounts_ServiceError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewCounterHandler(&fakeCounterService{countsErr: errorsx.New(errorsx.CodeBadRequest, "entityType/entityId 不能为空")})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/counter/:entityType/:entityId", h.GetCounts)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/123", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestParseMetrics_EmptyInput(t *testing.T) {
	metrics := parseMetrics("   ")
	if metrics != nil {
		t.Fatalf("parseMetrics() = %v, want nil", metrics)
	}
}

func TestParseMetrics_TrimAndIgnoreEmpty(t *testing.T) {
	metrics := parseMetrics(" like, , fav ,  ")
	if len(metrics) != 2 {
		t.Fatalf("len(metrics) = %d, want 2", len(metrics))
	}
	if metrics[0] != "like" || metrics[1] != "fav" {
		t.Fatalf("metrics = %v, want [like fav]", metrics)
	}
}
