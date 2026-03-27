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

	"github.com/gin-gonic/gin"
)

func TestRelationHandler_Follow_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{followChanged: true})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId=1002", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
	if got := stringsTrimBody(w.Body.String()); got != "true" {
		t.Fatalf("body = %s, want true", got)
	}
}

func TestRelationHandler_Unfollow_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{unfollowChanged: false})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/unfollow", h.Unfollow)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/unfollow?toUserId=1002", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
	if got := stringsTrimBody(w.Body.String()); got != "false" {
		t.Fatalf("body = %s, want false", got)
	}
}

func TestRelationHandler_Status_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{status: service.RelationStatus{Following: true, FollowedBy: false, Mutual: false}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.GET("/api/v1/relation/status", h.Status)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if following, _ := resp["following"].(bool); !following {
		t.Fatalf("following = %v, want true", following)
	}
}

func TestRelationHandler_Following_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{profiles: []service.ProfileResponse{{ID: 1002, Nickname: "alice", Avatar: ""}}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/following", h.Following)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&limit=20&offset=0", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp []map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(resp) != 1 {
		t.Fatalf("len = %d, want 1", len(resp))
	}
}

func TestRelationHandler_Following_InvalidCursor(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/following", h.Following)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&cursor=0", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestRelationHandler_Followers_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{profiles: []service.ProfileResponse{{ID: 1003, Nickname: "bob", Avatar: ""}}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/followers", h.Followers)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/followers?userId=1001&limit=20&offset=0", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp []map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(resp) != 1 {
		t.Fatalf("len = %d, want 1", len(resp))
	}
}

func TestRelationHandler_Counter_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{counters: service.RelationCounters{Followings: 1, Followers: 2, Posts: 3, LikedPosts: 4, FavedPosts: 5}})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/counter", h.Counter)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/counter?userId=1001", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got := int64(resp["posts"].(float64)); got != 3 {
		t.Fatalf("posts = %d, want 3", got)
	}
}

func TestRelationHandler_Follow_InvalidUser(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId=abc", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

type fakeRelationService struct {
	followChanged   bool
	unfollowChanged bool
	status          service.RelationStatus
	profiles        []service.ProfileResponse
	counters        service.RelationCounters
	err             error
}

func (s *fakeRelationService) Follow(_ context.Context, _ uint64, _ uint64) (bool, error) {
	if s.err != nil {
		return false, s.err
	}
	return s.followChanged, nil
}

func (s *fakeRelationService) Unfollow(_ context.Context, _ uint64, _ uint64) (bool, error) {
	if s.err != nil {
		return false, s.err
	}
	return s.unfollowChanged, nil
}

func (s *fakeRelationService) Status(_ context.Context, _ uint64, _ uint64) (service.RelationStatus, error) {
	if s.err != nil {
		return service.RelationStatus{}, s.err
	}
	return s.status, nil
}

func (s *fakeRelationService) FollowingProfiles(_ context.Context, _ uint64, _ int, _ int, _ *int64) ([]service.ProfileResponse, error) {
	if s.err != nil {
		return nil, s.err
	}
	return s.profiles, nil
}

func (s *fakeRelationService) FollowerProfiles(_ context.Context, _ uint64, _ int, _ int, _ *int64) ([]service.ProfileResponse, error) {
	if s.err != nil {
		return nil, s.err
	}
	return s.profiles, nil
}

func (s *fakeRelationService) Counters(_ context.Context, _ uint64) (service.RelationCounters, error) {
	if s.err != nil {
		return service.RelationCounters{}, s.err
	}
	return s.counters, nil
}

func stringsTrimBody(raw string) string {
	return strings.TrimSpace(raw)
}

func TestRelationHandler_Follow_InvalidUser_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId=abc", nil)
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

func TestRelationHandler_Following_InvalidCursor_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/following", h.Following)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&cursor=0", nil)
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

func TestRelationHandler_Follow_MissingAuth_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.POST("/api/v1/relation/follow", h.Follow)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId=1002", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}

	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if code, _ := resp["code"].(string); code != "INVALID_CREDENTIALS" {
		t.Fatalf("code = %s, want INVALID_CREDENTIALS", code)
	}
}

func TestRelationHandler_Following_InvalidLimit_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/following", h.Following)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&limit=-1", nil)
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

func TestRelationHandler_Following_InvalidOffset_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/following", h.Following)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&offset=-1", nil)
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

func TestRelationHandler_Counter_InvalidUserID_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewRelationHandler(&fakeRelationService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/counter", h.Counter)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/counter?userId=0", nil)
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
