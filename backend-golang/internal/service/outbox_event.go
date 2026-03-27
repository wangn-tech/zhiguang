package service

import (
	"context"
	"time"
)

// RelationChangeAction 表示关系变更动作。
type RelationChangeAction string

const (
	RelationChangeActionFollow   RelationChangeAction = "follow"
	RelationChangeActionUnfollow RelationChangeAction = "unfollow"
)

// RelationChangeEvent 描述关注关系变更事件。
type RelationChangeEvent struct {
	Action     RelationChangeAction
	FromUserID uint64
	ToUserID   uint64
	OccurredAt time.Time
}

// RelationEventPublisher 负责发布关系变更事件。
type RelationEventPublisher interface {
	PublishRelationChange(ctx context.Context, event RelationChangeEvent) error
}

// NopRelationEventPublisher 提供默认的空发布实现。
type NopRelationEventPublisher struct{}

// PublishRelationChange 默认不执行任何发布动作。
func (NopRelationEventPublisher) PublishRelationChange(context.Context, RelationChangeEvent) error {
	return nil
}

// CounterActionOperation 表示互动动作类型。
type CounterActionOperation string

const (
	CounterActionOperationActivate   CounterActionOperation = "activate"
	CounterActionOperationDeactivate CounterActionOperation = "deactivate"
)

// CounterActionChangeEvent 描述互动行为状态变更事件。
type CounterActionChangeEvent struct {
	Operation  CounterActionOperation
	Metric     string
	EntityType string
	EntityID   string
	UserID     uint64
	Active     bool
	OccurredAt time.Time
}

// CounterEventPublisher 负责发布互动行为变更事件。
type CounterEventPublisher interface {
	PublishCounterActionChange(ctx context.Context, event CounterActionChangeEvent) error
}

// NopCounterEventPublisher 提供默认的空发布实现。
type NopCounterEventPublisher struct{}

// PublishCounterActionChange 默认不执行任何发布动作。
func (NopCounterEventPublisher) PublishCounterActionChange(context.Context, CounterActionChangeEvent) error {
	return nil
}
