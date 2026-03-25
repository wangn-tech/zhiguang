package handler

import (
	"net/http"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

// StorageHandler 处理对象存储预签名请求。
type StorageHandler struct {
	service service.StoragePresignService
}

// NewStorageHandler 创建对象存储处理器。
func NewStorageHandler(service service.StoragePresignService) *StorageHandler {
	return &StorageHandler{service: service}
}

// Presign 生成直传预签名 URL。
func (h *StorageHandler) Presign(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.StoragePresignRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.service.Presign(c.Request.Context(), userID, service.StoragePresignRequest{
		Scene:       req.Scene,
		PostID:      req.PostID,
		ContentType: req.ContentType,
		Ext:         req.Ext,
	})
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.StoragePresignResponse{
		ObjectKey: resp.ObjectKey,
		PutURL:    resp.PutURL,
		Headers:   resp.Headers,
		ExpiresIn: resp.ExpiresIn,
	})
}
