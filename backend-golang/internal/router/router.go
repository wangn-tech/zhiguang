package router

import (
	"zhiguang/internal/handler"

	"github.com/gin-gonic/gin"
)

// NewEngine 创建并初始化 Gin 路由引擎。
func NewEngine(healthHandler *handler.HealthHandler) *gin.Engine {
	r := gin.New()
	r.Use(gin.Recovery(), gin.Logger())

	healthHandler.Register(r)

	return r
}
