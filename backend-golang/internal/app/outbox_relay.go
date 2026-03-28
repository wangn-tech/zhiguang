package app

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"
	"zhiguang/internal/config"
	"zhiguang/internal/repository"
	"zhiguang/internal/service"
	"zhiguang/internal/store"
)

var (
	errOutboxRelayConfigRequired             = errors.New("outbox relay config is required")
	errOutboxRelayKafkaProducerNotConfigured = errors.New("outbox relay kafka producer is not configured")
)

// RunOutboxRelayOnceOptions 包含执行一次 outbox relay 所需的可选依赖。
type RunOutboxRelayOnceOptions struct {
	KafkaProducer service.KafkaOutboxProducer
	StdoutWriter  io.Writer
}

// RunOutboxRelayOnce 执行一次 outbox relay 拉取并发布事件。
func RunOutboxRelayOnce(ctx context.Context, cfg *config.Config, opts RunOutboxRelayOnceOptions) (int, error) {
	if cfg == nil {
		return 0, errOutboxRelayConfigRequired
	}
	if ctx == nil {
		ctx = context.Background()
	}

	db, err := store.NewMySQL(cfg.MySQL.DSN)
	if err != nil {
		return 0, fmt.Errorf("open mysql: %w", err)
	}
	if sqlDB, sqlErr := db.DB(); sqlErr == nil {
		defer func() {
			_ = sqlDB.Close()
		}()
	}

	sink, err := buildOutboxSink(cfg, opts)
	if err != nil {
		return 0, err
	}

	relay := service.NewOutboxRelay(
		repository.NewOutboxRepository(db),
		sink,
		service.WithOutboxRelayBatchSize(cfg.Outbox.Relay.BatchSize),
	)
	published, err := relay.RunOnce(ctx)
	if err != nil {
		return published, fmt.Errorf("run outbox relay once: %w", err)
	}
	return published, nil
}

func buildOutboxSink(cfg *config.Config, opts RunOutboxRelayOnceOptions) (service.OutboxEventSink, error) {
	sinkType := strings.ToLower(strings.TrimSpace(cfg.Outbox.Relay.Sink))
	switch sinkType {
	case "", "stdout":
		return service.NewStdoutOutboxSink(opts.StdoutWriter), nil
	case "kafka":
		if opts.KafkaProducer == nil {
			return nil, errOutboxRelayKafkaProducerNotConfigured
		}
		sinkOptions := []service.KafkaOutboxSinkOption{
			service.WithKafkaOutboxDefaultTopic(cfg.Outbox.Kafka.TopicDefault),
		}
		for aggregateType, topic := range cfg.Outbox.Kafka.AggregateTopics {
			sinkOptions = append(sinkOptions, service.WithKafkaOutboxAggregateTopic(aggregateType, topic))
		}
		return service.NewKafkaOutboxSink(opts.KafkaProducer, sinkOptions...), nil
	default:
		return nil, fmt.Errorf("unsupported outbox relay sink: %s", sinkType)
	}
}
