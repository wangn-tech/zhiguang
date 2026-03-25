package handler

import (
	"context"
	"net/http"
	"time"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

// Checker abstracts dependency health probing.
type Checker interface {
	Name() string
	Check(ctx context.Context) error
}

// HealthHandler handles health/readiness endpoints.
type HealthHandler struct {
	checkers []Checker
}

// NewHealthHandler 创建健康检查处理器。
func NewHealthHandler(checkers []Checker) *HealthHandler {
	return &HealthHandler{checkers: checkers}
}

// Healthz 返回存活状态。
func (h *HealthHandler) Healthz(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// Readyz 返回依赖就绪状态。
func (h *HealthHandler) Readyz(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
	defer cancel()

	for _, checker := range h.checkers {
		if err := checker.Check(ctx); err != nil {
			c.JSON(http.StatusServiceUnavailable, errorsx.ErrorResponse{
				Code:    string(errorsx.CodeInternalError),
				Message: "依赖未就绪",
			})
			return
		}
	}

	c.JSON(http.StatusOK, gin.H{"status": "ready"})
}
