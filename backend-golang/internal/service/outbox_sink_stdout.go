package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"zhiguang/internal/model"
)

// StdoutOutboxSink 将 outbox 事件序列化后输出到 writer，用于开发联调验证 relay 链路。
type StdoutOutboxSink struct {
	writer io.Writer
}

// NewStdoutOutboxSink 创建 StdoutOutboxSink，writer 为空时默认输出到 stdout。
func NewStdoutOutboxSink(writer io.Writer) *StdoutOutboxSink {
	if writer == nil {
		writer = os.Stdout
	}
	return &StdoutOutboxSink{writer: writer}
}

// Publish 输出单条 outbox 事件。
func (s *StdoutOutboxSink) Publish(_ context.Context, event model.OutboxEvent) error {
	if s == nil || s.writer == nil {
		return fmt.Errorf("stdout outbox sink writer is not configured")
	}
	payload := struct {
		ID            uint64  `json:"id"`
		AggregateType string  `json:"aggregateType"`
		AggregateID   *uint64 `json:"aggregateId,omitempty"`
		Type          string  `json:"type"`
		Payload       string  `json:"payload"`
	}{
		ID:            event.ID,
		AggregateType: event.AggregateType,
		AggregateID:   event.AggregateID,
		Type:          event.Type,
		Payload:       event.Payload,
	}
	data, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal stdout outbox event: %w", err)
	}
	if _, err := s.writer.Write(append(data, '\n')); err != nil {
		return fmt.Errorf("write stdout outbox event: %w", err)
	}
	return nil
}
