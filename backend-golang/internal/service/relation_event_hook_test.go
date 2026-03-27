package service

import (
	"context"
	"errors"
	"testing"
)

type relationEventPublisherSpy struct {
	events []RelationChangeEvent
	err    error
}

func (s *relationEventPublisherSpy) PublishRelationChange(_ context.Context, event RelationChangeEvent) error {
	s.events = append(s.events, event)
	return s.err
}

func TestNewRelationService_DefaultRelationEventPublisher(t *testing.T) {
	svc, ok := NewRelationService(nil, nil, nil, nil).(*relationService)
	if !ok {
		t.Fatal("NewRelationService() should return *relationService")
	}
	if svc.relationEvents == nil {
		t.Fatal("default relationEvents should not be nil")
	}
}

func TestNewRelationService_WithRelationEventPublisher(t *testing.T) {
	spy := &relationEventPublisherSpy{}
	svc, ok := NewRelationService(nil, nil, nil, nil, WithRelationEventPublisher(spy)).(*relationService)
	if !ok {
		t.Fatal("NewRelationService() should return *relationService")
	}
	if svc.relationEvents != spy {
		t.Fatal("relationEvents should use injected publisher")
	}
}

func TestRelationServiceEmitRelationChange_PublishOnChanged(t *testing.T) {
	spy := &relationEventPublisherSpy{}
	svc := &relationService{relationEvents: spy}

	svc.emitRelationChange(context.Background(), RelationChangeActionFollow, 1001, 1002, true)

	if len(spy.events) != 1 {
		t.Fatalf("len(events) = %d, want 1", len(spy.events))
	}
	event := spy.events[0]
	if event.Action != RelationChangeActionFollow {
		t.Fatalf("event.Action = %s, want %s", event.Action, RelationChangeActionFollow)
	}
	if event.FromUserID != 1001 || event.ToUserID != 1002 {
		t.Fatalf("event users = (%d,%d), want (1001,1002)", event.FromUserID, event.ToUserID)
	}
	if event.OccurredAt.IsZero() {
		t.Fatal("event.OccurredAt should not be zero")
	}
}

func TestRelationServiceEmitRelationChange_SkipWhenUnchanged(t *testing.T) {
	spy := &relationEventPublisherSpy{}
	svc := &relationService{relationEvents: spy}

	svc.emitRelationChange(context.Background(), RelationChangeActionUnfollow, 1001, 1002, false)

	if len(spy.events) != 0 {
		t.Fatalf("len(events) = %d, want 0", len(spy.events))
	}
}

func TestRelationServiceEmitRelationChange_IgnorePublisherError(t *testing.T) {
	spy := &relationEventPublisherSpy{err: errors.New("publish failed")}
	svc := &relationService{relationEvents: spy}

	svc.emitRelationChange(context.Background(), RelationChangeActionFollow, 1001, 1002, true)

	if len(spy.events) != 1 {
		t.Fatalf("len(events) = %d, want 1", len(spy.events))
	}
}
