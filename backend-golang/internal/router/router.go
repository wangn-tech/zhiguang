package router

import (
	"zhiguang/internal/handler"
	"zhiguang/internal/middleware"

	"github.com/gin-gonic/gin"
)

// NewEngine 创建并初始化 Gin 路由引擎。
func NewEngine(
	healthHandler *handler.HealthHandler,
	authHandler *handler.AuthHandler,
	profileHandler *handler.ProfileHandler,
	storageHandler *handler.StorageHandler,
	knowPostHandler *handler.KnowPostHandler,
	authz gin.HandlerFunc,
) *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger(), middleware.ErrorHandler())
	if authz != nil {
		r.Use(authz)
	}

	// 基础探活路由。
	r.GET("/healthz", healthHandler.Healthz)
	r.GET("/readyz", healthHandler.Readyz)

	// 认证域路由。
	auth := r.Group("/api/v1/auth")
	auth.POST("/send-code", authHandler.SendCode)
	auth.POST("/register", authHandler.Register)
	auth.POST("/login", authHandler.Login)
	auth.POST("/token/refresh", authHandler.Refresh)
	auth.POST("/logout", authHandler.Logout)
	auth.POST("/password/reset", authHandler.ResetPassword)
	auth.GET("/me", authHandler.Me)

	if profileHandler != nil {
		profile := r.Group("/api/v1/profile")
		profile.GET("", profileHandler.Get)
		profile.PATCH("", profileHandler.Patch)
		profile.POST("/avatar", profileHandler.UploadAvatar)
	}

	if storageHandler != nil {
		storage := r.Group("/api/v1/storage")
		storage.POST("/presign", storageHandler.Presign)
	}

	if knowPostHandler != nil {
		knowposts := r.Group("/api/v1/knowposts")
		knowposts.GET("/feed", knowPostHandler.Feed)
		knowposts.GET("/mine", knowPostHandler.Mine)
		knowposts.GET("/detail/:id", knowPostHandler.Detail)
		knowposts.POST("/drafts", knowPostHandler.CreateDraft)
		knowposts.POST("/:id/content/confirm", knowPostHandler.ConfirmContent)
		knowposts.PATCH("/:id", knowPostHandler.PatchMetadata)
		knowposts.POST("/:id/publish", knowPostHandler.Publish)
	}

	return r
}
