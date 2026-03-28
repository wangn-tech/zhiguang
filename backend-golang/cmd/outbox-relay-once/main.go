package main

import (
	"context"
	"log"
	"zhiguang/internal/app"
	"zhiguang/internal/config"
	"zhiguang/pkg/logx"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("加载配置失败（config load failed）: %v", err)
	}

	if err := logx.Init(logx.Options{Env: cfg.Server.Env, Level: cfg.Logger.Level}); err != nil {
		log.Fatalf("初始化日志失败（logger init failed）: %v", err)
	}
	defer logx.Sync()

	published, err := app.RunOutboxRelayOnce(context.Background(), cfg, app.RunOutboxRelayOnceOptions{})
	if err != nil {
		logx.S().Fatalw("Outbox relay 执行失败（outbox relay run once failed）", "error", err)
	}
	logx.S().Infow("Outbox relay 执行完成（outbox relay run once completed）", "published", published, "sink", cfg.Outbox.Relay.Sink)
}
