package repository

import (
	"context"
	"fmt"
	"strconv"
	"strings"

	"github.com/redis/go-redis/v9"
)

// CounterRepository 提供点赞/收藏状态与计数的 Redis 存取能力。
type CounterRepository struct {
	redis *redis.Client
}

// NewCounterRepository 创建 CounterRepository。
func NewCounterRepository(redisClient *redis.Client) *CounterRepository {
	return &CounterRepository{redis: redisClient}
}

// AddAction 记录用户行为状态，返回是否发生状态变化。
// 关键逻辑：同时维护“实体维度用户集合”和“用户维度实体集合”，便于详情计数和用户计数都能快速读取。
func (r *CounterRepository) AddAction(ctx context.Context, metric string, entityType string, entityID string, userID uint64) (bool, error) {
	entityKey := counterEntityMemberKey(metric, entityType, entityID)
	userKey := counterUserEntityKey(metric, userID)
	userMember := strconv.FormatUint(userID, 10)
	entityMember := buildCounterEntityMember(entityType, entityID)

	pipe := r.redis.TxPipeline()
	addEntity := pipe.SAdd(ctx, entityKey, userMember)
	pipe.SAdd(ctx, userKey, entityMember)
	if _, err := pipe.Exec(ctx); err != nil {
		return false, fmt.Errorf("counter add action: %w", err)
	}
	return addEntity.Val() > 0, nil
}

// RemoveAction 删除用户行为状态，返回是否发生状态变化。
func (r *CounterRepository) RemoveAction(ctx context.Context, metric string, entityType string, entityID string, userID uint64) (bool, error) {
	entityKey := counterEntityMemberKey(metric, entityType, entityID)
	userKey := counterUserEntityKey(metric, userID)
	userMember := strconv.FormatUint(userID, 10)
	entityMember := buildCounterEntityMember(entityType, entityID)

	pipe := r.redis.TxPipeline()
	removeEntity := pipe.SRem(ctx, entityKey, userMember)
	pipe.SRem(ctx, userKey, entityMember)
	if _, err := pipe.Exec(ctx); err != nil {
		return false, fmt.Errorf("counter remove action: %w", err)
	}
	return removeEntity.Val() > 0, nil
}

// ExistsAction 判断用户是否已存在行为状态。
func (r *CounterRepository) ExistsAction(ctx context.Context, metric string, entityType string, entityID string, userID uint64) (bool, error) {
	exists, err := r.redis.SIsMember(ctx, counterEntityMemberKey(metric, entityType, entityID), strconv.FormatUint(userID, 10)).Result()
	if err != nil {
		return false, fmt.Errorf("counter check action exists: %w", err)
	}
	return exists, nil
}

// CountAction 返回指定行为在实体维度的聚合计数。
func (r *CounterRepository) CountAction(ctx context.Context, metric string, entityType string, entityID string) (int64, error) {
	count, err := r.redis.SCard(ctx, counterEntityMemberKey(metric, entityType, entityID)).Result()
	if err != nil {
		return 0, fmt.Errorf("counter count action: %w", err)
	}
	return count, nil
}

// CountUserMetric 返回用户维度的行为计数（例如点赞过多少条内容）。
func (r *CounterRepository) CountUserMetric(ctx context.Context, metric string, userID uint64) (int64, error) {
	count, err := r.redis.SCard(ctx, counterUserEntityKey(metric, userID)).Result()
	if err != nil {
		return 0, fmt.Errorf("counter count user metric: %w", err)
	}
	return count, nil
}

func counterEntityMemberKey(metric string, entityType string, entityID string) string {
	return strings.Join([]string{"counter", strings.ToLower(strings.TrimSpace(metric)), strings.ToLower(strings.TrimSpace(entityType)), strings.TrimSpace(entityID), "users"}, ":")
}

func counterUserEntityKey(metric string, userID uint64) string {
	return strings.Join([]string{"counter", "user", strconv.FormatUint(userID, 10), strings.ToLower(strings.TrimSpace(metric)), "entities"}, ":")
}

func buildCounterEntityMember(entityType string, entityID string) string {
	return strings.ToLower(strings.TrimSpace(entityType)) + ":" + strings.TrimSpace(entityID)
}
