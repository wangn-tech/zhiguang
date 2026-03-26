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

// KnowPostFeedRow 表示 feed 列表查询返回的原始行数据。
type KnowPostFeedRow struct {
	ID             uint64  `gorm:"column:id"`
	Title          string  `gorm:"column:title"`
	Description    string  `gorm:"column:description"`
	ImageURLsJSON  *string `gorm:"column:img_urls"`
	TagsJSON       *string `gorm:"column:tags"`
	Visible        string  `gorm:"column:visible"`
	IsTop          bool    `gorm:"column:is_top"`
	AuthorAvatar   *string `gorm:"column:author_avatar"`
	AuthorNickname string  `gorm:"column:author_nickname"`
	AuthorTagJSON  *string `gorm:"column:author_tag_json"`
}

// KnowPostDetailRow 表示详情查询返回的原始行数据。
type KnowPostDetailRow struct {
	ID             uint64     `gorm:"column:id"`
	CreatorID      uint64     `gorm:"column:creator_id"`
	Title          string     `gorm:"column:title"`
	Description    string     `gorm:"column:description"`
	ContentURL     string     `gorm:"column:content_url"`
	ImageURLsJSON  *string    `gorm:"column:img_urls"`
	TagsJSON       *string    `gorm:"column:tags"`
	AuthorAvatar   *string    `gorm:"column:author_avatar"`
	AuthorNickname string     `gorm:"column:author_nickname"`
	AuthorTagJSON  *string    `gorm:"column:author_tag_json"`
	PublishTime    *time.Time `gorm:"column:publish_time"`
	IsTop          bool       `gorm:"column:is_top"`
	Visible        string     `gorm:"column:visible"`
	Type           string     `gorm:"column:type"`
	Status         string     `gorm:"column:status"`
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

// Publish 将指定知文状态更新为已发布并记录发布时间。
func (r *KnowPostRepository) Publish(ctx context.Context, postID uint64, creatorID uint64) (bool, error) {
	now := time.Now()
	updates := map[string]any{
		"status":       "published",
		"publish_time": now,
		"update_time":  now,
	}

	result := r.db.WithContext(ctx).Model(&model.KnowPost{}).
		Where("id = ? AND creator_id = ?", postID, creatorID).
		Updates(updates)
	if result.Error != nil {
		return false, fmt.Errorf("publish knowpost: %w", result.Error)
	}
	return result.RowsAffected > 0, nil
}

// ListPublicFeed 分页读取公开且已发布的知文列表。
func (r *KnowPostRepository) ListPublicFeed(ctx context.Context, page int, size int) ([]KnowPostFeedRow, bool, error) {
	if size <= 0 {
		return []KnowPostFeedRow{}, false, nil
	}

	offset := (page - 1) * size
	limit := size + 1
	rows := make([]KnowPostFeedRow, 0, limit)

	err := r.db.WithContext(ctx).
		Table("know_posts AS kp").
		Select(`
			kp.id,
			COALESCE(kp.title, '') AS title,
			COALESCE(kp.description, '') AS description,
			kp.img_urls,
			kp.tags,
			kp.visible,
			kp.is_top,
			u.avatar AS author_avatar,
			u.nickname AS author_nickname,
			u.tags_json AS author_tag_json
		`).
		Joins("JOIN users AS u ON u.id = kp.creator_id").
		Where("kp.status = ? AND kp.visible = ?", "published", "public").
		Order("kp.is_top DESC, kp.publish_time DESC, kp.id DESC").
		Limit(limit).
		Offset(offset).
		Scan(&rows).Error
	if err != nil {
		return nil, false, fmt.Errorf("list public feed: %w", err)
	}

	hasMore := len(rows) > size
	if hasMore {
		rows = rows[:size]
	}
	return rows, hasMore, nil
}

// ListMyPublished 分页读取当前用户已发布的知文列表。
func (r *KnowPostRepository) ListMyPublished(ctx context.Context, creatorID uint64, page int, size int) ([]KnowPostFeedRow, bool, error) {
	if size <= 0 {
		return []KnowPostFeedRow{}, false, nil
	}

	offset := (page - 1) * size
	limit := size + 1
	rows := make([]KnowPostFeedRow, 0, limit)

	err := r.db.WithContext(ctx).
		Table("know_posts AS kp").
		Select(`
			kp.id,
			COALESCE(kp.title, '') AS title,
			COALESCE(kp.description, '') AS description,
			kp.img_urls,
			kp.tags,
			kp.visible,
			kp.is_top,
			u.avatar AS author_avatar,
			u.nickname AS author_nickname,
			u.tags_json AS author_tag_json
		`).
		Joins("JOIN users AS u ON u.id = kp.creator_id").
		Where("kp.creator_id = ? AND kp.status = ?", creatorID, "published").
		Order("kp.is_top DESC, kp.publish_time DESC, kp.id DESC").
		Limit(limit).
		Offset(offset).
		Scan(&rows).Error
	if err != nil {
		return nil, false, fmt.Errorf("list my published knowposts: %w", err)
	}

	hasMore := len(rows) > size
	if hasMore {
		rows = rows[:size]
	}
	return rows, hasMore, nil
}

// GetDetailByID 按知文 ID 读取详情信息。
func (r *KnowPostRepository) GetDetailByID(ctx context.Context, postID uint64) (*KnowPostDetailRow, error) {
	var row KnowPostDetailRow
	err := r.db.WithContext(ctx).
		Table("know_posts AS kp").
		Select(`
			kp.id,
			kp.creator_id,
			COALESCE(kp.title, '') AS title,
			COALESCE(kp.description, '') AS description,
			COALESCE(kp.content_url, '') AS content_url,
			kp.img_urls,
			kp.tags,
			u.avatar AS author_avatar,
			u.nickname AS author_nickname,
			u.tags_json AS author_tag_json,
			kp.publish_time,
			kp.is_top,
			kp.visible,
			kp.type,
			kp.status
		`).
		Joins("JOIN users AS u ON u.id = kp.creator_id").
		Where("kp.id = ?", postID).
		Take(&row).Error
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, nil
		}
		return nil, fmt.Errorf("get knowpost detail: %w", err)
	}
	return &row, nil
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
