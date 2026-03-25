package handler

import (
	"net/http"
	"strconv"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

// KnowPostHandler 处理知文主链路请求。
type KnowPostHandler struct {
	service service.KnowPostService
}

// NewKnowPostHandler 创建知文处理器。
func NewKnowPostHandler(service service.KnowPostService) *KnowPostHandler {
	return &KnowPostHandler{service: service}
}

// CreateDraft 创建当前用户草稿并返回新知文 ID。
func (h *KnowPostHandler) CreateDraft(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	postID, err := h.service.CreateDraft(c.Request.Context(), userID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.KnowPostDraftCreateResponse{ID: strconv.FormatUint(postID, 10)})
}
