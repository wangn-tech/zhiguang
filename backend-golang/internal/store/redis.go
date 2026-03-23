package store

import (
	"context"

	"github.com/redis/go-redis/v9"
)

// RedisChecker pings Redis for readiness checks.
type RedisChecker struct {
	client *redis.Client
}

// NewRedis 创建 Redis 客户端。
func NewRedis(addr, password string, db int) *redis.Client {
	return redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})
}

// NewRedisChecker 创建 Redis 就绪检查器。
func NewRedisChecker(client *redis.Client) *RedisChecker {
	return &RedisChecker{client: client}
}

// Name 返回依赖名称。
func (c *RedisChecker) Name() string {
	return "redis"
}

// Check 执行 Redis 连通性探测。
func (c *RedisChecker) Check(ctx context.Context) error {
	return c.client.Ping(ctx).Err()
}
