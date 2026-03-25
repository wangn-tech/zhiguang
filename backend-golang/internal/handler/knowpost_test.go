package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
)

import "github.com/gin-gonic/gin"

func TestKnowPostHandler_CreateDraft_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{draftID: 123456789})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/drafts", h.CreateDraft)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/drafts", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got, _ := body["id"].(string); got != "123456789" {
		t.Fatalf("id = %s, want 123456789", got)
	}
}

func TestKnowPostHandler_CreateDraft_ServiceError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{err: errors.New("db error")})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/drafts", h.CreateDraft)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/drafts", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusInternalServerError)
	}
}

func TestKnowPostHandler_ConfirmContent_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/:id/content/confirm", h.ConfirmContent)

	body := map[string]any{
		"objectKey": "posts/1/content.md",
		"etag":      "abc123",
		"size":      12,
		"sha256":    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
	}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/1/content/confirm", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestKnowPostHandler_ConfirmContent_InvalidID(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/:id/content/confirm", h.ConfirmContent)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/abc/content/confirm", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

type fakeKnowPostService struct {
	draftID uint64
	err     error
}

func (s *fakeKnowPostService) CreateDraft(_ context.Context, _ uint64) (uint64, error) {
	if s.err != nil {
		return 0, s.err
	}
	return s.draftID, nil
}

func (s *fakeKnowPostService) ConfirmContent(_ context.Context, _ uint64, _ uint64, _ service.KnowPostContentConfirmRequest) error {
	return s.err
}
