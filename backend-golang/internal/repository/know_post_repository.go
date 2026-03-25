package repository

import (
	"context"
	"fmt"
	"zhiguang/internal/model"

	"gorm.io/gorm"
)

// KnowPostRepository 负责 know_posts 表的数据访问能力。
type KnowPostRepository struct {
	db *gorm.DB
}

// NewKnowPostRepository 创建 KnowPostRepository。
func NewKnowPostRepository(db *gorm.DB) *KnowPostRepository {
	return &KnowPostRepository{db: db}
}

// CreateDraft 插入知文草稿记录。
func (r *KnowPostRepository) CreateDraft(ctx context.Context, post *model.KnowPost) error {
	if err := r.db.WithContext(ctx).Create(post).Error; err != nil {
		return fmt.Errorf("create knowpost draft: %w", err)
	}
	return nil
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
