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

	h := NewKnowPostHandler(&fakeKnowPostService{createErr: errors.New("db error")})

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

func TestKnowPostHandler_Feed_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{
		feedResp: service.KnowPostFeedPage{
			Items: []service.KnowPostFeedItem{
				{
					ID:             "10001",
					Title:          "Go 并发实践",
					Description:    "协程调度与模式",
					CoverImage:     "https://cdn.example.com/1.png",
					Tags:           []string{"Go", "并发"},
					TagJSON:        `["后端"]`,
					AuthorAvatar:   "https://cdn.example.com/a.png",
					AuthorNickname: "alice",
					LikeCount:      3,
					FavoriteCount:  1,
					Liked:          false,
					Faved:          false,
					IsTop:          false,
					Visible:        "public",
				},
			},
			Page:    1,
			Size:    20,
			HasMore: false,
		},
	})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/knowposts/feed", h.Feed)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/feed?page=1&size=20", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got := int(body["page"].(float64)); got != 1 {
		t.Fatalf("page = %d, want 1", got)
	}
	items, ok := body["items"].([]any)
	if !ok || len(items) != 1 {
		t.Fatalf("items len = %d, want 1", len(items))
	}
}

func TestKnowPostHandler_Feed_InvalidPage(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/knowposts/feed", h.Feed)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/feed?page=abc&size=20", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
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

func TestKnowPostHandler_PatchMetadata_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.PATCH("/api/v1/knowposts/:id", h.PatchMetadata)

	body := map[string]any{
		"title":       "Go 并发实践",
		"tagId":       12,
		"tags":        []string{"Go", "并发"},
		"imgUrls":     []string{"https://cdn.example.com/a.png"},
		"visible":     "public",
		"isTop":       false,
		"description": "并发模式整理",
	}
	payload, _ := json.Marshal(body)
	req := httptest.NewRequest(http.MethodPatch, "/api/v1/knowposts/1", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestKnowPostHandler_PatchMetadata_InvalidID(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.PATCH("/api/v1/knowposts/:id", h.PatchMetadata)

	req := httptest.NewRequest(http.MethodPatch, "/api/v1/knowposts/abc", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestKnowPostHandler_Publish_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/:id/publish", h.Publish)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/1/publish", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestKnowPostHandler_Publish_InvalidID(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewKnowPostHandler(&fakeKnowPostService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/knowposts/:id/publish", h.Publish)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/abc/publish", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

type fakeKnowPostService struct {
	draftID    uint64
	createErr  error
	confirmErr error
	patchErr   error
	publishErr error
	feedResp   service.KnowPostFeedPage
	feedErr    error
}

func (s *fakeKnowPostService) CreateDraft(_ context.Context, _ uint64) (uint64, error) {
	if s.createErr != nil {
		return 0, s.createErr
	}
	return s.draftID, nil
}

func (s *fakeKnowPostService) GetPublicFeed(_ context.Context, _ int, _ int) (service.KnowPostFeedPage, error) {
	if s.feedErr != nil {
		return service.KnowPostFeedPage{}, s.feedErr
	}
	return s.feedResp, nil
}

func (s *fakeKnowPostService) ConfirmContent(_ context.Context, _ uint64, _ uint64, _ service.KnowPostContentConfirmRequest) error {
	return s.confirmErr
}

func (s *fakeKnowPostService) UpdateMetadata(_ context.Context, _ uint64, _ uint64, _ service.KnowPostMetadataPatchRequest) error {
	return s.patchErr
}

func (s *fakeKnowPostService) Publish(_ context.Context, _ uint64, _ uint64) error {
	return s.publishErr
}
