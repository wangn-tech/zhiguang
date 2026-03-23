package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
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

	server, err := app.NewServer(cfg)
	if err != nil {
		logx.S().Fatalw("应用构建失败（app bootstrap failed）", "error", err)
	}

	go func() {
		logx.S().Infow("HTTP 服务启动（server started）", "addr", server.Addr, "env", cfg.Server.Env)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logx.S().Fatalw("HTTP 服务异常退出（server crashed）", "error", err)
		}
	}()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigCh
	logx.S().Infow("收到退出信号（shutdown signal）", "signal", sig.String())

	ctx, cancel := context.WithTimeout(context.Background(), cfg.Server.ShutdownTimeout)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		logx.S().Errorw("优雅关闭失败（graceful shutdown failed）", "error", err)
		return
	}
	logx.S().Infow("服务已停止（server stopped）")
}
