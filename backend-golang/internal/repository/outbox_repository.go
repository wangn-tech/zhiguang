package repository

import (
	"context"
	"fmt"
	"strings"
	"sync/atomic"
	"time"
	"zhiguang/internal/model"

	"gorm.io/gorm"
)

var outboxSeq atomic.Uint32

// OutboxCreateParams 表示 outbox 事件写入参数。
type OutboxCreateParams struct {
	AggregateType string
	AggregateID   *uint64
	Type          string
	Payload       string
}

// OutboxRepository 负责 outbox 表写入能力。
type OutboxRepository struct {
	db *gorm.DB
}

// NewOutboxRepository 创建 OutboxRepository。
func NewOutboxRepository(db *gorm.DB) *OutboxRepository {
	return &OutboxRepository{db: db}
}

// Create 写入一条 outbox 事件记录。
func (r *OutboxRepository) Create(ctx context.Context, params OutboxCreateParams) error {
	aggregateType := strings.TrimSpace(params.AggregateType)
	eventType := strings.TrimSpace(params.Type)
	payload := strings.TrimSpace(params.Payload)
	if aggregateType == "" || eventType == "" || payload == "" {
		return fmt.Errorf("create outbox event: invalid params")
	}

	now := time.Now().UTC()
	record := model.OutboxEvent{
		ID:            nextOutboxID(now),
		AggregateType: aggregateType,
		AggregateID:   params.AggregateID,
		Type:          eventType,
		Payload:       payload,
		CreatedAt:     now,
	}
	if err := r.db.WithContext(ctx).Create(&record).Error; err != nil {
		return fmt.Errorf("create outbox event: %w", err)
	}
	return nil
}

func nextOutboxID(now time.Time) uint64 {
	millis := uint64(now.UnixMilli())
	seq := uint64(outboxSeq.Add(1) & 0x0fff)
	return (millis << 12) | seq
}
