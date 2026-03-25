package service

import (
	"context"
	"strings"
	"sync/atomic"
	"time"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
)

var knowPostDraftSeq atomic.Uint32

// KnowPostService 负责知文主链路的核心业务。
type KnowPostService interface {
	CreateDraft(ctx context.Context, creatorID uint64) (uint64, error)
}

type knowPostService struct {
	repo *repository.KnowPostRepository
}

// NewKnowPostService 创建知文服务。
func NewKnowPostService(repo *repository.KnowPostRepository) KnowPostService {
	return &knowPostService{repo: repo}
}

// CreateDraft 创建草稿并返回新知文 ID。
// 关键逻辑：生成业务 ID 后写入默认状态，若遇到主键冲突会重试生成，避免并发下偶发冲突导致创建失败。
func (s *knowPostService) CreateDraft(ctx context.Context, creatorID uint64) (uint64, error) {
	if creatorID == 0 {
		return 0, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	const maxAttempts = 3
	for attempt := 0; attempt < maxAttempts; attempt++ {
		now := time.Now()
		postID := nextKnowPostID(now)
		post := &model.KnowPost{
			ID:         postID,
			CreatorID:  creatorID,
			Status:     "draft",
			Type:       "image_text",
			Visible:    "public",
			IsTop:      false,
			CreateTime: now,
			UpdateTime: now,
		}
		if err := s.repo.CreateDraft(ctx, post); err != nil {
			if isDuplicateEntryError(err) && attempt+1 < maxAttempts {
				continue
			}
			return 0, err
		}
		return postID, nil
	}

	return 0, errorsx.New(errorsx.CodeInternalError, "创建草稿失败，请稍后重试")
}

func nextKnowPostID(now time.Time) uint64 {
	millis := uint64(now.UnixMilli())
	seq := uint64(knowPostDraftSeq.Add(1) & 0x0fff)
	return (millis << 12) | seq
}

func isDuplicateEntryError(err error) bool {
	if err == nil {
		return false
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "duplicate") && strings.Contains(lower, "entry")
}
