package model

import "time"

// LoginLog 对应 login_logs 审计表实体。
type LoginLog struct {
	ID         uint64    `gorm:"column:id;primaryKey;autoIncrement"`
	UserID     *uint64   `gorm:"column:user_id"`
	Identifier string    `gorm:"column:identifier;type:varchar(128);not null"`
	Channel    string    `gorm:"column:channel;type:varchar(32);not null"`
	IP         *string   `gorm:"column:ip;type:varchar(45)"`
	UserAgent  *string   `gorm:"column:user_agent;type:varchar(512)"`
	Status     string    `gorm:"column:status;type:varchar(16);not null"`
	CreatedAt  time.Time `gorm:"column:created_at;autoCreateTime"`
}

// TableName 指定 login_logs 表名。
func (LoginLog) TableName() string {
	return "login_logs"
}
