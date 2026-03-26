package repository

import (
	"context"
	"errors"
	"fmt"
	"zhiguang/internal/model"

	"gorm.io/gorm"
)

// UserRepository 负责 users 表的 GORM 数据访问。
type UserRepository struct {
	db *gorm.DB
}

// NewUserRepository 创建 UserRepository。
func NewUserRepository(db *gorm.DB) *UserRepository {
	return &UserRepository{db: db}
}

// FindByPhone 按手机号查询用户。
func (r *UserRepository) FindByPhone(ctx context.Context, phone string) (*model.User, error) {
	var user model.User
	err := r.db.WithContext(ctx).Where("phone = ?", phone).First(&user).Error
	if err == nil {
		return &user, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	return nil, fmt.Errorf("find user by phone: %w", err)
}

// FindByEmail 按邮箱查询用户。
func (r *UserRepository) FindByEmail(ctx context.Context, email string) (*model.User, error) {
	var user model.User
	err := r.db.WithContext(ctx).Where("email = ?", email).First(&user).Error
	if err == nil {
		return &user, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	return nil, fmt.Errorf("find user by email: %w", err)
}

// FindByID 按 ID 查询用户。
func (r *UserRepository) FindByID(ctx context.Context, id uint64) (*model.User, error) {
	var user model.User
	err := r.db.WithContext(ctx).Where("id = ?", id).First(&user).Error
	if err == nil {
		return &user, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	return nil, fmt.Errorf("find user by id: %w", err)
}

// FindByIDs 按 ID 列表查询用户并保持输入顺序。
func (r *UserRepository) FindByIDs(ctx context.Context, ids []uint64) ([]model.User, error) {
	if len(ids) == 0 {
		return []model.User{}, nil
	}

	rows := make([]model.User, 0, len(ids))
	if err := r.db.WithContext(ctx).Where("id IN ?", ids).Find(&rows).Error; err != nil {
		return nil, fmt.Errorf("find users by ids: %w", err)
	}

	index := make(map[uint64]model.User, len(rows))
	for _, row := range rows {
		index[row.ID] = row
	}

	ordered := make([]model.User, 0, len(ids))
	for _, id := range ids {
		if user, ok := index[id]; ok {
			ordered = append(ordered, user)
		}
	}
	return ordered, nil
}

// Create 插入用户记录。
func (r *UserRepository) Create(ctx context.Context, user *model.User) error {
	if err := r.db.WithContext(ctx).Create(user).Error; err != nil {
		return fmt.Errorf("create user: %w", err)
	}
	return nil
}

// UpdatePassword 更新用户密码哈希。
func (r *UserRepository) UpdatePassword(ctx context.Context, userID uint64, passwordHash string) error {
	result := r.db.WithContext(ctx).Model(&model.User{}).Where("id = ?", userID).Update("password_hash", passwordHash)
	if result.Error != nil {
		return fmt.Errorf("update user password: %w", result.Error)
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

// ExistsByZgIDExceptID 判断知光号是否被其他用户占用。
func (r *UserRepository) ExistsByZgIDExceptID(ctx context.Context, zgID string, exceptUserID uint64) (bool, error) {
	var count int64
	err := r.db.WithContext(ctx).Model(&model.User{}).
		Where("zg_id = ? AND id <> ?", zgID, exceptUserID).
		Count(&count).Error
	if err != nil {
		return false, fmt.Errorf("check zg_id existence: %w", err)
	}
	return count > 0, nil
}

// ExistsByPhoneExceptID 判断手机号是否被其他用户占用。
func (r *UserRepository) ExistsByPhoneExceptID(ctx context.Context, phone string, exceptUserID uint64) (bool, error) {
	var count int64
	err := r.db.WithContext(ctx).Model(&model.User{}).
		Where("phone = ? AND id <> ?", phone, exceptUserID).
		Count(&count).Error
	if err != nil {
		return false, fmt.Errorf("check phone existence: %w", err)
	}
	return count > 0, nil
}

// ExistsByEmailExceptID 判断邮箱是否被其他用户占用。
func (r *UserRepository) ExistsByEmailExceptID(ctx context.Context, email string, exceptUserID uint64) (bool, error) {
	var count int64
	err := r.db.WithContext(ctx).Model(&model.User{}).
		Where("email = ? AND id <> ?", email, exceptUserID).
		Count(&count).Error
	if err != nil {
		return false, fmt.Errorf("check email existence: %w", err)
	}
	return count > 0, nil
}

// UpdateProfileFields 按字段更新用户资料。
func (r *UserRepository) UpdateProfileFields(ctx context.Context, userID uint64, fields map[string]any) error {
	if len(fields) == 0 {
		return nil
	}

	result := r.db.WithContext(ctx).Model(&model.User{}).Where("id = ?", userID).Updates(fields)
	if result.Error != nil {
		return fmt.Errorf("update profile fields: %w", result.Error)
	}
	if result.RowsAffected == 0 {
		return gorm.ErrRecordNotFound
	}
	return nil
}
