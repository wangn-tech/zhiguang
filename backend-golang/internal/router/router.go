package router

import (
	"zhiguang/internal/handler"
	"zhiguang/internal/middleware"

	"github.com/gin-gonic/gin"
)

// NewEngine 创建并初始化 Gin 路由引擎。
func NewEngine(healthHandler *handler.HealthHandler) *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger(), middleware.ErrorHandler())

	healthHandler.Register(r)

	return r
}
