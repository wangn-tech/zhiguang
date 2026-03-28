package service

import (
	"context"
	"errors"
	"zhiguang/internal/model"
)

const defaultOutboxRelayBatchSize = 100

var (
	errOutboxRelayReaderNotConfigured = errors.New("outbox relay reader is not configured")
	errOutboxRelaySinkNotConfigured   = errors.New("outbox relay sink is not configured")
)

// OutboxEventReader 提供 outbox 事件读取能力。
type OutboxEventReader interface {
	ListAfterID(ctx context.Context, afterID uint64, limit int) ([]model.OutboxEvent, error)
}

// OutboxEventSink 抽象事件发布目标。
type OutboxEventSink interface {
	Publish(ctx context.Context, event model.OutboxEvent) error
}

// OutboxRelayOption 负责扩展 OutboxRelay 配置。
type OutboxRelayOption func(*OutboxRelay)

// WithOutboxRelayBatchSize 配置单次 relay 拉取批大小。
func WithOutboxRelayBatchSize(batchSize int) OutboxRelayOption {
	return func(r *OutboxRelay) {
		if batchSize > 0 {
			r.batchSize = batchSize
		}
	}
}

// OutboxRelay 提供 outbox 事件中继的最小运行骨架。
type OutboxRelay struct {
	reader    OutboxEventReader
	sink      OutboxEventSink
	batchSize int
	lastID    uint64
}

// NewOutboxRelay 创建 OutboxRelay。
func NewOutboxRelay(reader OutboxEventReader, sink OutboxEventSink, opts ...OutboxRelayOption) *OutboxRelay {
	r := &OutboxRelay{reader: reader, sink: sink, batchSize: defaultOutboxRelayBatchSize}
	for _, opt := range opts {
		if opt != nil {
			opt(r)
		}
	}
	if r.batchSize <= 0 {
		r.batchSize = defaultOutboxRelayBatchSize
	}
	return r
}

// RunOnce 执行一次 relay 轮询并返回本次成功发布事件条数。
func (r *OutboxRelay) RunOnce(ctx context.Context) (int, error) {
	if r == nil || r.reader == nil {
		return 0, errOutboxRelayReaderNotConfigured
	}
	if r.sink == nil {
		return 0, errOutboxRelaySinkNotConfigured
	}

	events, err := r.reader.ListAfterID(ctx, r.lastID, r.batchSize)
	if err != nil {
		return 0, err
	}

	published := 0
	for _, event := range events {
		if err := r.sink.Publish(ctx, event); err != nil {
			return published, err
		}
		r.lastID = event.ID
		published++
	}
	return published, nil
}

// LastID 返回 relay 当前游标位置。
func (r *OutboxRelay) LastID() uint64 {
	if r == nil {
		return 0
	}
	return r.lastID
}
