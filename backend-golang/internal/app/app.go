package app

import (
	"fmt"
	"net/http"
	"time"
	"zhiguang/internal/config"
	"zhiguang/internal/handler"
	"zhiguang/internal/middleware"
	"zhiguang/internal/repository"
	"zhiguang/internal/router"
	"zhiguang/internal/service"
	"zhiguang/internal/store"
)

// NewServer 基于配置完成依赖初始化并构建 HTTP Server。
func NewServer(cfg *config.Config) (*http.Server, error) {
	db, err := store.NewMySQL(cfg.MySQL.DSN)
	if err != nil {
		return nil, fmt.Errorf("open mysql: %w", err)
	}

	redisClient := store.NewRedis(cfg.Redis.Addr, cfg.Redis.Password, cfg.Redis.DB)

	userRepo := repository.NewUserRepository(db)
	loginLogRepo := repository.NewLoginLogRepository(db)
	knowPostRepo := repository.NewKnowPostRepository(db)

	authService := service.NewAuthService(userRepo, loginLogRepo, redisClient, service.AuthOptions{
		TokenSecret:     cfg.Auth.JWT.Secret,
		AccessTokenTTL:  cfg.Auth.JWT.AccessTokenTTL,
		RefreshTokenTTL: cfg.Auth.JWT.RefreshTokenTTL,
	})
	profileService := service.NewProfileService(userRepo)
	objectStorageService := service.NewObjectStorageService(cfg.OSS)
	storagePresignService := service.NewStoragePresignService(objectStorageService, knowPostRepo, cfg.OSS.PresignExpireSeconds)
	knowPostService := service.NewKnowPostService(knowPostRepo)

	healthHandler := handler.NewHealthHandler([]handler.Checker{
		store.NewMySQLChecker(db),
		store.NewRedisChecker(redisClient),
	})
	authHandler := handler.NewAuthHandler(authService)
	profileHandler := handler.NewProfileHandler(profileService, objectStorageService)
	storageHandler := handler.NewStorageHandler(storagePresignService)
	knowPostHandler := handler.NewKnowPostHandler(knowPostService)

	enforcer, err := middleware.NewCasbinEnforcer()
	if err != nil {
		return nil, fmt.Errorf("init casbin enforcer: %w", err)
	}
	authz := middleware.Authz(enforcer, cfg.Auth.JWT.Secret)

	engine := router.NewEngine(healthHandler, authHandler, profileHandler, storageHandler, knowPostHandler, authz)

	server := &http.Server{
		Addr:              ":" + cfg.Server.Port,
		Handler:           engine,
		ReadHeaderTimeout: 3 * time.Second,
		ReadTimeout:       cfg.Server.ReadTimeout,
		WriteTimeout:      cfg.Server.WriteTimeout,
	}

	return server, nil
}
