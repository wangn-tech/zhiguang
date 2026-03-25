package handler

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/internal/middleware"

	"github.com/gin-gonic/gin"
)

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
