package router

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"zhiguang/internal/handler"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

func TestRouter_RelationActionCounter_RoutesReachable(t *testing.T) {
	gin.SetMode(gin.TestMode)

	r := newRouterForRelationActionCounterTest()
	testCases := []struct {
		name   string
		method string
		path   string
		body   []byte
	}{
		{name: "relation follow", method: http.MethodPost, path: "/api/v1/relation/follow?toUserId=1002"},
		{name: "relation unfollow", method: http.MethodPost, path: "/api/v1/relation/unfollow?toUserId=1002"},
		{name: "relation status", method: http.MethodGet, path: "/api/v1/relation/status?toUserId=1002"},
		{name: "relation following", method: http.MethodGet, path: "/api/v1/relation/following?userId=1001&limit=20&offset=0"},
		{name: "relation followers", method: http.MethodGet, path: "/api/v1/relation/followers?userId=1002&limit=20&offset=0"},
		{name: "relation counter", method: http.MethodGet, path: "/api/v1/relation/counter?userId=1001"},
		{name: "action like", method: http.MethodPost, path: "/api/v1/action/like", body: mustJSON(t, map[string]any{"entityType": "knowpost", "entityId": "123"})},
		{name: "action unlike", method: http.MethodPost, path: "/api/v1/action/unlike", body: mustJSON(t, map[string]any{"entityType": "knowpost", "entityId": "123"})},
		{name: "action fav", method: http.MethodPost, path: "/api/v1/action/fav", body: mustJSON(t, map[string]any{"entityType": "knowpost", "entityId": "123"})},
		{name: "action unfav", method: http.MethodPost, path: "/api/v1/action/unfav", body: mustJSON(t, map[string]any{"entityType": "knowpost", "entityId": "123"})},
		{name: "counter get", method: http.MethodGet, path: "/api/v1/counter/knowpost/123?metrics=like,fav"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, bytes.NewReader(tc.body))
			if len(tc.body) > 0 {
				req.Header.Set("Content-Type", "application/json")
			}
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			if w.Code != http.StatusOK {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
			}
		})
	}
}

func TestRouter_RelationActionCounter_MethodMismatchReturns405(t *testing.T) {
	gin.SetMode(gin.TestMode)

	r := newRouterForRelationActionCounterTest()
	testCases := []struct {
		name   string
		method string
		path   string
	}{
		{name: "action like get", method: http.MethodGet, path: "/api/v1/action/like"},
		{name: "counter post", method: http.MethodPost, path: "/api/v1/counter/knowpost/123"},
		{name: "relation following post", method: http.MethodPost, path: "/api/v1/relation/following?userId=1001"},
		{name: "relation status delete", method: http.MethodDelete, path: "/api/v1/relation/status?toUserId=1002"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, nil)
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			if w.Code != http.StatusMethodNotAllowed {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusMethodNotAllowed)
			}
		})
	}
}

func TestRouter_RelationActionCounter_UnknownPathReturns404(t *testing.T) {
	gin.SetMode(gin.TestMode)

	r := newRouterForRelationActionCounterTest()
	testCases := []struct {
		name   string
		method string
		path   string
	}{
		{name: "relation unknown", method: http.MethodGet, path: "/api/v1/relation/unknown"},
		{name: "action unknown", method: http.MethodGet, path: "/api/v1/action/unknown"},
		{name: "counter unknown", method: http.MethodGet, path: "/api/v1/counter/knowpost"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, nil)
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			if w.Code != http.StatusNotFound {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusNotFound)
			}
		})
	}
}

func newRouterForRelationActionCounterTest() *gin.Engine {
	healthHandler := handler.NewHealthHandler(nil)
	authHandler := &handler.AuthHandler{}
	relationHandler := handler.NewRelationHandler(&routerRelationServiceStub{})
	counterService := &routerCounterServiceStub{}
	actionHandler := handler.NewActionHandler(counterService)
	counterHandler := handler.NewCounterHandler(counterService)

	authz := func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	}

	r := NewEngine(
		healthHandler,
		authHandler,
		nil,
		nil,
		nil,
		relationHandler,
		actionHandler,
		counterHandler,
		authz,
	)
	r.HandleMethodNotAllowed = true
	return r
}

func mustJSON(t *testing.T, payload any) []byte {
	t.Helper()
	data, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}
	return data
}

type routerRelationServiceStub struct{}

func (s *routerRelationServiceStub) Follow(_ context.Context, _ uint64, _ uint64) (bool, error) {
	return true, nil
}

func (s *routerRelationServiceStub) Unfollow(_ context.Context, _ uint64, _ uint64) (bool, error) {
	return false, nil
}

func (s *routerRelationServiceStub) Status(_ context.Context, _ uint64, _ uint64) (service.RelationStatus, error) {
	return service.RelationStatus{Following: true, FollowedBy: false, Mutual: false}, nil
}

func (s *routerRelationServiceStub) FollowingProfiles(_ context.Context, _ uint64, _ int, _ int, _ *int64) ([]service.ProfileResponse, error) {
	return []service.ProfileResponse{{ID: 1002, Nickname: "user-1002", Avatar: ""}}, nil
}

func (s *routerRelationServiceStub) FollowerProfiles(_ context.Context, _ uint64, _ int, _ int, _ *int64) ([]service.ProfileResponse, error) {
	return []service.ProfileResponse{{ID: 1001, Nickname: "user-1001", Avatar: ""}}, nil
}

func (s *routerRelationServiceStub) Counters(_ context.Context, _ uint64) (service.RelationCounters, error) {
	return service.RelationCounters{Followings: 1, Followers: 1, Posts: 0, LikedPosts: 0, FavedPosts: 0}, nil
}

type routerCounterServiceStub struct{}

func (s *routerCounterServiceStub) Like(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{Changed: true, Active: true}, nil
}

func (s *routerCounterServiceStub) Unlike(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{Changed: false, Active: false}, nil
}

func (s *routerCounterServiceStub) Fav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{Changed: true, Active: true}, nil
}

func (s *routerCounterServiceStub) Unfav(_ context.Context, _ uint64, _ string, _ string) (service.ActionResult, error) {
	return service.ActionResult{Changed: false, Active: false}, nil
}

func (s *routerCounterServiceStub) GetCounts(_ context.Context, _ string, _ string, _ []string) (map[string]int64, error) {
	return map[string]int64{"like": 1, "fav": 1}, nil
}
