package repository

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

// RelationRepository 负责关注关系的 Redis 存储访问。
type RelationRepository struct {
	redis *redis.Client
}

// NewRelationRepository 创建 RelationRepository。
func NewRelationRepository(redisClient *redis.Client) *RelationRepository {
	return &RelationRepository{redis: redisClient}
}

// Follow 建立关注关系并返回是否新增成功。
func (r *RelationRepository) Follow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	score := float64(time.Now().UnixMilli())
	followingKey := relationFollowingKey(fromUserID)
	followersKey := relationFollowersKey(toUserID)
	member := strconv.FormatUint(toUserID, 10)
	followerMember := strconv.FormatUint(fromUserID, 10)

	pipe := r.redis.TxPipeline()
	addFollowing := pipe.ZAdd(ctx, followingKey, redis.Z{Score: score, Member: member})
	pipe.ZAdd(ctx, followersKey, redis.Z{Score: score, Member: followerMember})
	if _, err := pipe.Exec(ctx); err != nil {
		return false, fmt.Errorf("relation follow: %w", err)
	}
	return addFollowing.Val() > 0, nil
}

// Unfollow 取消关注关系并返回是否发生变化。
func (r *RelationRepository) Unfollow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	followingKey := relationFollowingKey(fromUserID)
	followersKey := relationFollowersKey(toUserID)
	member := strconv.FormatUint(toUserID, 10)
	followerMember := strconv.FormatUint(fromUserID, 10)

	pipe := r.redis.TxPipeline()
	removeFollowing := pipe.ZRem(ctx, followingKey, member)
	pipe.ZRem(ctx, followersKey, followerMember)
	if _, err := pipe.Exec(ctx); err != nil {
		return false, fmt.Errorf("relation unfollow: %w", err)
	}
	return removeFollowing.Val() > 0, nil
}

// IsFollowing 判断 fromUserID 是否关注 toUserID。
func (r *RelationRepository) IsFollowing(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	rank, err := r.redis.ZRank(ctx, relationFollowingKey(fromUserID), strconv.FormatUint(toUserID, 10)).Result()
	if err == redis.Nil {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("relation check following: %w", err)
	}
	return rank >= 0, nil
}

// ListFollowingIDs 返回用户的关注列表（按最近关注倒序）。
func (r *RelationRepository) ListFollowingIDs(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]uint64, error) {
	return r.listIDs(ctx, relationFollowingKey(userID), limit, offset, cursor)
}

// ListFollowerIDs 返回用户的粉丝列表（按最近关注倒序）。
func (r *RelationRepository) ListFollowerIDs(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]uint64, error) {
	return r.listIDs(ctx, relationFollowersKey(userID), limit, offset, cursor)
}

// CountFollowings 返回用户关注数量。
func (r *RelationRepository) CountFollowings(ctx context.Context, userID uint64) (int64, error) {
	count, err := r.redis.ZCard(ctx, relationFollowingKey(userID)).Result()
	if err != nil {
		return 0, fmt.Errorf("relation count followings: %w", err)
	}
	return count, nil
}

// CountFollowers 返回用户粉丝数量。
func (r *RelationRepository) CountFollowers(ctx context.Context, userID uint64) (int64, error) {
	count, err := r.redis.ZCard(ctx, relationFollowersKey(userID)).Result()
	if err != nil {
		return 0, fmt.Errorf("relation count followers: %w", err)
	}
	return count, nil
}

func (r *RelationRepository) listIDs(ctx context.Context, key string, limit int, offset int, cursor *int64) ([]uint64, error) {
	if limit <= 0 {
		return []uint64{}, nil
	}

	members := make([]string, 0, limit)
	if cursor != nil {
		result, err := r.redis.ZRevRangeByScore(ctx, key, &redis.ZRangeBy{Max: strconv.FormatInt(*cursor, 10), Min: "-inf", Offset: int64(offset), Count: int64(limit)}).Result()
		if err != nil {
			return nil, fmt.Errorf("relation list by cursor: %w", err)
		}
		members = result
	} else {
		result, err := r.redis.ZRevRange(ctx, key, int64(offset), int64(offset+limit-1)).Result()
		if err != nil {
			return nil, fmt.Errorf("relation list by offset: %w", err)
		}
		members = result
	}

	ids := make([]uint64, 0, len(members))
	for _, member := range members {
		id, err := strconv.ParseUint(strings.TrimSpace(member), 10, 64)
		if err != nil {
			continue
		}
		ids = append(ids, id)
	}
	return ids, nil
}

func relationFollowingKey(userID uint64) string {
	return fmt.Sprintf("relation:following:%d", userID)
}

func relationFollowersKey(userID uint64) string {
	return fmt.Sprintf("relation:followers:%d", userID)
}
