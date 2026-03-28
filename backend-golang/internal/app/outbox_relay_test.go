package app

import (
	"context"
	"errors"
	"strings"
	"testing"
	"zhiguang/internal/config"
	"zhiguang/internal/service"
)

type kafkaProducerStub struct{}

func (s *kafkaProducerStub) Publish(_ context.Context, _ string, _ string, _ []byte) error {
	return nil
}

func TestBuildOutboxSink_DefaultStdout(t *testing.T) {
	cfg := &config.Config{}
	sink, err := buildOutboxSink(cfg, RunOutboxRelayOnceOptions{})
	if err != nil {
		t.Fatalf("buildOutboxSink() error = %v", err)
	}
	if _, ok := sink.(*service.StdoutOutboxSink); !ok {
		t.Fatalf("sink type = %T, want *service.StdoutOutboxSink", sink)
	}
}

func TestBuildOutboxSink_KafkaRequiresProducer(t *testing.T) {
	cfg := &config.Config{Outbox: config.OutboxConfig{Relay: config.OutboxRelayConfig{Sink: "kafka"}}}
	_, err := buildOutboxSink(cfg, RunOutboxRelayOnceOptions{})
	if !errors.Is(err, errOutboxRelayKafkaProducerNotConfigured) {
		t.Fatalf("buildOutboxSink() err = %v, want errOutboxRelayKafkaProducerNotConfigured", err)
	}
}

func TestBuildOutboxSink_KafkaConfigured(t *testing.T) {
	cfg := &config.Config{
		Outbox: config.OutboxConfig{
			Relay: config.OutboxRelayConfig{Sink: "kafka"},
			Kafka: config.OutboxKafkaConfig{
				TopicDefault: "outbox.default",
				AggregateTopics: map[string]string{
					"relation": "outbox.relation",
				},
			},
		},
	}
	sink, err := buildOutboxSink(cfg, RunOutboxRelayOnceOptions{KafkaProducer: &kafkaProducerStub{}})
	if err != nil {
		t.Fatalf("buildOutboxSink() error = %v", err)
	}
	if _, ok := sink.(*service.KafkaOutboxSink); !ok {
		t.Fatalf("sink type = %T, want *service.KafkaOutboxSink", sink)
	}
}

func TestBuildOutboxSink_UnsupportedSink(t *testing.T) {
	cfg := &config.Config{Outbox: config.OutboxConfig{Relay: config.OutboxRelayConfig{Sink: "invalid"}}}
	_, err := buildOutboxSink(cfg, RunOutboxRelayOnceOptions{})
	if err == nil {
		t.Fatal("buildOutboxSink() expected error")
	}
	if !strings.Contains(err.Error(), "unsupported outbox relay sink") {
		t.Fatalf("buildOutboxSink() err = %v, want unsupported sink error", err)
	}
}

func TestRunOutboxRelayOnce_NilConfig(t *testing.T) {
	published, err := RunOutboxRelayOnce(context.Background(), nil, RunOutboxRelayOnceOptions{})
	if !errors.Is(err, errOutboxRelayConfigRequired) {
		t.Fatalf("RunOutboxRelayOnce() err = %v, want errOutboxRelayConfigRequired", err)
	}
	if published != 0 {
		t.Fatalf("published = %d, want 0", published)
	}
}
