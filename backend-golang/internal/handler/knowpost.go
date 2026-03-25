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

// ConfirmContent 确认正文上传结果并写回对象信息。
func (h *KnowPostHandler) ConfirmContent(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	idRaw := strings.TrimSpace(c.Param("id"))
	postID, err := strconv.ParseUint(idRaw, 10, 64)
	if err != nil || postID == 0 {
		c.Error(errorsx.New(errorsx.CodeBadRequest, "id 非法"))
		return
	}

	var req dto.KnowPostContentConfirmRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	if err := h.service.ConfirmContent(c.Request.Context(), userID, postID, service.KnowPostContentConfirmRequest{
		ObjectKey: req.ObjectKey,
		ETag:      req.ETag,
		Size:      req.Size,
		SHA256:    req.SHA256,
	}); err != nil {
		c.Error(err)
		return
	}

	c.Status(http.StatusNoContent)
}
