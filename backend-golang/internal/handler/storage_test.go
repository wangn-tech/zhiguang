package handler

import (
	"context"
	"net/http"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestStorageHandler_Presign_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewStorageHandler(&fakeStoragePresignService{
		resp: service.StoragePresignResponse{
			ObjectKey: "posts/123/content.md",
			PutURL:    "https://oss.example.com/upload",
			Headers:   map[string]string{"Content-Type": "text/markdown"},
			ExpiresIn: 600,
		},
	})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/storage/presign", h.Presign)

	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/storage/presign", map[string]any{
		"scene":       "knowpost_content",
		"postId":      "123",
		"contentType": "text/markdown",
		"ext":         ".md",
	}, "")
	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var body map[string]any
	mustDecodeJSON(t, w.Body.Bytes(), &body)
	if got, _ := body["objectKey"].(string); got != "posts/123/content.md" {
		t.Fatalf("objectKey = %s, want posts/123/content.md", got)
	}
}

func TestStorageHandler_Presign_InvalidBody(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewStorageHandler(&fakeStoragePresignService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/storage/presign", h.Presign)

	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/storage/presign", map[string]any{}, "")
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestStorageHandler_Presign_ServiceError(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewStorageHandler(&fakeStoragePresignService{err: errorsx.New(errorsx.CodeBadRequest, "scene 非法")})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/storage/presign", h.Presign)

	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/storage/presign", map[string]any{
		"scene":       "knowpost_content",
		"postId":      "123",
		"contentType": "text/markdown",
		"ext":         ".md",
	}, "")
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

type fakeStoragePresignService struct {
	resp service.StoragePresignResponse
	err  error
}

func (s *fakeStoragePresignService) Presign(_ context.Context, _ uint64, _ service.StoragePresignRequest) (service.StoragePresignResponse, error) {
	if s.err != nil {
		return service.StoragePresignResponse{}, s.err
	}
	return s.resp, nil
}
