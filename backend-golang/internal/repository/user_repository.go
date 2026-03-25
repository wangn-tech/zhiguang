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
