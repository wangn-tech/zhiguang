package service

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"
	"zhiguang/internal/repository"
)

type outboxWriter interface {
	Create(ctx context.Context, params repository.OutboxCreateParams) error
}

type relationOutboxEventPublisher struct {
	writer outboxWriter
}

type counterOutboxEventPublisher struct {
	writer outboxWriter
}

// NewRelationOutboxEventPublisher 创建关系事件 outbox 发布器。
func NewRelationOutboxEventPublisher(repo *repository.OutboxRepository) RelationEventPublisher {
	if repo == nil {
		return NopRelationEventPublisher{}
	}
	return &relationOutboxEventPublisher{writer: repo}
}

// NewCounterOutboxEventPublisher 创建互动事件 outbox 发布器。
func NewCounterOutboxEventPublisher(repo *repository.OutboxRepository) CounterEventPublisher {
	if repo == nil {
		return NopCounterEventPublisher{}
	}
	return &counterOutboxEventPublisher{writer: repo}
}

// PublishRelationChange 将关系变更事件落库到 outbox。
func (p *relationOutboxEventPublisher) PublishRelationChange(ctx context.Context, event RelationChangeEvent) error {
	if p == nil || p.writer == nil {
		return nil
	}

	payloadBytes, err := json.Marshal(struct {
		Action     RelationChangeAction `json:"action"`
		FromUserID uint64               `json:"fromUserId"`
		ToUserID   uint64               `json:"toUserId"`
		OccurredAt time.Time            `json:"occurredAt"`
	}{
		Action:     event.Action,
		FromUserID: event.FromUserID,
		ToUserID:   event.ToUserID,
		OccurredAt: event.OccurredAt,
	})
	if err != nil {
		return fmt.Errorf("marshal relation outbox payload: %w", err)
	}

	aggregateID := event.FromUserID
	return p.writer.Create(ctx, repository.OutboxCreateParams{
		AggregateType: "relation",
		AggregateID:   &aggregateID,
		Type:          "relation." + string(event.Action),
		Payload:       string(payloadBytes),
	})
}

// PublishCounterActionChange 将互动行为变更事件落库到 outbox。
func (p *counterOutboxEventPublisher) PublishCounterActionChange(ctx context.Context, event CounterActionChangeEvent) error {
	if p == nil || p.writer == nil {
		return nil
	}

	payloadBytes, err := json.Marshal(struct {
		Operation  CounterActionOperation `json:"operation"`
		Metric     string                 `json:"metric"`
		EntityType string                 `json:"entityType"`
		EntityID   string                 `json:"entityId"`
		UserID     uint64                 `json:"userId"`
		Active     bool                   `json:"active"`
		OccurredAt time.Time              `json:"occurredAt"`
	}{
		Operation:  event.Operation,
		Metric:     event.Metric,
		EntityType: event.EntityType,
		EntityID:   event.EntityID,
		UserID:     event.UserID,
		Active:     event.Active,
		OccurredAt: event.OccurredAt,
	})
	if err != nil {
		return fmt.Errorf("marshal counter outbox payload: %w", err)
	}

	typ := strings.TrimSpace(event.EntityType)
	if typ == "" {
		typ = "unknown"
	}

	var aggregateID *uint64
	if parsed, parseErr := strconv.ParseUint(strings.TrimSpace(event.EntityID), 10, 64); parseErr == nil {
		aggregateID = &parsed
	}

	return p.writer.Create(ctx, repository.OutboxCreateParams{
		AggregateType: "counter." + strings.ToLower(typ),
		AggregateID:   aggregateID,
		Type:          "counter." + strings.ToLower(strings.TrimSpace(event.Metric)) + "." + string(event.Operation),
		Payload:       string(payloadBytes),
	})
}
