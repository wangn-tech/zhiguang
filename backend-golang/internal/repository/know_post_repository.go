package repository

import (
	"context"
	"fmt"
	"time"
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

// ConfirmContent 保存正文对象信息，并更新正文可访问地址。
func (r *KnowPostRepository) ConfirmContent(ctx context.Context, postID uint64, creatorID uint64, objectKey string, etag string, size int64, sha256 string, contentURL string) (bool, error) {
	updates := map[string]any{
		"content_object_key": objectKey,
		"content_etag":       etag,
		"content_size":       size,
		"content_sha256":     sha256,
		"content_url":        contentURL,
		"update_time":        time.Now(),
	}

	result := r.db.WithContext(ctx).Model(&model.KnowPost{}).
		Where("id = ? AND creator_id = ?", postID, creatorID).
		Updates(updates)
	if result.Error != nil {
		return false, fmt.Errorf("confirm knowpost content: %w", result.Error)
	}
	return result.RowsAffected > 0, nil
}

// UpdateMetadata 按作者归属更新知文元数据字段。
func (r *KnowPostRepository) UpdateMetadata(ctx context.Context, postID uint64, creatorID uint64, updates map[string]any) (bool, error) {
	if len(updates) == 0 {
		return false, nil
	}

	updates["update_time"] = time.Now()
	result := r.db.WithContext(ctx).Model(&model.KnowPost{}).
		Where("id = ? AND creator_id = ?", postID, creatorID).
		Updates(updates)
	if result.Error != nil {
		return false, fmt.Errorf("update knowpost metadata: %w", result.Error)
	}
	return result.RowsAffected > 0, nil
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
