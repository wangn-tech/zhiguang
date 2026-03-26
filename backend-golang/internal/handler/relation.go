package handler

import (
	"net/http"
	"strconv"
	"strings"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

// RelationHandler 处理关注关系请求。
type RelationHandler struct {
	service service.RelationService
}

// NewRelationHandler 创建 RelationHandler。
func NewRelationHandler(service service.RelationService) *RelationHandler {
	return &RelationHandler{service: service}
}

// Follow 发起关注。
func (h *RelationHandler) Follow(c *gin.Context) {
	fromUserID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	toUserID, err := parseQueryUserID(c.Query("toUserId"), "toUserId")
	if err != nil {
		c.Error(err)
		return
	}

	changed, err := h.service.Follow(c.Request.Context(), fromUserID, toUserID)
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, changed)
}

// Unfollow 取消关注。
func (h *RelationHandler) Unfollow(c *gin.Context) {
	fromUserID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	toUserID, err := parseQueryUserID(c.Query("toUserId"), "toUserId")
	if err != nil {
		c.Error(err)
		return
	}

	changed, err := h.service.Unfollow(c.Request.Context(), fromUserID, toUserID)
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, changed)
}

// Status 查询关注关系三态。
func (h *RelationHandler) Status(c *gin.Context) {
	fromUserID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	toUserID, err := parseQueryUserID(c.Query("toUserId"), "toUserId")
	if err != nil {
		c.Error(err)
		return
	}

	status, err := h.service.Status(c.Request.Context(), fromUserID, toUserID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.RelationStatusResponse{
		Following:  status.Following,
		FollowedBy: status.FollowedBy,
		Mutual:     status.Mutual,
	})
}

// Following 返回关注列表。
func (h *RelationHandler) Following(c *gin.Context) {
	userID, err := parseQueryUserID(c.Query("userId"), "userId")
	if err != nil {
		c.Error(err)
		return
	}
	limit, err := parseOptionalInt(c.Query("limit"), 20, "limit")
	if err != nil {
		c.Error(err)
		return
	}
	offset, err := parseOptionalInt(c.Query("offset"), 0, "offset")
	if err != nil {
		c.Error(err)
		return
	}
	cursor, err := parseOptionalCursor(c.Query("cursor"))
	if err != nil {
		c.Error(err)
		return
	}

	profiles, err := h.service.FollowingProfiles(c.Request.Context(), userID, limit, offset, cursor)
	if err != nil {
		c.Error(err)
		return
	}

	resp := make([]dto.ProfileResponse, 0, len(profiles))
	for _, profile := range profiles {
		resp = append(resp, mapProfileResponse(profile))
	}
	c.JSON(http.StatusOK, resp)
}

// Followers 返回粉丝列表。
func (h *RelationHandler) Followers(c *gin.Context) {
	userID, err := parseQueryUserID(c.Query("userId"), "userId")
	if err != nil {
		c.Error(err)
		return
	}
	limit, err := parseOptionalInt(c.Query("limit"), 20, "limit")
	if err != nil {
		c.Error(err)
		return
	}
	offset, err := parseOptionalInt(c.Query("offset"), 0, "offset")
	if err != nil {
		c.Error(err)
		return
	}
	cursor, err := parseOptionalCursor(c.Query("cursor"))
	if err != nil {
		c.Error(err)
		return
	}

	profiles, err := h.service.FollowerProfiles(c.Request.Context(), userID, limit, offset, cursor)
	if err != nil {
		c.Error(err)
		return
	}

	resp := make([]dto.ProfileResponse, 0, len(profiles))
	for _, profile := range profiles {
		resp = append(resp, mapProfileResponse(profile))
	}
	c.JSON(http.StatusOK, resp)
}

// Counter 返回关系计数。
func (h *RelationHandler) Counter(c *gin.Context) {
	userID, err := parseQueryUserID(c.Query("userId"), "userId")
	if err != nil {
		c.Error(err)
		return
	}

	counters, err := h.service.Counters(c.Request.Context(), userID)
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, dto.RelationCountersResponse{
		Followings: counters.Followings,
		Followers:  counters.Followers,
		Posts:      counters.Posts,
		LikedPosts: counters.LikedPosts,
		FavedPosts: counters.FavedPosts,
	})
}

func parseQueryUserID(raw string, field string) (uint64, error) {
	value := strings.TrimSpace(raw)
	id, err := strconv.ParseUint(value, 10, 64)
	if err != nil || id == 0 {
		return 0, errorsx.New(errorsx.CodeBadRequest, field+" 非法")
	}
	return id, nil
}

func parseOptionalInt(raw string, defaultValue int, field string) (int, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return defaultValue, nil
	}
	value, err := strconv.Atoi(trimmed)
	if err != nil || value < 0 {
		return 0, errorsx.New(errorsx.CodeBadRequest, field+" 非法")
	}
	return value, nil
}

func parseOptionalCursor(raw string) (*int64, error) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return nil, nil
	}
	value, err := strconv.ParseInt(trimmed, 10, 64)
	if err != nil || value <= 0 {
		return nil, errorsx.New(errorsx.CodeBadRequest, "cursor 非法")
	}
	return &value, nil
}
