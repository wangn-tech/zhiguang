package model

import "time"

// User 对应 users 表实体。
type User struct {
	ID           uint64     `gorm:"column:id;primaryKey;autoIncrement"`
	Phone        *string    `gorm:"column:phone;type:varchar(32)"`
	Email        *string    `gorm:"column:email;type:varchar(128)"`
	PasswordHash string     `gorm:"column:password_hash;type:varchar(128)"`
	Nickname     string     `gorm:"column:nickname;type:varchar(64);not null"`
	Avatar       *string    `gorm:"column:avatar;type:text"`
	Bio          *string    `gorm:"column:bio;type:varchar(512)"`
	ZgID         *string    `gorm:"column:zg_id;type:varchar(64)"`
	Gender       *string    `gorm:"column:gender;type:varchar(16)"`
	Birthday     *time.Time `gorm:"column:birthday;type:date"`
	School       *string    `gorm:"column:school;type:varchar(128)"`
	TagsJSON     *string    `gorm:"column:tags_json;type:json"`
	CreatedAt    time.Time  `gorm:"column:created_at;autoCreateTime"`
	UpdatedAt    time.Time  `gorm:"column:updated_at;autoUpdateTime"`
}

// TableName 指定 users 表名。
func (User) TableName() string {
	return "users"
}
