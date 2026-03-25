package repository

import (
	"context"
	"fmt"
	"zhiguang/internal/model"

	"gorm.io/gorm"
)

// KnowPostRepository 负责 know_posts 表的最小查询能力。
type KnowPostRepository struct {
	db *gorm.DB
}

// NewKnowPostRepository 创建 KnowPostRepository。
func NewKnowPostRepository(db *gorm.DB) *KnowPostRepository {
	return &KnowPostRepository{db: db}
}

// IsOwnedBy 检查知文是否属于指定用户。
func (r *KnowPostRepository) IsOwnedBy(ctx context.Context, postID uint64, userID uint64) (bool, error) {
	var count int64
	err := r.db.WithContext(ctx).Model(&model.KnowPost{}).
		Where("id = ? AND creator_id = ?", postID, userID).
		Count(&count).Error
	if err != nil {
		return false, fmt.Errorf("check knowpost ownership: %w", err)
	}
	return count > 0, nil
}
