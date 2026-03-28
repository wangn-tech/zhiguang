package service

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"zhiguang/internal/model"
)

var (
	errKafkaOutboxProducerNotConfigured = errors.New("kafka outbox producer is not configured")
	errKafkaOutboxTopicNotConfigured    = errors.New("kafka outbox topic is not configured")
)

// KafkaOutboxProducer 抽象 Kafka 发布能力，后续可由真实 Kafka 客户端实现。
type KafkaOutboxProducer interface {
	Publish(ctx context.Context, topic string, key string, value []byte) error
}

// KafkaOutboxSinkOption 扩展 KafkaOutboxSink 的配置。
type KafkaOutboxSinkOption func(*KafkaOutboxSink)

// WithKafkaOutboxDefaultTopic 设置默认发布 Topic。
func WithKafkaOutboxDefaultTopic(topic string) KafkaOutboxSinkOption {
	return func(s *KafkaOutboxSink) {
		t := strings.TrimSpace(topic)
		if t != "" {
			s.defaultTopic = t
		}
	}
}

// WithKafkaOutboxAggregateTopic 设置指定 aggregateType 的发布 Topic。
func WithKafkaOutboxAggregateTopic(aggregateType string, topic string) KafkaOutboxSinkOption {
	return func(s *KafkaOutboxSink) {
		typ := strings.TrimSpace(aggregateType)
		t := strings.TrimSpace(topic)
		if typ == "" || t == "" {
			return
		}
		s.aggregateTopics[strings.ToLower(typ)] = t
	}
}

// KafkaOutboxSink 将 outbox 事件转发到 Kafka。
type KafkaOutboxSink struct {
	producer        KafkaOutboxProducer
	defaultTopic    string
	aggregateTopics map[string]string
}

// NewKafkaOutboxSink 创建 KafkaOutboxSink。
func NewKafkaOutboxSink(producer KafkaOutboxProducer, opts ...KafkaOutboxSinkOption) *KafkaOutboxSink {
	sink := &KafkaOutboxSink{producer: producer, aggregateTopics: map[string]string{}, defaultTopic: "outbox.events"}
	for _, opt := range opts {
		if opt != nil {
			opt(sink)
		}
	}
	return sink
}

// Publish 发布单条 outbox 事件。
func (s *KafkaOutboxSink) Publish(ctx context.Context, event model.OutboxEvent) error {
	if s == nil || s.producer == nil {
		return errKafkaOutboxProducerNotConfigured
	}
	topic := s.resolveTopic(event.AggregateType)
	if topic == "" {
		return errKafkaOutboxTopicNotConfigured
	}
	key := buildKafkaOutboxKey(event)
	return s.producer.Publish(ctx, topic, key, []byte(event.Payload))
}

func (s *KafkaOutboxSink) resolveTopic(aggregateType string) string {
	if s == nil {
		return ""
	}
	typ := strings.ToLower(strings.TrimSpace(aggregateType))
	if typ != "" {
		if topic, ok := s.aggregateTopics[typ]; ok && strings.TrimSpace(topic) != "" {
			return topic
		}
	}
	return strings.TrimSpace(s.defaultTopic)
}

func buildKafkaOutboxKey(event model.OutboxEvent) string {
	aggType := strings.TrimSpace(event.AggregateType)
	if event.AggregateID != nil {
		return fmt.Sprintf("%s:%d", aggType, *event.AggregateID)
	}
	return fmt.Sprintf("%s:%d", aggType, event.ID)
}
