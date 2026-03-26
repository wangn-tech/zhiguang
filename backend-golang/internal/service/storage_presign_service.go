package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strconv"
	"strings"
	"time"
	"zhiguang/pkg/errorsx"
)

// KnowPostOwnershipChecker 提供知文归属校验能力。
type KnowPostOwnershipChecker interface {
	IsOwnedBy(ctx context.Context, postID uint64, userID uint64) (bool, error)
}

// StoragePresignService 负责构建对象 Key 并生成预签名链接。
type StoragePresignService interface {
	Presign(ctx context.Context, userID uint64, req StoragePresignRequest) (StoragePresignResponse, error)
}

// StoragePresignRequest 表示预签名请求参数。
type StoragePresignRequest struct {
	Scene       string
	PostID      string
	ContentType string
	Ext         string
}

// StoragePresignResponse 表示预签名响应参数。
type StoragePresignResponse struct {
	ObjectKey string
	PutURL    string
	Headers   map[string]string
	ExpiresIn int
}

type storagePresignService struct {
	storage   ObjectStorageService
	knowposts KnowPostOwnershipChecker
	expiresIn int
}

type storageExtRule struct {
	defaultExt string
	allowedExt map[string]struct{}
}

var storageContentRules = map[string]map[string]storageExtRule{
	"knowpost_content": {
		"text/markdown": {
			defaultExt: ".md",
			allowedExt: map[string]struct{}{".md": {}, ".markdown": {}},
		},
		"text/html": {
			defaultExt: ".html",
			allowedExt: map[string]struct{}{".html": {}, ".htm": {}},
		},
		"text/plain": {
			defaultExt: ".txt",
			allowedExt: map[string]struct{}{".txt": {}},
		},
		"application/json": {
			defaultExt: ".json",
			allowedExt: map[string]struct{}{".json": {}},
		},
	},
	"knowpost_image": {
		"image/jpeg": {
			defaultExt: ".jpg",
			allowedExt: map[string]struct{}{".jpg": {}, ".jpeg": {}},
		},
		"image/png": {
			defaultExt: ".png",
			allowedExt: map[string]struct{}{".png": {}},
		},
		"image/webp": {
			defaultExt: ".webp",
			allowedExt: map[string]struct{}{".webp": {}},
		},
		"image/svg+xml": {
			defaultExt: ".svg",
			allowedExt: map[string]struct{}{".svg": {}},
		},
		"image/gif": {
			defaultExt: ".gif",
			allowedExt: map[string]struct{}{".gif": {}},
		},
	},
}

// NewStoragePresignService 创建预签名服务。
func NewStoragePresignService(storage ObjectStorageService, knowposts KnowPostOwnershipChecker, expiresIn int) StoragePresignService {
	if expiresIn <= 0 {
		expiresIn = 600
	}
	return &storagePresignService{
		storage:   storage,
		knowposts: knowposts,
		expiresIn: expiresIn,
	}
}

// Presign 校验场景、文件类型与草稿归属后生成上传地址。
// 关键逻辑：按 scene+contentType 限制允许的扩展名，防止前端误传不合法文件组合。
func (s *storagePresignService) Presign(ctx context.Context, userID uint64, req StoragePresignRequest) (StoragePresignResponse, error) {
	if userID == 0 {
		return StoragePresignResponse{}, errorsx.New(errorsx.CodeBadRequest, "用户标识无效")
	}

	scene := strings.ToLower(strings.TrimSpace(req.Scene))
	contentType := normalizeContentType(req.ContentType)
	if scene == "" || contentType == "" || strings.TrimSpace(req.PostID) == "" {
		return StoragePresignResponse{}, errorsx.New(errorsx.CodeBadRequest, "scene/postId/contentType 不能为空")
	}

	postID, err := strconv.ParseUint(strings.TrimSpace(req.PostID), 10, 64)
	if err != nil {
		return StoragePresignResponse{}, errorsx.New(errorsx.CodeBadRequest, "postId 非法")
	}

	if s.knowposts != nil {
		owned, err := s.knowposts.IsOwnedBy(ctx, postID, userID)
		if err != nil {
			return StoragePresignResponse{}, err
		}
		if !owned {
			return StoragePresignResponse{}, errorsx.New(errorsx.CodeBadRequest, "草稿不存在或无权限")
		}
	}

	ext, err := resolveStorageExt(scene, contentType, req.Ext)
	if err != nil {
		return StoragePresignResponse{}, err
	}

	objectKey, err := buildObjectKey(scene, postID, ext)
	if err != nil {
		return StoragePresignResponse{}, err
	}

	putURL, err := s.storage.GeneratePresignedPutURL(ctx, objectKey, contentType, s.expiresIn)
	if err != nil {
		return StoragePresignResponse{}, err
	}

	return StoragePresignResponse{
		ObjectKey: objectKey,
		PutURL:    putURL,
		Headers: map[string]string{
			"Content-Type": contentType,
		},
		ExpiresIn: s.expiresIn,
	}, nil
}

func buildObjectKey(scene string, postID uint64, ext string) (string, error) {
	switch scene {
	case "knowpost_content":
		return fmt.Sprintf("posts/%d/content%s", postID, ext), nil
	case "knowpost_image":
		date := time.Now().UTC().Format("20060102")
		rand8, err := randomHex(4)
		if err != nil {
			return "", fmt.Errorf("generate object key suffix: %w", err)
		}
		return fmt.Sprintf("posts/%d/images/%s/%s%s", postID, date, rand8, ext), nil
	default:
		return "", errorsx.New(errorsx.CodeBadRequest, "不支持的上传场景")
	}
}

func resolveStorageExt(scene string, contentType string, ext string) (string, error) {
	sceneRules, ok := storageContentRules[scene]
	if !ok {
		return "", errorsx.New(errorsx.CodeBadRequest, "不支持的上传场景")
	}

	rule, ok := sceneRules[contentType]
	if !ok {
		return "", errorsx.New(errorsx.CodeBadRequest, "contentType 非法或不支持")
	}

	normalizedExt := normalizeStorageExt(ext)
	if normalizedExt == "" {
		return rule.defaultExt, nil
	}

	if _, ok := rule.allowedExt[normalizedExt]; !ok {
		return "", errorsx.New(errorsx.CodeBadRequest, "ext 与 contentType 不匹配")
	}
	return normalizedExt, nil
}

func normalizeStorageExt(ext string) string {
	trimmed := strings.ToLower(strings.TrimSpace(ext))
	if trimmed == "" {
		return ""
	}
	if strings.HasPrefix(trimmed, ".") {
		return trimmed
	}
	return "." + trimmed
}

func normalizeContentType(raw string) string {
	trimmed := strings.ToLower(strings.TrimSpace(raw))
	if trimmed == "" {
		return ""
	}
	if idx := strings.Index(trimmed, ";"); idx >= 0 {
		trimmed = strings.TrimSpace(trimmed[:idx])
	}
	return trimmed
}

func randomHex(byteLen int) (string, error) {
	buf := make([]byte, byteLen)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
