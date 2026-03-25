package service

import (
	"context"
	"regexp"
	"strings"
	"sync/atomic"
	"time"
	"zhiguang/internal/config"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
)

var (
	knowPostDraftSeq    atomic.Uint32
	knowPostSha256Regex = regexp.MustCompile(`^[a-fA-F0-9]{64}$`)
)

// KnowPostService 负责知文主链路的核心业务。
type KnowPostService interface {
	CreateDraft(ctx context.Context, creatorID uint64) (uint64, error)
	ConfirmContent(ctx context.Context, creatorID uint64, postID uint64, req KnowPostContentConfirmRequest) error
}

type knowPostService struct {
	repo *repository.KnowPostRepository
	oss  config.OSSConfig
}

// KnowPostContentConfirmRequest 表示正文上传确认参数。
type KnowPostContentConfirmRequest struct {
	ObjectKey string
	ETag      string
	Size      int64
	SHA256    string
}

// NewKnowPostService 创建知文服务。
func NewKnowPostService(repo *repository.KnowPostRepository, oss config.OSSConfig) KnowPostService {
	return &knowPostService{repo: repo, oss: oss}
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

// ConfirmContent 在上传完成后确认正文对象信息并写回帖子。
// 关键逻辑：先校验 objectKey/etag/size/sha256 的完整性，再按作者归属更新，避免越权覆盖。
func (s *knowPostService) ConfirmContent(ctx context.Context, creatorID uint64, postID uint64, req KnowPostContentConfirmRequest) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	objectKey := strings.TrimSpace(req.ObjectKey)
	etag := strings.TrimSpace(req.ETag)
	sha256 := strings.TrimSpace(req.SHA256)
	if objectKey == "" || etag == "" || sha256 == "" || req.Size <= 0 {
		return errorsx.New(errorsx.CodeBadRequest, "objectKey/etag/size/sha256 参数不合法")
	}
	if !knowPostSha256Regex.MatchString(sha256) {
		return errorsx.New(errorsx.CodeBadRequest, "sha256 格式错误")
	}

	contentURL := buildKnowPostContentPublicURL(s.oss, objectKey)
	updated, err := s.repo.ConfirmContent(ctx, postID, creatorID, objectKey, etag, req.Size, strings.ToLower(sha256), contentURL)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "草稿不存在或无权限")
	}
	return nil
}

func nextKnowPostID(now time.Time) uint64 {
	millis := uint64(now.UnixMilli())
	seq := uint64(knowPostDraftSeq.Add(1) & 0x0fff)
	return (millis << 12) | seq
}

func buildKnowPostContentPublicURL(oss config.OSSConfig, objectKey string) string {
	trimmedKey := strings.TrimPrefix(strings.TrimSpace(objectKey), "/")
	if strings.TrimSpace(oss.PublicDomain) != "" {
		return strings.TrimRight(oss.PublicDomain, "/") + "/" + trimmedKey
	}
	endpoint := strings.TrimPrefix(strings.TrimPrefix(oss.Endpoint, "https://"), "http://")
	return "https://" + oss.Bucket + "." + endpoint + "/" + trimmedKey
}

func isDuplicateEntryError(err error) bool {
	if err == nil {
		return false
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "duplicate") && strings.Contains(lower, "entry")
}
