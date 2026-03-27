package service

import (
	"context"
	"time"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
)

// RelationService 提供关注关系能力。
type RelationService interface {
	Follow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error)
	Unfollow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error)
	Status(ctx context.Context, fromUserID uint64, toUserID uint64) (RelationStatus, error)
	FollowingProfiles(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]ProfileResponse, error)
	FollowerProfiles(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]ProfileResponse, error)
	Counters(ctx context.Context, userID uint64) (RelationCounters, error)
}

// RelationStatus 表示用户关系三态。
type RelationStatus struct {
	Following  bool
	FollowedBy bool
	Mutual     bool
}

// RelationCounters 表示用户关系与互动维度计数。
type RelationCounters struct {
	Followings int64
	Followers  int64
	Posts      int64
	LikedPosts int64
	FavedPosts int64
}

// RelationServiceOption 负责扩展 RelationService 的可选能力。
type RelationServiceOption func(*relationService)

// WithRelationEventPublisher 注入关系事件发布器。
func WithRelationEventPublisher(publisher RelationEventPublisher) RelationServiceOption {
	return func(s *relationService) {
		if publisher != nil {
			s.relationEvents = publisher
		}
	}
}

type relationService struct {
	relations      *repository.RelationRepository
	users          *repository.UserRepository
	knowposts      *repository.KnowPostRepository
	counters       *repository.CounterRepository
	relationEvents RelationEventPublisher
}

// NewRelationService 创建 RelationService。
func NewRelationService(relations *repository.RelationRepository, users *repository.UserRepository, knowposts *repository.KnowPostRepository, counters *repository.CounterRepository, opts ...RelationServiceOption) RelationService {
	svc := &relationService{
		relations:      relations,
		users:          users,
		knowposts:      knowposts,
		counters:       counters,
		relationEvents: NopRelationEventPublisher{},
	}
	for _, opt := range opts {
		if opt != nil {
			opt(svc)
		}
	}
	if svc.relationEvents == nil {
		svc.relationEvents = NopRelationEventPublisher{}
	}
	return svc
}

// Follow 创建关注关系。
func (s *relationService) Follow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	if err := validateRelationUsers(fromUserID, toUserID); err != nil {
		return false, err
	}
	changed, err := s.relations.Follow(ctx, fromUserID, toUserID)
	if err != nil {
		return false, err
	}
	s.emitRelationChange(ctx, RelationChangeActionFollow, fromUserID, toUserID, changed)
	return changed, nil
}

// Unfollow 删除关注关系。
func (s *relationService) Unfollow(ctx context.Context, fromUserID uint64, toUserID uint64) (bool, error) {
	if err := validateRelationUsers(fromUserID, toUserID); err != nil {
		return false, err
	}
	changed, err := s.relations.Unfollow(ctx, fromUserID, toUserID)
	if err != nil {
		return false, err
	}
	s.emitRelationChange(ctx, RelationChangeActionUnfollow, fromUserID, toUserID, changed)
	return changed, nil
}

// Status 查询双方关注关系。
func (s *relationService) Status(ctx context.Context, fromUserID uint64, toUserID uint64) (RelationStatus, error) {
	if err := validateRelationUsers(fromUserID, toUserID); err != nil {
		return RelationStatus{}, err
	}

	following, err := s.relations.IsFollowing(ctx, fromUserID, toUserID)
	if err != nil {
		return RelationStatus{}, err
	}
	followedBy, err := s.relations.IsFollowing(ctx, toUserID, fromUserID)
	if err != nil {
		return RelationStatus{}, err
	}
	return RelationStatus{Following: following, FollowedBy: followedBy, Mutual: following && followedBy}, nil
}

// FollowingProfiles 返回关注列表资料。
func (s *relationService) FollowingProfiles(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]ProfileResponse, error) {
	if userID == 0 {
		return []ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "userId 非法")
	}
	limit, offset = normalizeRelationPage(limit, offset)

	ids, err := s.relations.ListFollowingIDs(ctx, userID, limit, offset, cursor)
	if err != nil {
		return nil, err
	}
	return s.loadProfilesByIDs(ctx, ids)
}

// FollowerProfiles 返回粉丝列表资料。
func (s *relationService) FollowerProfiles(ctx context.Context, userID uint64, limit int, offset int, cursor *int64) ([]ProfileResponse, error) {
	if userID == 0 {
		return []ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "userId 非法")
	}
	limit, offset = normalizeRelationPage(limit, offset)

	ids, err := s.relations.ListFollowerIDs(ctx, userID, limit, offset, cursor)
	if err != nil {
		return nil, err
	}
	return s.loadProfilesByIDs(ctx, ids)
}

// Counters 返回关系与互动维度计数。
func (s *relationService) Counters(ctx context.Context, userID uint64) (RelationCounters, error) {
	if userID == 0 {
		return RelationCounters{}, errorsx.New(errorsx.CodeBadRequest, "userId 非法")
	}

	followings, err := s.relations.CountFollowings(ctx, userID)
	if err != nil {
		return RelationCounters{}, err
	}
	followers, err := s.relations.CountFollowers(ctx, userID)
	if err != nil {
		return RelationCounters{}, err
	}
	posts, err := s.knowposts.CountPublishedByCreator(ctx, userID)
	if err != nil {
		return RelationCounters{}, err
	}
	likedPosts, err := s.counters.CountUserMetric(ctx, counterMetricLike, userID)
	if err != nil {
		return RelationCounters{}, err
	}
	favedPosts, err := s.counters.CountUserMetric(ctx, counterMetricFav, userID)
	if err != nil {
		return RelationCounters{}, err
	}

	return RelationCounters{
		Followings: followings,
		Followers:  followers,
		Posts:      posts,
		LikedPosts: likedPosts,
		FavedPosts: favedPosts,
	}, nil
}

func (s *relationService) emitRelationChange(ctx context.Context, action RelationChangeAction, fromUserID uint64, toUserID uint64, changed bool) {
	if !changed || s.relationEvents == nil {
		return
	}
	_ = s.relationEvents.PublishRelationChange(ctx, RelationChangeEvent{
		Action:     action,
		FromUserID: fromUserID,
		ToUserID:   toUserID,
		OccurredAt: time.Now().UTC(),
	})
}

func (s *relationService) loadProfilesByIDs(ctx context.Context, ids []uint64) ([]ProfileResponse, error) {
	if len(ids) == 0 {
		return []ProfileResponse{}, nil
	}

	users, err := s.users.FindByIDs(ctx, ids)
	if err != nil {
		return nil, err
	}

	profiles := make([]ProfileResponse, 0, len(users))
	for i := range users {
		user := users[i]
		profiles = append(profiles, mapProfileResponse(&user))
	}
	return profiles, nil
}

func validateRelationUsers(fromUserID uint64, toUserID uint64) error {
	if fromUserID == 0 || toUserID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "用户标识非法")
	}
	if fromUserID == toUserID {
		return errorsx.New(errorsx.CodeBadRequest, "不能关注自己")
	}
	return nil
}

func normalizeRelationPage(limit int, offset int) (int, int) {
	if limit <= 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}
	if offset < 0 {
		offset = 0
	}
	return limit, offset
}
