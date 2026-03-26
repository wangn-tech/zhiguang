package handler

import (
	"net/http"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

// ActionHandler 处理点赞与收藏行为请求。
type ActionHandler struct {
	service service.CounterService
}

// NewActionHandler 创建 ActionHandler。
func NewActionHandler(service service.CounterService) *ActionHandler {
	return &ActionHandler{service: service}
}

// Like 点赞。
func (h *ActionHandler) Like(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.ActionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	result, err := h.service.Like(c.Request.Context(), userID, req.EntityType, req.EntityID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.ActionLikeResponse{Changed: result.Changed, Liked: result.Active})
}

// Unlike 取消点赞。
func (h *ActionHandler) Unlike(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.ActionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	result, err := h.service.Unlike(c.Request.Context(), userID, req.EntityType, req.EntityID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.ActionLikeResponse{Changed: result.Changed, Liked: result.Active})
}

// Fav 收藏。
func (h *ActionHandler) Fav(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.ActionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	result, err := h.service.Fav(c.Request.Context(), userID, req.EntityType, req.EntityID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.ActionFavResponse{Changed: result.Changed, Faved: result.Active})
}

// Unfav 取消收藏。
func (h *ActionHandler) Unfav(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.ActionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	result, err := h.service.Unfav(c.Request.Context(), userID, req.EntityType, req.EntityID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.ActionFavResponse{Changed: result.Changed, Faved: result.Active})
}
