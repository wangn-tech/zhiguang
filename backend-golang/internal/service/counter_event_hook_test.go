package service

import (
	"context"
	"errors"
	"testing"
)

type counterEventPublisherSpy struct {
	events []CounterActionChangeEvent
	err    error
}

func (s *counterEventPublisherSpy) PublishCounterActionChange(_ context.Context, event CounterActionChangeEvent) error {
	s.events = append(s.events, event)
	return s.err
}

func TestNewCounterService_DefaultCounterEventPublisher(t *testing.T) {
	svc, ok := NewCounterService(nil).(*counterService)
	if !ok {
		t.Fatal("NewCounterService() should return *counterService")
	}
	if svc.counterEvents == nil {
		t.Fatal("default counterEvents should not be nil")
	}
}

func TestNewCounterService_WithCounterEventPublisher(t *testing.T) {
	spy := &counterEventPublisherSpy{}
	svc, ok := NewCounterService(nil, WithCounterEventPublisher(spy)).(*counterService)
	if !ok {
		t.Fatal("NewCounterService() should return *counterService")
	}
	if svc.counterEvents != spy {
		t.Fatal("counterEvents should use injected publisher")
	}
}

func TestCounterServiceEmitCounterActionChange_PublishOnChanged(t *testing.T) {
	spy := &counterEventPublisherSpy{}
	svc := &counterService{counterEvents: spy}

	svc.emitCounterActionChange(context.Background(), CounterActionOperationActivate, counterMetricLike, "knowpost", "2001", 1001, true, true)

	if len(spy.events) != 1 {
		t.Fatalf("len(events) = %d, want 1", len(spy.events))
	}
	event := spy.events[0]
	if event.Operation != CounterActionOperationActivate {
		t.Fatalf("event.Operation = %s, want %s", event.Operation, CounterActionOperationActivate)
	}
	if event.Metric != counterMetricLike {
		t.Fatalf("event.Metric = %s, want %s", event.Metric, counterMetricLike)
	}
	if event.EntityType != "knowpost" || event.EntityID != "2001" || event.UserID != 1001 {
		t.Fatalf("event entity/user = (%s,%s,%d), want (knowpost,2001,1001)", event.EntityType, event.EntityID, event.UserID)
	}
	if !event.Active {
		t.Fatal("event.Active should be true")
	}
	if event.OccurredAt.IsZero() {
		t.Fatal("event.OccurredAt should not be zero")
	}
}

func TestCounterServiceEmitCounterActionChange_SkipWhenUnchanged(t *testing.T) {
	spy := &counterEventPublisherSpy{}
	svc := &counterService{counterEvents: spy}

	svc.emitCounterActionChange(context.Background(), CounterActionOperationDeactivate, counterMetricFav, "knowpost", "2001", 1001, false, false)

	if len(spy.events) != 0 {
		t.Fatalf("len(events) = %d, want 0", len(spy.events))
	}
}

func TestCounterServiceEmitCounterActionChange_IgnorePublisherError(t *testing.T) {
	spy := &counterEventPublisherSpy{err: errors.New("publish failed")}
	svc := &counterService{counterEvents: spy}

	svc.emitCounterActionChange(context.Background(), CounterActionOperationDeactivate, counterMetricLike, "knowpost", "2001", 1001, true, false)

	if len(spy.events) != 1 {
		t.Fatalf("len(events) = %d, want 1", len(spy.events))
	}
}
