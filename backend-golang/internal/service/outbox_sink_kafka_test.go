package service

import (
	"context"
	"errors"
	"testing"
	"zhiguang/internal/model"
)

type kafkaProducerSpy struct {
	topic string
	key   string
	value []byte
	err   error
}

func (s *kafkaProducerSpy) Publish(_ context.Context, topic string, key string, value []byte) error {
	s.topic = topic
	s.key = key
	s.value = append([]byte(nil), value...)
	return s.err
}

func TestKafkaOutboxSinkPublish_UsesAggregateTopicOverride(t *testing.T) {
	producer := &kafkaProducerSpy{}
	sink := NewKafkaOutboxSink(producer,
		WithKafkaOutboxDefaultTopic("outbox.default"),
		WithKafkaOutboxAggregateTopic("relation", "outbox.relation"),
	)
	aggID := uint64(1001)
	err := sink.Publish(context.Background(), model.OutboxEvent{ID: 1, AggregateType: "relation", AggregateID: &aggID, Payload: `{"x":1}`})
	if err != nil {
		t.Fatalf("Publish() error = %v", err)
	}
	if producer.topic != "outbox.relation" {
		t.Fatalf("topic = %s, want outbox.relation", producer.topic)
	}
	if producer.key != "relation:1001" {
		t.Fatalf("key = %s, want relation:1001", producer.key)
	}
}

func TestKafkaOutboxSinkPublish_UsesDefaultTopic(t *testing.T) {
	producer := &kafkaProducerSpy{}
	sink := NewKafkaOutboxSink(producer, WithKafkaOutboxDefaultTopic("outbox.default"))

	err := sink.Publish(context.Background(), model.OutboxEvent{ID: 11, AggregateType: "counter.knowpost", Payload: `{"x":1}`})
	if err != nil {
		t.Fatalf("Publish() error = %v", err)
	}
	if producer.topic != "outbox.default" {
		t.Fatalf("topic = %s, want outbox.default", producer.topic)
	}
	if producer.key != "counter.knowpost:11" {
		t.Fatalf("key = %s, want counter.knowpost:11", producer.key)
	}
}

func TestKafkaOutboxSinkPublish_ProducerNotConfigured(t *testing.T) {
	sink := NewKafkaOutboxSink(nil)
	err := sink.Publish(context.Background(), model.OutboxEvent{ID: 1, AggregateType: "relation", Payload: `{}`})
	if !errors.Is(err, errKafkaOutboxProducerNotConfigured) {
		t.Fatalf("Publish() err = %v, want errKafkaOutboxProducerNotConfigured", err)
	}
}

func TestKafkaOutboxSinkPublish_TopicNotConfigured(t *testing.T) {
	producer := &kafkaProducerSpy{}
	sink := NewKafkaOutboxSink(producer, WithKafkaOutboxDefaultTopic(""))
	sink.defaultTopic = ""
	err := sink.Publish(context.Background(), model.OutboxEvent{ID: 1, AggregateType: "relation", Payload: `{}`})
	if !errors.Is(err, errKafkaOutboxTopicNotConfigured) {
		t.Fatalf("Publish() err = %v, want errKafkaOutboxTopicNotConfigured", err)
	}
}
