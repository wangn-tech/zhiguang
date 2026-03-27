package service

import (
	"context"
	"strings"
	"time"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
)

const (
	counterMetricLike = "like"
	counterMetricFav  = "fav"
)

var counterSupportedMetrics = []string{counterMetricLike, counterMetricFav}

// CounterService 提供互动行为与计数查询能力。
type CounterService interface {
	Like(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error)
	Unlike(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error)
	Fav(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error)
	Unfav(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error)
	GetCounts(ctx context.Context, entityType string, entityID string, metrics []string) (map[string]int64, error)
}

// ActionResult 表示行为操作结果。
type ActionResult struct {
	Changed bool
	Active  bool
}

// CounterServiceOption 负责扩展 CounterService 的可选能力。
type CounterServiceOption func(*counterService)

// WithCounterEventPublisher 注入互动行为事件发布器。
func WithCounterEventPublisher(publisher CounterEventPublisher) CounterServiceOption {
	return func(s *counterService) {
		if publisher != nil {
			s.counterEvents = publisher
		}
	}
}

type counterService struct {
	repo          *repository.CounterRepository
	counterEvents CounterEventPublisher
}

// NewCounterService 创建 CounterService。
func NewCounterService(repo *repository.CounterRepository, opts ...CounterServiceOption) CounterService {
	svc := &counterService{repo: repo, counterEvents: NopCounterEventPublisher{}}
	for _, opt := range opts {
		if opt != nil {
			opt(svc)
		}
	}
	if svc.counterEvents == nil {
		svc.counterEvents = NopCounterEventPublisher{}
	}
	return svc
}

// Like 执行点赞操作并返回当前点赞状态。
func (s *counterService) Like(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error) {
	return s.activate(ctx, userID, entityType, entityID, counterMetricLike)
}

// Unlike 执行取消点赞操作并返回当前点赞状态。
func (s *counterService) Unlike(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error) {
	return s.deactivate(ctx, userID, entityType, entityID, counterMetricLike)
}

// Fav 执行收藏操作并返回当前收藏状态。
func (s *counterService) Fav(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error) {
	return s.activate(ctx, userID, entityType, entityID, counterMetricFav)
}

// Unfav 执行取消收藏操作并返回当前收藏状态。
func (s *counterService) Unfav(ctx context.Context, userID uint64, entityType string, entityID string) (ActionResult, error) {
	return s.deactivate(ctx, userID, entityType, entityID, counterMetricFav)
}

// GetCounts 返回指定实体在给定指标上的计数值。
func (s *counterService) GetCounts(ctx context.Context, entityType string, entityID string, metrics []string) (map[string]int64, error) {
	normalizedType, normalizedID, err := normalizeCounterEntity(entityType, entityID)
	if err != nil {
		return nil, err
	}

	targetMetrics := normalizeCounterMetrics(metrics)
	counts := make(map[string]int64, len(targetMetrics))
	for _, metric := range targetMetrics {
		count, err := s.repo.CountAction(ctx, metric, normalizedType, normalizedID)
		if err != nil {
			return nil, err
		}
		counts[metric] = count
	}
	return counts, nil
}

func (s *counterService) activate(ctx context.Context, userID uint64, entityType string, entityID string, metric string) (ActionResult, error) {
	normalizedType, normalizedID, err := normalizeCounterEntity(entityType, entityID)
	if err != nil {
		return ActionResult{}, err
	}
	if userID == 0 {
		return ActionResult{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	changed, err := s.repo.AddAction(ctx, metric, normalizedType, normalizedID, userID)
	if err != nil {
		return ActionResult{}, err
	}
	active, err := s.repo.ExistsAction(ctx, metric, normalizedType, normalizedID, userID)
	if err != nil {
		return ActionResult{}, err
	}
	s.emitCounterActionChange(ctx, CounterActionOperationActivate, metric, normalizedType, normalizedID, userID, changed, active)
	return ActionResult{Changed: changed, Active: active}, nil
}

func (s *counterService) deactivate(ctx context.Context, userID uint64, entityType string, entityID string, metric string) (ActionResult, error) {
	normalizedType, normalizedID, err := normalizeCounterEntity(entityType, entityID)
	if err != nil {
		return ActionResult{}, err
	}
	if userID == 0 {
		return ActionResult{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	changed, err := s.repo.RemoveAction(ctx, metric, normalizedType, normalizedID, userID)
	if err != nil {
		return ActionResult{}, err
	}
	active, err := s.repo.ExistsAction(ctx, metric, normalizedType, normalizedID, userID)
	if err != nil {
		return ActionResult{}, err
	}
	s.emitCounterActionChange(ctx, CounterActionOperationDeactivate, metric, normalizedType, normalizedID, userID, changed, active)
	return ActionResult{Changed: changed, Active: active}, nil
}

func (s *counterService) emitCounterActionChange(ctx context.Context, operation CounterActionOperation, metric string, entityType string, entityID string, userID uint64, changed bool, active bool) {
	if !changed || s.counterEvents == nil {
		return
	}
	_ = s.counterEvents.PublishCounterActionChange(ctx, CounterActionChangeEvent{
		Operation:  operation,
		Metric:     metric,
		EntityType: entityType,
		EntityID:   entityID,
		UserID:     userID,
		Active:     active,
		OccurredAt: time.Now().UTC(),
	})
}

func normalizeCounterEntity(entityType string, entityID string) (string, string, error) {
	typ := strings.ToLower(strings.TrimSpace(entityType))
	id := strings.TrimSpace(entityID)
	if typ == "" || id == "" {
		return "", "", errorsx.New(errorsx.CodeBadRequest, "entityType/entityId 不能为空")
	}
	return typ, id, nil
}

func normalizeCounterMetrics(metrics []string) []string {
	if len(metrics) == 0 {
		return append([]string{}, counterSupportedMetrics...)
	}

	set := make(map[string]struct{}, len(counterSupportedMetrics))
	for _, metric := range metrics {
		normalized := strings.ToLower(strings.TrimSpace(metric))
		if normalized == counterMetricLike || normalized == counterMetricFav {
			set[normalized] = struct{}{}
		}
	}
	if len(set) == 0 {
		return append([]string{}, counterSupportedMetrics...)
	}

	result := make([]string, 0, len(set))
	for _, metric := range counterSupportedMetrics {
		if _, ok := set[metric]; ok {
			result = append(result, metric)
		}
	}
	return result
}
