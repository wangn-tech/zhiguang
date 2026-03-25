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

// Presign 校验场景与草稿归属后生成上传地址。
func (s *storagePresignService) Presign(ctx context.Context, userID uint64, req StoragePresignRequest) (StoragePresignResponse, error) {
	scene := strings.TrimSpace(req.Scene)
	contentType := strings.TrimSpace(req.ContentType)
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

	ext := normalizeStorageExt(strings.TrimSpace(req.Ext), contentType, scene)
	if ext == "" {
		return StoragePresignResponse{}, errorsx.New(errorsx.CodeBadRequest, "不支持的上传场景")
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

func normalizeStorageExt(ext string, contentType string, scene string) string {
	if ext != "" {
		if strings.HasPrefix(ext, ".") {
			return ext
		}
		return "." + ext
	}

	switch scene {
	case "knowpost_content":
		switch contentType {
		case "text/markdown":
			return ".md"
		case "text/html":
			return ".html"
		case "text/plain":
			return ".txt"
		case "application/json":
			return ".json"
		default:
			return ".bin"
		}
	case "knowpost_image":
		switch contentType {
		case "image/jpeg":
			return ".jpg"
		case "image/png":
			return ".png"
		case "image/webp":
			return ".webp"
		case "image/svg+xml":
			return ".svg"
		default:
			return ".img"
		}
	default:
		return ""
	}
}

func randomHex(byteLen int) (string, error) {
	buf := make([]byte, byteLen)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
