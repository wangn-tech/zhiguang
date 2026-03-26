package handler

import (
	"net/http"
	"strings"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

// CounterHandler 处理计数读取请求。
type CounterHandler struct {
	service service.CounterService
}

// NewCounterHandler 创建 CounterHandler。
func NewCounterHandler(service service.CounterService) *CounterHandler {
	return &CounterHandler{service: service}
}

// GetCounts 返回实体在指定指标上的计数值。
func (h *CounterHandler) GetCounts(c *gin.Context) {
	entityType := c.Param("entityType")
	entityID := c.Param("entityId")
	metrics := parseMetrics(c.Query("metrics"))

	counts, err := h.service.GetCounts(c.Request.Context(), entityType, entityID, metrics)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.CounterCountsResponse{
		EntityType: strings.ToLower(strings.TrimSpace(entityType)),
		EntityID:   strings.TrimSpace(entityID),
		Counts:     counts,
	})
}

func parseMetrics(raw string) []string {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return nil
	}
	parts := strings.Split(trimmed, ",")
	metrics := make([]string, 0, len(parts))
	for _, part := range parts {
		value := strings.TrimSpace(part)
		if value != "" {
			metrics = append(metrics, value)
		}
	}
	return metrics
}
