package model

import "time"

// OutboxEvent 对应 outbox 表实体。
type OutboxEvent struct {
	ID            uint64    `gorm:"column:id;primaryKey"`
	AggregateType string    `gorm:"column:aggregate_type;type:varchar(64);not null"`
	AggregateID   *uint64   `gorm:"column:aggregate_id"`
	Type          string    `gorm:"column:type;type:varchar(64);not null"`
	Payload       string    `gorm:"column:payload;type:json;not null"`
	CreatedAt     time.Time `gorm:"column:created_at;autoCreateTime:milli"`
}

// TableName 指定 outbox 表名。
func (OutboxEvent) TableName() string {
	return "outbox"
}
