package repository

import (
	"context"
	"fmt"
	"zhiguang/internal/model"

	"gorm.io/gorm"
)

// LoginLogRepository 负责 login_logs 表的 GORM 数据访问。
type LoginLogRepository struct {
	db *gorm.DB
}

// NewLoginLogRepository 创建 LoginLogRepository。
func NewLoginLogRepository(db *gorm.DB) *LoginLogRepository {
	return &LoginLogRepository{db: db}
}

// Create 插入登录审计日志。
func (r *LoginLogRepository) Create(ctx context.Context, log *model.LoginLog) error {
	if err := r.db.WithContext(ctx).Create(log).Error; err != nil {
		return fmt.Errorf("create login log: %w", err)
	}
	return nil
}
