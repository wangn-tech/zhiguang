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

// Register 注册健康检查与就绪检查路由。
func (h *HealthHandler) Register(r gin.IRouter) {
	r.GET("/healthz", h.healthz)
	r.GET("/readyz", h.readyz)
}

func (h *HealthHandler) healthz(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func (h *HealthHandler) readyz(c *gin.Context) {
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
