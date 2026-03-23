package logx

import (
	"fmt"
	"strings"
	"sync"

	"go.uber.org/zap"
)

// Options 定义 logger 初始化参数。
type Options struct {
	Env   string
	Level string
}

var (
	mu     sync.RWMutex
	logger = zap.NewNop()
	sugar  = logger.Sugar()
)

// Init 初始化全局 zap logger。
// 推荐在 main 函数启动早期调用一次。
func Init(opts Options) error {
	cfg := zap.NewDevelopmentConfig()
	if strings.EqualFold(opts.Env, "prod") || strings.EqualFold(opts.Env, "production") {
		cfg = zap.NewProductionConfig()
	}

	level := strings.TrimSpace(opts.Level)
	if level == "" {
		if strings.EqualFold(opts.Env, "prod") || strings.EqualFold(opts.Env, "production") {
			level = "info"
		} else {
			level = "debug"
		}
	}
	if err := cfg.Level.UnmarshalText([]byte(level)); err != nil {
		return fmt.Errorf("invalid log level %q: %w", level, err)
	}

	newLogger, err := cfg.Build(zap.AddCaller(), zap.AddCallerSkip(1))
	if err != nil {
		return fmt.Errorf("build zap logger: %w", err)
	}

	mu.Lock()
	old := logger
	logger = newLogger
	sugar = newLogger.Sugar()
	mu.Unlock()

	_ = old.Sync()
	return nil
}

// L 返回结构化 logger（structured logger）。
func L() *zap.Logger {
	mu.RLock()
	defer mu.RUnlock()
	return logger
}

// S 返回带 format 能力的 sugared logger。
func S() *zap.SugaredLogger {
	mu.RLock()
	defer mu.RUnlock()
	return sugar
}

// Sync 刷新 logger buffer，通常在进程退出前调用。
func Sync() {
	mu.RLock()
	defer mu.RUnlock()
	_ = logger.Sync()
}
