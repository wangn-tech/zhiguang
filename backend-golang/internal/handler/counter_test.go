package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
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

func TestCounterHandler_GetCounts_ServiceError_Contract(t *testing.T) {
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

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if code, _ := resp["code"].(string); code != "BAD_REQUEST" {
		t.Fatalf("code = %s, want BAD_REQUEST", code)
	}
}

func TestCounterHandler_GetCounts_ServiceError_MessageContract(t *testing.T) {
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

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if message, _ := resp["message"].(string); message != "entityType/entityId 不能为空" {
		t.Fatalf("message = %s, want entityType/entityId 不能为空", message)
	}
}

func TestCounterHandler_GetCounts_UnknownMetrics_FallbackSuccess(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewCounterHandler(&fakeCounterService{counts: map[string]int64{"like": 7, "fav": 9}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/counter/:entityType/:entityId", h.GetCounts)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/123?metrics=foo,%20,bar", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	counts, ok := resp["counts"].(map[string]any)
	if !ok {
		t.Fatalf("counts type assertion failed")
	}
	if got := int64(counts["like"].(float64)); got != 7 {
		t.Fatalf("like count = %d, want 7", got)
	}
	if got := int64(counts["fav"].(float64)); got != 9 {
		t.Fatalf("fav count = %d, want 9", got)
	}
}

func TestCounterHandler_GetCounts_PathParamBoundary_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewCounterHandler(&counterValidationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/counter/:entityType/:entityId", h.GetCounts)

	testCases := []struct {
		name string
		path string
	}{
		{name: "blank entity type", path: "/api/v1/counter/%20/123"},
		{name: "blank entity id", path: "/api/v1/counter/knowpost/%20"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, tc.path, nil)
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)

			if w.Code != http.StatusBadRequest {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
			}
			var resp map[string]any
			if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if code, _ := resp["code"].(string); code != "BAD_REQUEST" {
				t.Fatalf("code = %s, want BAD_REQUEST", code)
			}
			if message, _ := resp["message"].(string); message != "entityType/entityId 不能为空" {
				t.Fatalf("message = %s, want entityType/entityId 不能为空", message)
			}
		})
	}
}

type counterValidationService struct{}

func (s *counterValidationService) Like(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{}, nil
}

func (s *counterValidationService) Unlike(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{}, nil
}

func (s *counterValidationService) Fav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{}, nil
}

func (s *counterValidationService) Unfav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{}, nil
}

func (s *counterValidationService) GetCounts(_ context.Context, entityType string, entityID string, _ []string) (map[string]int64, error) {
	if strings.TrimSpace(entityType) == "" || strings.TrimSpace(entityID) == "" {
		return nil, errorsx.New(errorsx.CodeBadRequest, "entityType/entityId 不能为空")
	}
	return map[string]int64{"like": 1, "fav": 2}, nil
}
