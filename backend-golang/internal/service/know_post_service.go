package service

import (
	"context"
	"encoding/json"
	"regexp"
	"strconv"
	"strings"
	"sync/atomic"
	"time"
	"unicode/utf8"
	"zhiguang/internal/config"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
)

var (
	knowPostDraftSeq    atomic.Uint32
	knowPostSha256Regex = regexp.MustCompile("^[a-fA-F0-9]{64}$")
)

// KnowPostService 负责知文主链路的核心业务。
type KnowPostService interface {
	CreateDraft(ctx context.Context, creatorID uint64) (uint64, error)
	ConfirmContent(ctx context.Context, creatorID uint64, postID uint64, req KnowPostContentConfirmRequest) error
	UpdateMetadata(ctx context.Context, creatorID uint64, postID uint64, req KnowPostMetadataPatchRequest) error
	Publish(ctx context.Context, creatorID uint64, postID uint64) error
	UpdateTop(ctx context.Context, creatorID uint64, postID uint64, isTop bool) error
	UpdateVisibility(ctx context.Context, creatorID uint64, postID uint64, visible string) error
	Delete(ctx context.Context, creatorID uint64, postID uint64) error
	GetPublicFeed(ctx context.Context, page int, size int) (KnowPostFeedPage, error)
	GetMyPublished(ctx context.Context, creatorID uint64, page int, size int) (KnowPostFeedPage, error)
	GetDetail(ctx context.Context, postID uint64, currentUserID *uint64) (KnowPostDetail, error)
	SuggestDescription(ctx context.Context, creatorID uint64, content string) (string, error)
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

// KnowPostMetadataPatchRequest 表示知文元数据更新参数。
type KnowPostMetadataPatchRequest struct {
	Title       *string
	TagID       *int64
	Tags        *[]string
	ImageURLs   *[]string
	Visible     *string
	IsTop       *bool
	Description *string
}

// KnowPostFeedPage 表示公开 feed 分页结果。
type KnowPostFeedPage struct {
	Items   []KnowPostFeedItem
	Page    int
	Size    int
	HasMore bool
}

// KnowPostFeedItem 表示 feed 中的一条知文记录。
type KnowPostFeedItem struct {
	ID             string
	Title          string
	Description    string
	CoverImage     string
	Tags           []string
	TagJSON        string
	AuthorAvatar   string
	AuthorNickname string
	LikeCount      int64
	FavoriteCount  int64
	Liked          bool
	Faved          bool
	IsTop          bool
	Visible        string
}

// KnowPostDetail 表示知文详情数据。
type KnowPostDetail struct {
	ID             string
	Title          string
	Description    string
	ContentURL     string
	Images         []string
	Tags           []string
	AuthorID       uint64
	AuthorAvatar   string
	AuthorNickname string
	AuthorTagJSON  string
	LikeCount      int64
	FavoriteCount  int64
	Liked          bool
	Faved          bool
	IsTop          bool
	Visible        string
	Type           string
	PublishTime    *time.Time
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

// UpdateMetadata 更新知文元数据字段。
// 关键逻辑：对 title/tags/imgUrls/visible/description 做入参校验，按作者归属执行部分更新。
func (s *knowPostService) UpdateMetadata(ctx context.Context, creatorID uint64, postID uint64, req KnowPostMetadataPatchRequest) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	updates := make(map[string]any)

	if req.Title != nil {
		title := strings.TrimSpace(*req.Title)
		if title == "" {
			return errorsx.New(errorsx.CodeBadRequest, "标题不能为空")
		}
		if utf8.RuneCountInString(title) > 256 {
			return errorsx.New(errorsx.CodeBadRequest, "标题长度不能超过 256")
		}
		updates["title"] = title
	}

	if req.TagID != nil {
		if *req.TagID <= 0 {
			return errorsx.New(errorsx.CodeBadRequest, "tagId 非法")
		}
		updates["tag_id"] = *req.TagID
	}

	if req.Tags != nil {
		if len(*req.Tags) > 20 {
			return errorsx.New(errorsx.CodeBadRequest, "tags 最多 20 项")
		}
		normalized, err := normalizeStringList(*req.Tags, "tags")
		if err != nil {
			return err
		}
		raw, err := json.Marshal(normalized)
		if err != nil {
			return errorsx.New(errorsx.CodeBadRequest, "tags 序列化失败")
		}
		updates["tags"] = string(raw)
	}

	if req.ImageURLs != nil {
		if len(*req.ImageURLs) > 20 {
			return errorsx.New(errorsx.CodeBadRequest, "imgUrls 最多 20 项")
		}
		normalized, err := normalizeStringList(*req.ImageURLs, "imgUrls")
		if err != nil {
			return err
		}
		raw, err := json.Marshal(normalized)
		if err != nil {
			return errorsx.New(errorsx.CodeBadRequest, "imgUrls 序列化失败")
		}
		updates["img_urls"] = string(raw)
	}

	if req.Visible != nil {
		visible := strings.ToLower(strings.TrimSpace(*req.Visible))
		if !isValidVisible(visible) {
			return errorsx.New(errorsx.CodeBadRequest, "visible 取值非法")
		}
		updates["visible"] = visible
	}

	if req.IsTop != nil {
		updates["is_top"] = *req.IsTop
	}

	if req.Description != nil {
		description := strings.TrimSpace(*req.Description)
		if utf8.RuneCountInString(description) > 50 {
			return errorsx.New(errorsx.CodeBadRequest, "description 长度不能超过 50")
		}
		updates["description"] = description
	}

	if len(updates) == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "未提交任何更新字段")
	}

	updates["type"] = "image_text"
	updated, err := s.repo.UpdateMetadata(ctx, postID, creatorID, updates)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "草稿不存在或无权限")
	}
	return nil
}

// Publish 发布知文，将状态切换为 published 并记录发布时间。
func (s *knowPostService) Publish(ctx context.Context, creatorID uint64, postID uint64) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	updated, err := s.repo.Publish(ctx, postID, creatorID)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "草稿不存在或无权限")
	}
	return nil
}

// UpdateTop 更新知文置顶状态。
func (s *knowPostService) UpdateTop(ctx context.Context, creatorID uint64, postID uint64, isTop bool) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	updated, err := s.repo.UpdateTop(ctx, postID, creatorID, isTop)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "知文不存在或无权限")
	}
	return nil
}

// UpdateVisibility 更新知文可见性。
func (s *knowPostService) UpdateVisibility(ctx context.Context, creatorID uint64, postID uint64, visible string) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	normalized := strings.ToLower(strings.TrimSpace(visible))
	if !isValidVisible(normalized) {
		return errorsx.New(errorsx.CodeBadRequest, "visible 取值非法")
	}

	updated, err := s.repo.UpdateVisibility(ctx, postID, creatorID, normalized)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "知文不存在或无权限")
	}
	return nil
}

// Delete 软删除知文。
func (s *knowPostService) Delete(ctx context.Context, creatorID uint64, postID uint64) error {
	if creatorID == 0 || postID == 0 {
		return errorsx.New(errorsx.CodeBadRequest, "请求参数错误")
	}

	updated, err := s.repo.SoftDelete(ctx, postID, creatorID)
	if err != nil {
		return err
	}
	if !updated {
		return errorsx.New(errorsx.CodeBadRequest, "知文不存在或无权限")
	}
	return nil
}

// GetPublicFeed 返回公开已发布知文列表。
// 关键逻辑：仅查询 published+public 的记录，并将 JSON 字段转为前端可直接消费的数据结构。
func (s *knowPostService) GetPublicFeed(ctx context.Context, page int, size int) (KnowPostFeedPage, error) {
	safePage, safeSize := normalizeKnowPostPageSize(page, size)
	rows, hasMore, err := s.repo.ListPublicFeed(ctx, safePage, safeSize)
	if err != nil {
		return KnowPostFeedPage{}, err
	}

	items := make([]KnowPostFeedItem, 0, len(rows))
	for _, row := range rows {
		imageURLs := parseJSONStringArray(row.ImageURLsJSON)
		tags := parseJSONStringArray(row.TagsJSON)

		coverImage := ""
		if len(imageURLs) > 0 {
			coverImage = imageURLs[0]
		}

		authorAvatar := ""
		if row.AuthorAvatar != nil {
			authorAvatar = strings.TrimSpace(*row.AuthorAvatar)
		}

		authorTagJSON := ""
		if row.AuthorTagJSON != nil {
			authorTagJSON = strings.TrimSpace(*row.AuthorTagJSON)
		}

		items = append(items, KnowPostFeedItem{
			ID:             strconv.FormatUint(row.ID, 10),
			Title:          strings.TrimSpace(row.Title),
			Description:    strings.TrimSpace(row.Description),
			CoverImage:     coverImage,
			Tags:           tags,
			TagJSON:        authorTagJSON,
			AuthorAvatar:   authorAvatar,
			AuthorNickname: strings.TrimSpace(row.AuthorNickname),
			LikeCount:      0,
			FavoriteCount:  0,
			Liked:          false,
			Faved:          false,
			IsTop:          row.IsTop,
			Visible:        strings.TrimSpace(row.Visible),
		})
	}

	return KnowPostFeedPage{
		Items:   items,
		Page:    safePage,
		Size:    safeSize,
		HasMore: hasMore,
	}, nil
}

// GetMyPublished 返回当前用户已发布的知文列表。
// 关键逻辑：仅查询当前用户 status=published 的记录，复用 feed 响应结构以降低前端渲染分支。
func (s *knowPostService) GetMyPublished(ctx context.Context, creatorID uint64, page int, size int) (KnowPostFeedPage, error) {
	if creatorID == 0 {
		return KnowPostFeedPage{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	safePage, safeSize := normalizeKnowPostPageSize(page, size)
	rows, hasMore, err := s.repo.ListMyPublished(ctx, creatorID, safePage, safeSize)
	if err != nil {
		return KnowPostFeedPage{}, err
	}

	items := make([]KnowPostFeedItem, 0, len(rows))
	for _, row := range rows {
		imageURLs := parseJSONStringArray(row.ImageURLsJSON)
		tags := parseJSONStringArray(row.TagsJSON)

		coverImage := ""
		if len(imageURLs) > 0 {
			coverImage = imageURLs[0]
		}

		authorAvatar := ""
		if row.AuthorAvatar != nil {
			authorAvatar = strings.TrimSpace(*row.AuthorAvatar)
		}

		authorTagJSON := ""
		if row.AuthorTagJSON != nil {
			authorTagJSON = strings.TrimSpace(*row.AuthorTagJSON)
		}

		items = append(items, KnowPostFeedItem{
			ID:             strconv.FormatUint(row.ID, 10),
			Title:          strings.TrimSpace(row.Title),
			Description:    strings.TrimSpace(row.Description),
			CoverImage:     coverImage,
			Tags:           tags,
			TagJSON:        authorTagJSON,
			AuthorAvatar:   authorAvatar,
			AuthorNickname: strings.TrimSpace(row.AuthorNickname),
			LikeCount:      0,
			FavoriteCount:  0,
			Liked:          false,
			Faved:          false,
			IsTop:          row.IsTop,
			Visible:        strings.TrimSpace(row.Visible),
		})
	}

	return KnowPostFeedPage{
		Items:   items,
		Page:    safePage,
		Size:    safeSize,
		HasMore: hasMore,
	}, nil
}

// GetDetail 返回知文详情。
// 关键逻辑：公开内容可匿名访问，非公开内容仅作者可访问。
func (s *knowPostService) GetDetail(ctx context.Context, postID uint64, currentUserID *uint64) (KnowPostDetail, error) {
	if postID == 0 {
		return KnowPostDetail{}, errorsx.New(errorsx.CodeBadRequest, "id 非法")
	}

	row, err := s.repo.GetDetailByID(ctx, postID)
	if err != nil {
		return KnowPostDetail{}, err
	}
	if row == nil || strings.EqualFold(strings.TrimSpace(row.Status), "deleted") {
		return KnowPostDetail{}, errorsx.New(errorsx.CodeBadRequest, "内容不存在")
	}

	status := strings.ToLower(strings.TrimSpace(row.Status))
	visible := strings.ToLower(strings.TrimSpace(row.Visible))
	isPublic := status == "published" && visible == "public"
	isOwner := currentUserID != nil && *currentUserID > 0 && *currentUserID == row.CreatorID
	if !isPublic && !isOwner {
		return KnowPostDetail{}, errorsx.New(errorsx.CodeBadRequest, "无权限查看")
	}

	authorAvatar := ""
	if row.AuthorAvatar != nil {
		authorAvatar = strings.TrimSpace(*row.AuthorAvatar)
	}
	authorTagJSON := ""
	if row.AuthorTagJSON != nil {
		authorTagJSON = strings.TrimSpace(*row.AuthorTagJSON)
	}

	typ := strings.TrimSpace(row.Type)
	if typ == "" {
		typ = "image_text"
	}

	return KnowPostDetail{
		ID:             strconv.FormatUint(row.ID, 10),
		Title:          strings.TrimSpace(row.Title),
		Description:    strings.TrimSpace(row.Description),
		ContentURL:     strings.TrimSpace(row.ContentURL),
		Images:         parseJSONStringArray(row.ImageURLsJSON),
		Tags:           parseJSONStringArray(row.TagsJSON),
		AuthorID:       row.CreatorID,
		AuthorAvatar:   authorAvatar,
		AuthorNickname: strings.TrimSpace(row.AuthorNickname),
		AuthorTagJSON:  authorTagJSON,
		LikeCount:      0,
		FavoriteCount:  0,
		Liked:          false,
		Faved:          false,
		IsTop:          row.IsTop,
		Visible:        visible,
		Type:           typ,
		PublishTime:    row.PublishTime,
	}, nil
}

// SuggestDescription 基于正文生成摘要建议。
// 关键逻辑：对正文做空白归一化并限制在 50 字符内，避免超长描述影响发布页展示。
func (s *knowPostService) SuggestDescription(_ context.Context, creatorID uint64, content string) (string, error) {
	if creatorID == 0 {
		return "", errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	normalized := strings.Join(strings.Fields(strings.TrimSpace(content)), " ")
	if normalized == "" {
		return "", errorsx.New(errorsx.CodeBadRequest, "content 不能为空")
	}
	if utf8.RuneCountInString(normalized) > 20000 {
		return "", errorsx.New(errorsx.CodeBadRequest, "content 长度不能超过 20000")
	}

	return truncateToRunes(normalized, 50), nil
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

func normalizeStringList(values []string, fieldName string) ([]string, error) {
	normalized := make([]string, 0, len(values))
	for _, value := range values {
		trimmed := strings.TrimSpace(value)
		if trimmed == "" {
			return nil, errorsx.New(errorsx.CodeBadRequest, fieldName+" 存在空值")
		}
		normalized = append(normalized, trimmed)
	}
	return normalized, nil
}

func normalizeKnowPostPageSize(page int, size int) (int, int) {
	const (
		defaultPage = 1
		defaultSize = 20
		maxSize     = 50
	)

	if page <= 0 {
		page = defaultPage
	}
	if size <= 0 {
		size = defaultSize
	}
	if size > maxSize {
		size = maxSize
	}
	return page, size
}

func parseJSONStringArray(raw *string) []string {
	if raw == nil {
		return []string{}
	}
	trimmed := strings.TrimSpace(*raw)
	if trimmed == "" {
		return []string{}
	}

	var values []string
	if err := json.Unmarshal([]byte(trimmed), &values); err != nil {
		return []string{}
	}
	return values
}

func truncateToRunes(input string, max int) string {
	if max <= 0 {
		return ""
	}
	runes := []rune(input)
	if len(runes) <= max {
		return input
	}
	return string(runes[:max])
}

func isValidVisible(visible string) bool {
	switch visible {
	case "public", "followers", "school", "private", "unlisted":
		return true
	default:
		return false
	}
}

func isDuplicateEntryError(err error) bool {
	if err == nil {
		return false
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "duplicate") && strings.Contains(lower, "entry")
}
