package service

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
	"zhiguang/internal/repository"
)

type outboxWriterSpy struct {
	params []repository.OutboxCreateParams
	err    error
}

func (s *outboxWriterSpy) Create(_ context.Context, params repository.OutboxCreateParams) error {
	s.params = append(s.params, params)
	return s.err
}

func TestRelationOutboxEventPublisher_PublishRelationChange(t *testing.T) {
	spy := &outboxWriterSpy{}
	publisher := &relationOutboxEventPublisher{writer: spy}

	err := publisher.PublishRelationChange(context.Background(), RelationChangeEvent{
		Action:     RelationChangeActionFollow,
		FromUserID: 1001,
		ToUserID:   1002,
		OccurredAt: time.Unix(1711449600, 0).UTC(),
	})
	if err != nil {
		t.Fatalf("PublishRelationChange() error = %v", err)
	}
	if len(spy.params) != 1 {
		t.Fatalf("len(params) = %d, want 1", len(spy.params))
	}
	created := spy.params[0]
	if created.AggregateType != "relation" {
		t.Fatalf("AggregateType = %s, want relation", created.AggregateType)
	}
	if created.AggregateID == nil || *created.AggregateID != 1001 {
		t.Fatalf("AggregateID = %v, want 1001", created.AggregateID)
	}
	if created.Type != "relation.follow" {
		t.Fatalf("Type = %s, want relation.follow", created.Type)
	}
	if !strings.Contains(created.Payload, "\"fromUserId\":1001") {
		t.Fatalf("payload = %s, want contains fromUserId", created.Payload)
	}
}

func TestRelationOutboxEventPublisher_PublishRelationChangeWriterError(t *testing.T) {
	spy := &outboxWriterSpy{err: errors.New("db failed")}
	publisher := &relationOutboxEventPublisher{writer: spy}

	err := publisher.PublishRelationChange(context.Background(), RelationChangeEvent{Action: RelationChangeActionUnfollow, FromUserID: 1001, ToUserID: 1002, OccurredAt: time.Now().UTC()})
	if err == nil {
		t.Fatal("PublishRelationChange() expected error")
	}
}

func TestCounterOutboxEventPublisher_PublishCounterActionChangeNumericAggregateID(t *testing.T) {
	spy := &outboxWriterSpy{}
	publisher := &counterOutboxEventPublisher{writer: spy}

	err := publisher.PublishCounterActionChange(context.Background(), CounterActionChangeEvent{
		Operation:  CounterActionOperationActivate,
		Metric:     "like",
		EntityType: "knowpost",
		EntityID:   "2001",
		UserID:     1001,
		Active:     true,
		OccurredAt: time.Unix(1711449600, 0).UTC(),
	})
	if err != nil {
		t.Fatalf("PublishCounterActionChange() error = %v", err)
	}
	if len(spy.params) != 1 {
		t.Fatalf("len(params) = %d, want 1", len(spy.params))
	}
	created := spy.params[0]
	if created.AggregateType != "counter.knowpost" {
		t.Fatalf("AggregateType = %s, want counter.knowpost", created.AggregateType)
	}
	if created.AggregateID == nil || *created.AggregateID != 2001 {
		t.Fatalf("AggregateID = %v, want 2001", created.AggregateID)
	}
	if created.Type != "counter.like.activate" {
		t.Fatalf("Type = %s, want counter.like.activate", created.Type)
	}
	if !strings.Contains(created.Payload, "\"entityId\":\"2001\"") {
		t.Fatalf("payload = %s, want contains entityId", created.Payload)
	}
}

func TestCounterOutboxEventPublisher_PublishCounterActionChangeNonNumericAggregateID(t *testing.T) {
	spy := &outboxWriterSpy{}
	publisher := &counterOutboxEventPublisher{writer: spy}

	err := publisher.PublishCounterActionChange(context.Background(), CounterActionChangeEvent{
		Operation:  CounterActionOperationDeactivate,
		Metric:     "fav",
		EntityType: "knowpost",
		EntityID:   "post-abc",
		UserID:     1001,
		Active:     false,
		OccurredAt: time.Unix(1711449600, 0).UTC(),
	})
	if err != nil {
		t.Fatalf("PublishCounterActionChange() error = %v", err)
	}
	if len(spy.params) != 1 {
		t.Fatalf("len(params) = %d, want 1", len(spy.params))
	}
	if spy.params[0].AggregateID != nil {
		t.Fatalf("AggregateID = %v, want nil", spy.params[0].AggregateID)
	}
}

func TestNewOutboxEventPublisher_NilRepoFallback(t *testing.T) {
	relationPublisher := NewRelationOutboxEventPublisher(nil)
	if err := relationPublisher.PublishRelationChange(context.Background(), RelationChangeEvent{Action: RelationChangeActionFollow, FromUserID: 1, ToUserID: 2, OccurredAt: time.Now().UTC()}); err != nil {
		t.Fatalf("relation nil repo fallback error = %v", err)
	}

	counterPublisher := NewCounterOutboxEventPublisher(nil)
	if err := counterPublisher.PublishCounterActionChange(context.Background(), CounterActionChangeEvent{Operation: CounterActionOperationActivate, Metric: "like", EntityType: "knowpost", EntityID: "1", UserID: 1, Active: true, OccurredAt: time.Now().UTC()}); err != nil {
		t.Fatalf("counter nil repo fallback error = %v", err)
	}
}
