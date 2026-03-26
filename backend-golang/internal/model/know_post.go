package model

import "time"

// KnowPost 对应 know_posts 表实体。
type KnowPost struct {
	ID         uint64    `gorm:"column:id;primaryKey"`
	CreatorID  uint64    `gorm:"column:creator_id;not null"`
	Status     string    `gorm:"column:status;type:varchar(16);not null"`
	Type       string    `gorm:"column:type;type:varchar(32);not null"`
	Visible    string    `gorm:"column:visible;type:varchar(32);not null"`
	IsTop      bool      `gorm:"column:is_top;not null"`
	CreateTime time.Time `gorm:"column:create_time;autoCreateTime"`
	UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`
}

// TableName 指定 know_posts 表名。
func (KnowPost) TableName() string {
	return "know_posts"
}
