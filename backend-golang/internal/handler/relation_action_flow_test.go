package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sort"
	"strconv"
	"strings"
	"sync"
	"testing"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestActionCounterFlow_Sequence(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowCounterService()
	actionHandler := NewActionHandler(svc)
	counterHandler := NewCounterHandler(svc)

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/action/like", actionHandler.Like)
	r.POST("/api/v1/action/unlike", actionHandler.Unlike)
	r.POST("/api/v1/action/fav", actionHandler.Fav)
	r.POST("/api/v1/action/unfav", actionHandler.Unfav)
	r.GET("/api/v1/counter/:entityType/:entityId", counterHandler.GetCounts)

	postAction := func(path string) map[string]any {
		payload, _ := json.Marshal(map[string]any{"entityType": "knowpost", "entityId": "2001"})
		req := httptest.NewRequest(http.MethodPost, path, bytes.NewReader(payload))
		req.Header.Set("Content-Type", "application/json")
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		var resp map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode %s response: %v", path, err)
		}
		return resp
	}

	queryCounts := func() map[string]any {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/2001?metrics=like,fav", nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("counter status = %d, want %d", w.Code, http.StatusOK)
		}
		var resp map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode counter response: %v", err)
		}
		return resp
	}

	like := postAction("/api/v1/action/like")
	if changed, _ := like["changed"].(bool); !changed {
		t.Fatalf("first like changed = %v, want true", changed)
	}
	if liked, _ := like["liked"].(bool); !liked {
		t.Fatalf("first like liked = %v, want true", liked)
	}

	countResp := queryCounts()
	counts := countResp["counts"].(map[string]any)
	if got := int64(counts["like"].(float64)); got != 1 {
		t.Fatalf("like count after first like = %d, want 1", got)
	}
	if got := int64(counts["fav"].(float64)); got != 0 {
		t.Fatalf("fav count after first like = %d, want 0", got)
	}

	likeAgain := postAction("/api/v1/action/like")
	if changed, _ := likeAgain["changed"].(bool); changed {
		t.Fatalf("second like changed = %v, want false", changed)
	}
	if liked, _ := likeAgain["liked"].(bool); !liked {
		t.Fatalf("second like liked = %v, want true", liked)
	}

	unlike := postAction("/api/v1/action/unlike")
	if changed, _ := unlike["changed"].(bool); !changed {
		t.Fatalf("first unlike changed = %v, want true", changed)
	}
	if liked, _ := unlike["liked"].(bool); liked {
		t.Fatalf("first unlike liked = %v, want false", liked)
	}

	unlikeAgain := postAction("/api/v1/action/unlike")
	if changed, _ := unlikeAgain["changed"].(bool); changed {
		t.Fatalf("second unlike changed = %v, want false", changed)
	}
	if liked, _ := unlikeAgain["liked"].(bool); liked {
		t.Fatalf("second unlike liked = %v, want false", liked)
	}

	fav := postAction("/api/v1/action/fav")
	if changed, _ := fav["changed"].(bool); !changed {
		t.Fatalf("first fav changed = %v, want true", changed)
	}
	if faved, _ := fav["faved"].(bool); !faved {
		t.Fatalf("first fav faved = %v, want true", faved)
	}

	unfav := postAction("/api/v1/action/unfav")
	if changed, _ := unfav["changed"].(bool); !changed {
		t.Fatalf("first unfav changed = %v, want true", changed)
	}
	if faved, _ := unfav["faved"].(bool); faved {
		t.Fatalf("first unfav faved = %v, want false", faved)
	}

	countResp = queryCounts()
	counts = countResp["counts"].(map[string]any)
	if got := int64(counts["like"].(float64)); got != 0 {
		t.Fatalf("final like count = %d, want 0", got)
	}
	if got := int64(counts["fav"].(float64)); got != 0 {
		t.Fatalf("final fav count = %d, want 0", got)
	}
}

func TestRelationFlow_SequenceFollowStatusCounterAndList(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowRelationService(map[uint64]service.ProfileResponse{
		1001: {ID: 1001, Nickname: "user-1001", Avatar: ""},
		1002: {ID: 1002, Nickname: "user-1002", Avatar: ""},
	})
	h := NewRelationHandler(svc)

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)
	r.POST("/api/v1/relation/unfollow", h.Unfollow)
	r.GET("/api/v1/relation/status", h.Status)
	r.GET("/api/v1/relation/following", h.Following)
	r.GET("/api/v1/relation/followers", h.Followers)
	r.GET("/api/v1/relation/counter", h.Counter)

	follow := func(path string) bool {
		req := httptest.NewRequest(http.MethodPost, path, nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		return stringsTrimBody(w.Body.String()) == "true"
	}

	queryStatus := func() map[string]any {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("status endpoint status = %d, want %d", w.Code, http.StatusOK)
		}
		var resp map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode status response: %v", err)
		}
		return resp
	}

	queryCounter := func(userID uint64) map[string]any {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/counter?userId="+strconv.FormatUint(userID, 10), nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("counter endpoint status = %d, want %d", w.Code, http.StatusOK)
		}
		var resp map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode counter response: %v", err)
		}
		return resp
	}

	queryProfiles := func(path string) []map[string]any {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		var resp []map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode %s response: %v", path, err)
		}
		return resp
	}

	if changed := follow("/api/v1/relation/follow?toUserId=1002"); !changed {
		t.Fatal("first follow changed = false, want true")
	}
	if changed := follow("/api/v1/relation/follow?toUserId=1002"); changed {
		t.Fatal("second follow changed = true, want false")
	}

	status := queryStatus()
	if following, _ := status["following"].(bool); !following {
		t.Fatalf("status.following = %v, want true", following)
	}

	counter := queryCounter(1001)
	if got := int64(counter["followings"].(float64)); got != 1 {
		t.Fatalf("followings = %d, want 1", got)
	}

	followingList := queryProfiles("/api/v1/relation/following?userId=1001&limit=20&offset=0")
	if len(followingList) != 1 {
		t.Fatalf("len(followingList) = %d, want 1", len(followingList))
	}
	if got := uint64(followingList[0]["id"].(float64)); got != 1002 {
		t.Fatalf("following first id = %d, want 1002", got)
	}

	followerList := queryProfiles("/api/v1/relation/followers?userId=1002&limit=20&offset=0")
	if len(followerList) != 1 {
		t.Fatalf("len(followerList) = %d, want 1", len(followerList))
	}
	if got := uint64(followerList[0]["id"].(float64)); got != 1001 {
		t.Fatalf("follower first id = %d, want 1001", got)
	}

	if changed := follow("/api/v1/relation/unfollow?toUserId=1002"); !changed {
		t.Fatal("first unfollow changed = false, want true")
	}
	if changed := follow("/api/v1/relation/unfollow?toUserId=1002"); changed {
		t.Fatal("second unfollow changed = true, want false")
	}

	status = queryStatus()
	if following, _ := status["following"].(bool); following {
		t.Fatalf("status.following after unfollow = %v, want false", following)
	}

	counter = queryCounter(1001)
	if got := int64(counter["followings"].(float64)); got != 0 {
		t.Fatalf("followings after unfollow = %d, want 0", got)
	}
}

func TestRelationFlow_FollowingCursorExclusive(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowRelationService(map[uint64]service.ProfileResponse{
		1001: {ID: 1001, Nickname: "user-1001", Avatar: ""},
		1002: {ID: 1002, Nickname: "user-1002", Avatar: ""},
		1003: {ID: 1003, Nickname: "user-1003", Avatar: ""},
	})
	h := NewRelationHandler(svc)

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)
	r.GET("/api/v1/relation/following", h.Following)

	for _, toUserID := range []uint64{1002, 1003} {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId="+strconv.FormatUint(toUserID, 10), nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("follow %d status = %d, want %d", toUserID, w.Code, http.StatusOK)
		}
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&limit=1&offset=0", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("first page status = %d, want %d", w.Code, http.StatusOK)
	}
	var firstPage []map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &firstPage); err != nil {
		t.Fatalf("decode first page: %v", err)
	}
	if len(firstPage) != 1 {
		t.Fatalf("len(firstPage) = %d, want 1", len(firstPage))
	}
	if got := uint64(firstPage[0]["id"].(float64)); got != 1003 {
		t.Fatalf("first page id = %d, want 1003", got)
	}

	cursor := svc.snapshotScore(1001, 1003)
	cursorReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001&limit=20&offset=0&cursor="+strconv.FormatInt(cursor, 10), nil)
	cursorW := httptest.NewRecorder()
	r.ServeHTTP(cursorW, cursorReq)
	if cursorW.Code != http.StatusOK {
		t.Fatalf("cursor page status = %d, want %d", cursorW.Code, http.StatusOK)
	}
	var cursorPage []map[string]any
	if err := json.Unmarshal(cursorW.Body.Bytes(), &cursorPage); err != nil {
		t.Fatalf("decode cursor page: %v", err)
	}
	if len(cursorPage) != 1 {
		t.Fatalf("len(cursorPage) = %d, want 1", len(cursorPage))
	}
	if got := uint64(cursorPage[0]["id"].(float64)); got != 1002 {
		t.Fatalf("cursor page id = %d, want 1002", got)
	}
}

type flowCounterService struct {
	mu      sync.Mutex
	actions map[string]map[string]map[uint64]struct{}
}

func newFlowCounterService() *flowCounterService {
	return &flowCounterService{actions: map[string]map[string]map[uint64]struct{}{}}
}

func (s *flowCounterService) Like(_ context.Context, userID uint64, entityType string, entityID string) (service.ActionResult, error) {
	return s.activate(userID, entityType, entityID, "like")
}

func (s *flowCounterService) Unlike(_ context.Context, userID uint64, entityType string, entityID string) (service.ActionResult, error) {
	return s.deactivate(userID, entityType, entityID, "like")
}

func (s *flowCounterService) Fav(_ context.Context, userID uint64, entityType string, entityID string) (service.ActionResult, error) {
	return s.activate(userID, entityType, entityID, "fav")
}

func (s *flowCounterService) Unfav(_ context.Context, userID uint64, entityType string, entityID string) (service.ActionResult, error) {
	return s.deactivate(userID, entityType, entityID, "fav")
}

func (s *flowCounterService) GetCounts(_ context.Context, entityType string, entityID string, metrics []string) (map[string]int64, error) {
	if strings.TrimSpace(entityType) == "" || strings.TrimSpace(entityID) == "" {
		return nil, errorsx.New(errorsx.CodeBadRequest, "entityType/entityId 不能为空")
	}

	targetMetrics := normalizeFlowMetrics(metrics)
	entityKey := flowEntityKey(entityType, entityID)

	s.mu.Lock()
	defer s.mu.Unlock()

	result := make(map[string]int64, len(targetMetrics))
	for _, metric := range targetMetrics {
		users := s.actions[metric][entityKey]
		result[metric] = int64(len(users))
	}
	return result, nil
}

func (s *flowCounterService) activate(userID uint64, entityType string, entityID string, metric string) (service.ActionResult, error) {
	if userID == 0 {
		return service.ActionResult{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}
	entityKey := flowEntityKey(entityType, entityID)

	s.mu.Lock()
	defer s.mu.Unlock()

	s.ensureMetric(metric)
	users := s.actions[metric][entityKey]
	if users == nil {
		users = map[uint64]struct{}{}
		s.actions[metric][entityKey] = users
	}
	_, existed := users[userID]
	users[userID] = struct{}{}
	return service.ActionResult{Changed: !existed, Active: true}, nil
}

func (s *flowCounterService) deactivate(userID uint64, entityType string, entityID string, metric string) (service.ActionResult, error) {
	if userID == 0 {
		return service.ActionResult{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}
	entityKey := flowEntityKey(entityType, entityID)

	s.mu.Lock()
	defer s.mu.Unlock()

	s.ensureMetric(metric)
	users := s.actions[metric][entityKey]
	if users == nil {
		return service.ActionResult{Changed: false, Active: false}, nil
	}
	_, existed := users[userID]
	delete(users, userID)
	return service.ActionResult{Changed: existed, Active: false}, nil
}

func (s *flowCounterService) ensureMetric(metric string) {
	if s.actions[metric] == nil {
		s.actions[metric] = map[string]map[uint64]struct{}{}
	}
}

func normalizeFlowMetrics(metrics []string) []string {
	if len(metrics) == 0 {
		return []string{"like", "fav"}
	}

	set := map[string]struct{}{}
	for _, metric := range metrics {
		normalized := strings.ToLower(strings.TrimSpace(metric))
		if normalized == "like" || normalized == "fav" {
			set[normalized] = struct{}{}
		}
	}
	if len(set) == 0 {
		return []string{"like", "fav"}
	}

	ordered := make([]string, 0, 2)
	if _, ok := set["like"]; ok {
		ordered = append(ordered, "like")
	}
	if _, ok := set["fav"]; ok {
		ordered = append(ordered, "fav")
	}
	return ordered
}

func flowEntityKey(entityType string, entityID string) string {
	return strings.ToLower(strings.TrimSpace(entityType)) + ":" + strings.TrimSpace(entityID)
}

type flowRelationService struct {
	mu        sync.Mutex
	seq       int64
	following map[uint64]map[uint64]int64
	followers map[uint64]map[uint64]int64
	profiles  map[uint64]service.ProfileResponse
}

func newFlowRelationService(profiles map[uint64]service.ProfileResponse) *flowRelationService {
	return &flowRelationService{
		seq:       0,
		following: map[uint64]map[uint64]int64{},
		followers: map[uint64]map[uint64]int64{},
		profiles:  profiles,
	}
}

func (s *flowRelationService) Follow(_ context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	if fromUserID == 0 || toUserID == 0 || fromUserID == toUserID {
		return false, errorsx.New(errorsx.CodeBadRequest, "用户标识非法")
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	s.ensureUserMaps(fromUserID, toUserID)
	if _, ok := s.following[fromUserID][toUserID]; ok {
		return false, nil
	}

	s.seq++
	s.following[fromUserID][toUserID] = s.seq
	s.followers[toUserID][fromUserID] = s.seq
	return true, nil
}

func (s *flowRelationService) Unfollow(_ context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	if fromUserID == 0 || toUserID == 0 || fromUserID == toUserID {
		return false, errorsx.New(errorsx.CodeBadRequest, "用户标识非法")
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	s.ensureUserMaps(fromUserID, toUserID)
	if _, ok := s.following[fromUserID][toUserID]; !ok {
		return false, nil
	}

	delete(s.following[fromUserID], toUserID)
	delete(s.followers[toUserID], fromUserID)
	return true, nil
}

func (s *flowRelationService) Status(_ context.Context, fromUserID uint64, toUserID uint64) (service.RelationStatus, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, following := s.following[fromUserID][toUserID]
	_, followedBy := s.following[toUserID][fromUserID]
	return service.RelationStatus{Following: following, FollowedBy: followedBy, Mutual: following && followedBy}, nil
}

func (s *flowRelationService) FollowingProfiles(_ context.Context, userID uint64, limit int, offset int, cursor *int64) ([]service.ProfileResponse, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.listProfiles(s.following[userID], limit, offset, cursor), nil
}

func (s *flowRelationService) FollowerProfiles(_ context.Context, userID uint64, limit int, offset int, cursor *int64) ([]service.ProfileResponse, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.listProfiles(s.followers[userID], limit, offset, cursor), nil
}

func (s *flowRelationService) Counters(_ context.Context, userID uint64) (service.RelationCounters, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	return service.RelationCounters{
		Followings: int64(len(s.following[userID])),
		Followers:  int64(len(s.followers[userID])),
		Posts:      0,
		LikedPosts: 0,
		FavedPosts: 0,
	}, nil
}

func (s *flowRelationService) snapshotScore(fromUserID uint64, toUserID uint64) int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.following[fromUserID][toUserID]
}

func (s *flowRelationService) ensureUserMaps(fromUserID uint64, toUserID uint64) {
	if s.following[fromUserID] == nil {
		s.following[fromUserID] = map[uint64]int64{}
	}
	if s.followers[toUserID] == nil {
		s.followers[toUserID] = map[uint64]int64{}
	}
	if s.following[toUserID] == nil {
		s.following[toUserID] = map[uint64]int64{}
	}
	if s.followers[fromUserID] == nil {
		s.followers[fromUserID] = map[uint64]int64{}
	}
}

func (s *flowRelationService) listProfiles(entries map[uint64]int64, limit int, offset int, cursor *int64) []service.ProfileResponse {
	if limit <= 0 {
		limit = 20
	}

	type pair struct {
		id    uint64
		score int64
	}
	pairs := make([]pair, 0, len(entries))
	for id, score := range entries {
		if cursor != nil && score >= *cursor {
			continue
		}
		pairs = append(pairs, pair{id: id, score: score})
	}
	sort.Slice(pairs, func(i int, j int) bool {
		if pairs[i].score == pairs[j].score {
			return pairs[i].id > pairs[j].id
		}
		return pairs[i].score > pairs[j].score
	})

	if offset >= len(pairs) {
		return []service.ProfileResponse{}
	}

	end := offset + limit
	if end > len(pairs) {
		end = len(pairs)
	}

	result := make([]service.ProfileResponse, 0, end-offset)
	for _, item := range pairs[offset:end] {
		profile, ok := s.profiles[item.id]
		if !ok {
			profile = service.ProfileResponse{ID: item.id, Nickname: fmt.Sprintf("user-%d", item.id), Avatar: ""}
		}
		result = append(result, profile)
	}
	return result
}

func TestRelationFlow_FollowingPaginationBoundaryWithCursorOffsetLimit(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowRelationService(map[uint64]service.ProfileResponse{
		1001: {ID: 1001, Nickname: "user-1001", Avatar: ""},
		1002: {ID: 1002, Nickname: "user-1002", Avatar: ""},
		1003: {ID: 1003, Nickname: "user-1003", Avatar: ""},
		1004: {ID: 1004, Nickname: "user-1004", Avatar: ""},
		1005: {ID: 1005, Nickname: "user-1005", Avatar: ""},
		1006: {ID: 1006, Nickname: "user-1006", Avatar: ""},
	})
	h := NewRelationHandler(svc)

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)
	r.GET("/api/v1/relation/following", h.Following)

	for _, toUserID := range []uint64{1002, 1003, 1004, 1005, 1006} {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId="+strconv.FormatUint(toUserID, 10), nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("follow %d status = %d, want %d", toUserID, w.Code, http.StatusOK)
		}
	}

	queryIDs := func(path string) []uint64 {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		var resp []map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode %s response: %v", path, err)
		}
		ids := make([]uint64, 0, len(resp))
		for _, item := range resp {
			ids = append(ids, uint64(item["id"].(float64)))
		}
		return ids
	}

	firstPage := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0")
	if len(firstPage) != 2 || firstPage[0] != 1006 || firstPage[1] != 1005 {
		t.Fatalf("firstPage = %v, want [1006 1005]", firstPage)
	}

	cursor1005 := svc.snapshotScore(1001, 1005)
	secondPage := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(cursor1005, 10))
	if len(secondPage) != 2 || secondPage[0] != 1004 || secondPage[1] != 1003 {
		t.Fatalf("secondPage = %v, want [1004 1003]", secondPage)
	}

	cursorOffsetPage := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=1&cursor=" + strconv.FormatInt(cursor1005, 10))
	if len(cursorOffsetPage) != 2 || cursorOffsetPage[0] != 1003 || cursorOffsetPage[1] != 1002 {
		t.Fatalf("cursorOffsetPage = %v, want [1003 1002]", cursorOffsetPage)
	}

	cursor1002 := svc.snapshotScore(1001, 1002)
	emptyTailPage := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(cursor1002, 10))
	if len(emptyTailPage) != 0 {
		t.Fatalf("emptyTailPage = %v, want []", emptyTailPage)
	}

	emptyOffsetPage := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=10")
	if len(emptyOffsetPage) != 0 {
		t.Fatalf("emptyOffsetPage = %v, want []", emptyOffsetPage)
	}
}

func TestRelationFlow_FollowersPaginationBoundaryWithCursorOffsetLimit(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowRelationService(map[uint64]service.ProfileResponse{
		1001: {ID: 1001, Nickname: "user-1001", Avatar: ""},
		1002: {ID: 1002, Nickname: "user-1002", Avatar: ""},
		1003: {ID: 1003, Nickname: "user-1003", Avatar: ""},
		1004: {ID: 1004, Nickname: "user-1004", Avatar: ""},
		1005: {ID: 1005, Nickname: "user-1005", Avatar: ""},
		1006: {ID: 1006, Nickname: "user-1006", Avatar: ""},
	})
	h := NewRelationHandler(svc)

	for _, fromUserID := range []uint64{1002, 1003, 1004, 1005, 1006} {
		changed, err := svc.Follow(context.Background(), fromUserID, 1001)
		if err != nil {
			t.Fatalf("seed follow %d -> 1001 error: %v", fromUserID, err)
		}
		if !changed {
			t.Fatalf("seed follow %d -> 1001 changed = false, want true", fromUserID)
		}
	}

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.GET("/api/v1/relation/followers", h.Followers)

	queryIDs := func(path string) []uint64 {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		var resp []map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode %s response: %v", path, err)
		}
		ids := make([]uint64, 0, len(resp))
		for _, item := range resp {
			ids = append(ids, uint64(item["id"].(float64)))
		}
		return ids
	}

	firstPage := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0")
	if len(firstPage) != 2 || firstPage[0] != 1006 || firstPage[1] != 1005 {
		t.Fatalf("firstPage = %v, want [1006 1005]", firstPage)
	}

	cursor1005 := svc.snapshotScore(1005, 1001)
	secondPage := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(cursor1005, 10))
	if len(secondPage) != 2 || secondPage[0] != 1004 || secondPage[1] != 1003 {
		t.Fatalf("secondPage = %v, want [1004 1003]", secondPage)
	}

	cursorOffsetPage := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=1&cursor=" + strconv.FormatInt(cursor1005, 10))
	if len(cursorOffsetPage) != 2 || cursorOffsetPage[0] != 1003 || cursorOffsetPage[1] != 1002 {
		t.Fatalf("cursorOffsetPage = %v, want [1003 1002]", cursorOffsetPage)
	}

	cursor1002 := svc.snapshotScore(1002, 1001)
	emptyTailPage := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(cursor1002, 10))
	if len(emptyTailPage) != 0 {
		t.Fatalf("emptyTailPage = %v, want []", emptyTailPage)
	}

	emptyOffsetPage := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=10")
	if len(emptyOffsetPage) != 0 {
		t.Fatalf("emptyOffsetPage = %v, want []", emptyOffsetPage)
	}
}

func TestRelationFlow_FollowingAndFollowers_ConsistentPaginationSemantics(t *testing.T) {
	gin.SetMode(gin.TestMode)

	svc := newFlowRelationService(map[uint64]service.ProfileResponse{
		1001: {ID: 1001, Nickname: "user-1001", Avatar: ""},
		1002: {ID: 1002, Nickname: "user-1002", Avatar: ""},
		1003: {ID: 1003, Nickname: "user-1003", Avatar: ""},
		1004: {ID: 1004, Nickname: "user-1004", Avatar: ""},
		1005: {ID: 1005, Nickname: "user-1005", Avatar: ""},
		1006: {ID: 1006, Nickname: "user-1006", Avatar: ""},
		1102: {ID: 1102, Nickname: "user-1102", Avatar: ""},
		1103: {ID: 1103, Nickname: "user-1103", Avatar: ""},
		1104: {ID: 1104, Nickname: "user-1104", Avatar: ""},
		1105: {ID: 1105, Nickname: "user-1105", Avatar: ""},
		1106: {ID: 1106, Nickname: "user-1106", Avatar: ""},
	})
	h := NewRelationHandler(svc)

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	r.Use(func(c *gin.Context) {
		c.Set("auth_user_id", uint64(1001))
		c.Next()
	})
	r.POST("/api/v1/relation/follow", h.Follow)
	r.GET("/api/v1/relation/following", h.Following)
	r.GET("/api/v1/relation/followers", h.Followers)

	for _, toUserID := range []uint64{1002, 1003, 1004, 1005, 1006} {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId="+strconv.FormatUint(toUserID, 10), nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("follow %d status = %d, want %d", toUserID, w.Code, http.StatusOK)
		}
	}
	for _, fromUserID := range []uint64{1102, 1103, 1104, 1105, 1106} {
		changed, err := svc.Follow(context.Background(), fromUserID, 1001)
		if err != nil {
			t.Fatalf("seed follower %d -> 1001 error: %v", fromUserID, err)
		}
		if !changed {
			t.Fatalf("seed follower %d -> 1001 changed = false, want true", fromUserID)
		}
	}

	queryIDs := func(path string) []uint64 {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		w := httptest.NewRecorder()
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s status = %d, want %d", path, w.Code, http.StatusOK)
		}
		var resp []map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decode %s response: %v", path, err)
		}
		ids := make([]uint64, 0, len(resp))
		for _, item := range resp {
			ids = append(ids, uint64(item["id"].(float64)))
		}
		return ids
	}

	assertSlice := func(label string, got []uint64, want []uint64) {
		if len(got) != len(want) {
			t.Fatalf("%s len = %d, want %d; got=%v want=%v", label, len(got), len(want), got, want)
		}
		for i := range want {
			if got[i] != want[i] {
				t.Fatalf("%s = %v, want %v", label, got, want)
			}
		}
	}

	followingFirst := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0")
	followersFirst := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0")
	assertSlice("followingFirst", followingFirst, []uint64{1006, 1005})
	assertSlice("followersFirst", followersFirst, []uint64{1106, 1105})

	followingCursor := svc.snapshotScore(1001, 1005)
	followersCursor := svc.snapshotScore(1105, 1001)

	followingSecond := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(followingCursor, 10))
	followersSecond := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(followersCursor, 10))
	assertSlice("followingSecond", followingSecond, []uint64{1004, 1003})
	assertSlice("followersSecond", followersSecond, []uint64{1104, 1103})

	followingCursorOffset := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=1&cursor=" + strconv.FormatInt(followingCursor, 10))
	followersCursorOffset := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=1&cursor=" + strconv.FormatInt(followersCursor, 10))
	assertSlice("followingCursorOffset", followingCursorOffset, []uint64{1003, 1002})
	assertSlice("followersCursorOffset", followersCursorOffset, []uint64{1103, 1102})

	followingTailCursor := svc.snapshotScore(1001, 1002)
	followersTailCursor := svc.snapshotScore(1102, 1001)

	followingTail := queryIDs("/api/v1/relation/following?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(followingTailCursor, 10))
	followersTail := queryIDs("/api/v1/relation/followers?userId=1001&limit=2&offset=0&cursor=" + strconv.FormatInt(followersTailCursor, 10))
	assertSlice("followingTail", followingTail, []uint64{})
	assertSlice("followersTail", followersTail, []uint64{})
}
