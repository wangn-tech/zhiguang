package service

import (
	"bytes"
	"context"
	"encoding/json"
	"testing"
	"zhiguang/internal/model"
)

func TestStdoutOutboxSinkPublish_WritesJSONLine(t *testing.T) {
	buf := &bytes.Buffer{}
	sink := NewStdoutOutboxSink(buf)
	aggID := uint64(3001)

	err := sink.Publish(context.Background(), model.OutboxEvent{
		ID:            10,
		AggregateType: "relation",
		AggregateID:   &aggID,
		Type:          "relation.followed",
		Payload:       `{"followerId":1,"followeeId":2}`,
	})
	if err != nil {
		t.Fatalf("Publish() error = %v", err)
	}

	line := bytes.TrimSpace(buf.Bytes())
	if len(line) == 0 {
		t.Fatal("stdout line is empty")
	}
	var got map[string]any
	if err := json.Unmarshal(line, &got); err != nil {
		t.Fatalf("json.Unmarshal() error = %v", err)
	}
	if got["aggregateType"] != "relation" {
		t.Fatalf("aggregateType = %v, want relation", got["aggregateType"])
	}
	if got["payload"] != `{"followerId":1,"followeeId":2}` {
		t.Fatalf("payload = %v, want relation payload string", got["payload"])
	}
}

func TestStdoutOutboxSinkPublish_WriterNotConfigured(t *testing.T) {
	sink := &StdoutOutboxSink{}
	err := sink.Publish(context.Background(), model.OutboxEvent{ID: 1})
	if err == nil {
		t.Fatal("Publish() expected error when writer is nil")
	}
}
