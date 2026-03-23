package app

import (
	"fmt"
	"net/http"
	"time"
	"zhiguang/internal/config"
	"zhiguang/internal/handler"
	"zhiguang/internal/router"
	"zhiguang/internal/store"
)

// NewServer 基于配置完成依赖初始化并构建 HTTP Server。
func NewServer(cfg *config.Config) (*http.Server, error) {
	db, err := store.NewMySQL(cfg.MySQL.DSN)
	if err != nil {
		return nil, fmt.Errorf("open mysql: %w", err)
	}

	redisClient := store.NewRedis(cfg.Redis.Addr, cfg.Redis.Password, cfg.Redis.DB)

	healthHandler := handler.NewHealthHandler([]handler.Checker{
		store.NewMySQLChecker(db),
		store.NewRedisChecker(redisClient),
	})

	engine := router.NewEngine(healthHandler)

	server := &http.Server{
		Addr:              ":" + cfg.Server.Port,
		Handler:           engine,
		ReadHeaderTimeout: 3 * time.Second,
		ReadTimeout:       cfg.Server.ReadTimeout,
		WriteTimeout:      cfg.Server.WriteTimeout,
	}

	return server, nil
}
