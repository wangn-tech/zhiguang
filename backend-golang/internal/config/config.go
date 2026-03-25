package config

import (
	"fmt"
	"strings"
	"time"

	"github.com/spf13/viper"
)

// Config 定义应用运行时配置（runtime config）。
type Config struct {
	Server ServerConfig
	Logger LoggerConfig
	MySQL  MySQLConfig
	Redis  RedisConfig
	Auth   AuthConfig
}

// ServerConfig 包含 HTTP server 相关配置。
type ServerConfig struct {
	Port            string
	Env             string
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
	ShutdownTimeout time.Duration
}

// LoggerConfig 包含 zap logger 相关配置。
type LoggerConfig struct {
	Level string
}

// MySQLConfig 包含 MySQL connection 相关配置。
type MySQLConfig struct {
	DSN             string
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
}

// RedisConfig 包含 Redis connection 相关配置。
type RedisConfig struct {
	Addr     string
	Password string
	DB       int
}

// AuthConfig 包含认证域运行配置。
type AuthConfig struct {
	JWT JWTConfig
}

// JWTConfig 包含 JWT 相关配置。
type JWTConfig struct {
	Secret          string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
}

const defaultConfigPath = "configs/config.yaml"

// Load 从默认配置文件加载配置。
func Load() (*Config, error) {
	return LoadFromPath(defaultConfigPath)
}

// LoadFromPath 从指定配置文件加载配置。
// 当 configPath 为空字符串时，会回退到默认路径。
func LoadFromPath(configPath string) (*Config, error) {
	if strings.TrimSpace(configPath) == "" {
		configPath = defaultConfigPath
	}

	v := viper.New()
	setDefaults(v)
	if err := readConfigFile(v, configPath); err != nil {
		return nil, err
	}

	cfg := &Config{
		Server: ServerConfig{
			Port:            v.GetString("server.port"),
			Env:             v.GetString("server.env"),
			ReadTimeout:     parseDurationOrDefault(v.GetString("server.read_timeout"), 10*time.Second),
			WriteTimeout:    parseDurationOrDefault(v.GetString("server.write_timeout"), 15*time.Second),
			ShutdownTimeout: parseDurationOrDefault(v.GetString("server.shutdown_timeout"), 10*time.Second),
		},
		Logger: LoggerConfig{
			Level: v.GetString("logger.level"),
		},
		MySQL: MySQLConfig{
			DSN:             v.GetString("mysql.dsn"),
			MaxOpenConns:    v.GetInt("mysql.max_open_conns"),
			MaxIdleConns:    v.GetInt("mysql.max_idle_conns"),
			ConnMaxLifetime: parseDurationOrDefault(v.GetString("mysql.conn_max_lifetime"), 30*time.Minute),
		},
		Redis: RedisConfig{
			Addr:     v.GetString("redis.addr"),
			Password: v.GetString("redis.password"),
			DB:       v.GetInt("redis.db"),
		},
		Auth: AuthConfig{
			JWT: JWTConfig{
				Secret:          v.GetString("auth.jwt.secret"),
				AccessTokenTTL:  parseDurationOrDefault(v.GetString("auth.jwt.access_token_ttl"), 15*time.Minute),
				RefreshTokenTTL: parseDurationOrDefault(v.GetString("auth.jwt.refresh_token_ttl"), 7*24*time.Hour),
			},
		},
	}

	return cfg, nil
}

func setDefaults(v *viper.Viper) {
	v.SetDefault("server.port", "8080")
	v.SetDefault("server.env", "dev")
	v.SetDefault("server.read_timeout", "10s")
	v.SetDefault("server.write_timeout", "15s")
	v.SetDefault("server.shutdown_timeout", "10s")

	v.SetDefault("logger.level", "info")

	v.SetDefault("mysql.dsn", "root:root@tcp(127.0.0.1:3306)/zhiguang?charset=utf8mb4&parseTime=True&loc=Local")
	v.SetDefault("mysql.max_open_conns", 20)
	v.SetDefault("mysql.max_idle_conns", 5)
	v.SetDefault("mysql.conn_max_lifetime", "30m")

	v.SetDefault("redis.addr", "127.0.0.1:6379")
	v.SetDefault("redis.password", "")
	v.SetDefault("redis.db", 0)

	v.SetDefault("auth.jwt.secret", "zhiguang-dev-jwt-secret")
	v.SetDefault("auth.jwt.access_token_ttl", "15m")
	v.SetDefault("auth.jwt.refresh_token_ttl", "168h")
}

func readConfigFile(v *viper.Viper, configPath string) error {
	v.SetConfigFile(configPath)
	if err := v.ReadInConfig(); err != nil {
		return fmt.Errorf("read config %s: %w", configPath, err)
	}
	return nil
}

func parseDurationOrDefault(raw string, fallback time.Duration) time.Duration {
	if raw == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(raw)
	if err != nil {
		return fallback
	}
	return parsed
}
