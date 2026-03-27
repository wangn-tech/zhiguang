package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestActionHandler_Like_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{actionResult: service.ActionResult{Changed: true, Active: true}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/like", h.Like)

	body := map[string]any{"entityType": "knowpost", "entityId": "123"}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if changed, _ := resp["changed"].(bool); !changed {
		t.Fatalf("changed = %v, want true", changed)
	}
	if liked, _ := resp["liked"].(bool); !liked {
		t.Fatalf("liked = %v, want true", liked)
	}
}

func TestActionHandler_Unlike_Idempotent(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{actionResult: service.ActionResult{Changed: false, Active: false}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/unlike", h.Unlike)

	body := map[string]any{"entityType": "knowpost", "entityId": "123"}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/unlike", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if changed, _ := resp["changed"].(bool); changed {
		t.Fatalf("changed = %v, want false", changed)
	}
	if liked, _ := resp["liked"].(bool); liked {
		t.Fatalf("liked = %v, want false", liked)
	}
}

func TestActionHandler_Fav_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{actionResult: service.ActionResult{Changed: true, Active: true}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/fav", h.Fav)

	body := map[string]any{"entityType": "knowpost", "entityId": "123"}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/fav", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if faved, _ := resp["faved"].(bool); !faved {
		t.Fatalf("faved = %v, want true", faved)
	}
}

func TestActionHandler_Unfav_Idempotent(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{actionResult: service.ActionResult{Changed: false, Active: false}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/unfav", h.Unfav)

	body := map[string]any{"entityType": "knowpost", "entityId": "123"}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/unfav", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if changed, _ := resp["changed"].(bool); changed {
		t.Fatalf("changed = %v, want false", changed)
	}
	if faved, _ := resp["faved"].(bool); faved {
		t.Fatalf("faved = %v, want false", faved)
	}
}

func TestActionHandler_Like_ServiceError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{actionErr: newBadRequestError("entityType/entityId 不能为空")})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/like", h.Like)

	body := map[string]any{"entityType": "knowpost", "entityId": "123"}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestActionHandler_InvalidBody(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/like", h.Like)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

type fakeCounterService struct {
	actionResult service.ActionResult
	actionErr    error
	counts       map[string]int64
	countsErr    error
}

func (s *fakeCounterService) Like(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	if s.actionErr != nil {
		return service.ActionResult{}, s.actionErr
	}
	return s.actionResult, nil
}

func (s *fakeCounterService) Unlike(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	if s.actionErr != nil {
		return service.ActionResult{}, s.actionErr
	}
	return s.actionResult, nil
}

func (s *fakeCounterService) Fav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	if s.actionErr != nil {
		return service.ActionResult{}, s.actionErr
	}
	return s.actionResult, nil
}

func (s *fakeCounterService) Unfav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	if s.actionErr != nil {
		return service.ActionResult{}, s.actionErr
	}
	return s.actionResult, nil
}

func (s *fakeCounterService) GetCounts(_ context.Context, _ string, _ string, _ []string) (map[string]int64, error) {
	if s.countsErr != nil {
		return nil, s.countsErr
	}
	if s.counts != nil {
		return s.counts, nil
	}
	return map[string]int64{"like": 0, "fav": 0}, nil
}

func newBadRequestError(message string) error {
	return errorsx.New(errorsx.CodeBadRequest, message)
}

func TestActionHandler_InvalidBody_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewActionHandler(&fakeCounterService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/like", h.Like)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("Content-Type", "application/json")
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
