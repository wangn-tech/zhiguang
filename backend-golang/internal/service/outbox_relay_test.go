package service

import (
	"context"
	"errors"
	"testing"
	"zhiguang/internal/model"
)

type outboxRelayReaderSpy struct {
	events    []model.OutboxEvent
	err       error
	lastAfter uint64
	lastLimit int
}

func (s *outboxRelayReaderSpy) ListAfterID(_ context.Context, afterID uint64, limit int) ([]model.OutboxEvent, error) {
	s.lastAfter = afterID
	s.lastLimit = limit
	if s.err != nil {
		return nil, s.err
	}
	filtered := make([]model.OutboxEvent, 0, len(s.events))
	for _, event := range s.events {
		if event.ID > afterID {
			filtered = append(filtered, event)
		}
	}
	if len(filtered) > limit {
		filtered = filtered[:limit]
	}
	return filtered, nil
}

type outboxRelaySinkSpy struct {
	published []uint64
	failOnID  uint64
	err       error
}

func (s *outboxRelaySinkSpy) Publish(_ context.Context, event model.OutboxEvent) error {
	s.published = append(s.published, event.ID)
	if s.err != nil && event.ID == s.failOnID {
		return s.err
	}
	return nil
}

func TestOutboxRelayRunOnce_PublishAndAdvanceCursor(t *testing.T) {
	reader := &outboxRelayReaderSpy{events: []model.OutboxEvent{{ID: 1}, {ID: 2}, {ID: 3}}}
	sink := &outboxRelaySinkSpy{}
	relay := NewOutboxRelay(reader, sink, WithOutboxRelayBatchSize(2))

	published, err := relay.RunOnce(context.Background())
	if err != nil {
		t.Fatalf("RunOnce() error = %v", err)
	}
	if published != 2 {
		t.Fatalf("published = %d, want 2", published)
	}
	if relay.LastID() != 2 {
		t.Fatalf("LastID = %d, want 2", relay.LastID())
	}
	if reader.lastAfter != 0 || reader.lastLimit != 2 {
		t.Fatalf("reader args after=%d limit=%d, want 0 and 2", reader.lastAfter, reader.lastLimit)
	}
	if len(sink.published) != 2 || sink.published[0] != 1 || sink.published[1] != 2 {
		t.Fatalf("sink published = %v, want [1 2]", sink.published)
	}
}

func TestOutboxRelayRunOnce_StopsOnPublishError(t *testing.T) {
	reader := &outboxRelayReaderSpy{events: []model.OutboxEvent{{ID: 10}, {ID: 11}, {ID: 12}}}
	sink := &outboxRelaySinkSpy{failOnID: 11, err: errors.New("sink failed")}
	relay := NewOutboxRelay(reader, sink)

	published, err := relay.RunOnce(context.Background())
	if err == nil {
		t.Fatal("RunOnce() expected publish error")
	}
	if published != 1 {
		t.Fatalf("published = %d, want 1", published)
	}
	if relay.LastID() != 10 {
		t.Fatalf("LastID = %d, want 10", relay.LastID())
	}
}

func TestOutboxRelayRunOnce_ReaderError(t *testing.T) {
	reader := &outboxRelayReaderSpy{err: errors.New("reader failed")}
	relay := NewOutboxRelay(reader, &outboxRelaySinkSpy{})

	published, err := relay.RunOnce(context.Background())
	if err == nil {
		t.Fatal("RunOnce() expected reader error")
	}
	if published != 0 {
		t.Fatalf("published = %d, want 0", published)
	}
}

func TestOutboxRelayRunOnce_NotConfigured(t *testing.T) {
	relay := NewOutboxRelay(nil, nil)

	_, err := relay.RunOnce(context.Background())
	if !errors.Is(err, errOutboxRelayReaderNotConfigured) {
		t.Fatalf("RunOnce() err = %v, want errOutboxRelayReaderNotConfigured", err)
	}

	relay = NewOutboxRelay(&outboxRelayReaderSpy{}, nil)
	_, err = relay.RunOnce(context.Background())
	if !errors.Is(err, errOutboxRelaySinkNotConfigured) {
		t.Fatalf("RunOnce() err = %v, want errOutboxRelaySinkNotConfigured", err)
	}
}
