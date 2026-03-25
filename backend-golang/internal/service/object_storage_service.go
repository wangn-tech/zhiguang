package service

import (
	"context"
	"fmt"
	"mime/multipart"
	"path/filepath"
	"strings"
	"time"
	"zhiguang/internal/config"
	"zhiguang/pkg/errorsx"

	"github.com/aliyun/aliyun-oss-go-sdk/oss"
)

// ObjectStorageService 提供对象存储上传与预签名能力。
type ObjectStorageService interface {
	UploadAvatar(ctx context.Context, userID uint64, file *multipart.FileHeader) (string, error)
	GeneratePresignedPutURL(ctx context.Context, objectKey string, contentType string, expiresInSeconds int) (string, error)
}

type objectStorageService struct {
	cfg config.OSSConfig
}

// NewObjectStorageService 创建对象存储服务。
func NewObjectStorageService(cfg config.OSSConfig) ObjectStorageService {
	return &objectStorageService{cfg: cfg}
}

// UploadAvatar 上传头像并返回可访问地址。
func (s *objectStorageService) UploadAvatar(_ context.Context, userID uint64, file *multipart.FileHeader) (string, error) {
	if file == nil {
		return "", errorsx.New(errorsx.CodeBadRequest, "头像文件不能为空")
	}
	if err := s.ensureConfigured(); err != nil {
		return "", err
	}

	ext := filepath.Ext(file.Filename)
	objectKey := fmt.Sprintf("%s/%d-%d%s", strings.Trim(s.cfg.Folder, "/"), userID, time.Now().UnixMilli(), ext)

	client, err := oss.New(s.cfg.Endpoint, s.cfg.AccessKeyID, s.cfg.AccessKeySecret)
	if err != nil {
		return "", fmt.Errorf("create oss client: %w", err)
	}
	bucket, err := client.Bucket(s.cfg.Bucket)
	if err != nil {
		return "", fmt.Errorf("open oss bucket: %w", err)
	}

	src, err := file.Open()
	if err != nil {
		return "", errorsx.New(errorsx.CodeBadRequest, "头像文件读取失败")
	}
	defer src.Close()

	contentType := file.Header.Get("Content-Type")
	options := make([]oss.Option, 0, 1)
	if strings.TrimSpace(contentType) != "" {
		options = append(options, oss.ContentType(contentType))
	}

	if err := bucket.PutObject(objectKey, src, options...); err != nil {
		return "", fmt.Errorf("upload avatar to oss: %w", err)
	}

	return s.publicURL(objectKey), nil
}

// GeneratePresignedPutURL 生成直传用的 PUT 预签名链接。
func (s *objectStorageService) GeneratePresignedPutURL(_ context.Context, objectKey string, contentType string, expiresInSeconds int) (string, error) {
	if err := s.ensureConfigured(); err != nil {
		return "", err
	}
	if strings.TrimSpace(objectKey) == "" {
		return "", errorsx.New(errorsx.CodeBadRequest, "objectKey 不能为空")
	}

	client, err := oss.New(s.cfg.Endpoint, s.cfg.AccessKeyID, s.cfg.AccessKeySecret)
	if err != nil {
		return "", fmt.Errorf("create oss client: %w", err)
	}
	bucket, err := client.Bucket(s.cfg.Bucket)
	if err != nil {
		return "", fmt.Errorf("open oss bucket: %w", err)
	}

	if expiresInSeconds <= 0 {
		expiresInSeconds = 600
	}

	options := make([]oss.Option, 0, 1)
	if strings.TrimSpace(contentType) != "" {
		options = append(options, oss.ContentType(contentType))
	}

	url, err := bucket.SignURL(objectKey, oss.HTTPPut, int64(expiresInSeconds), options...)
	if err != nil {
		return "", fmt.Errorf("generate presigned put url: %w", err)
	}
	return url, nil
}

func (s *objectStorageService) ensureConfigured() error {
	if strings.TrimSpace(s.cfg.Endpoint) == "" ||
		strings.TrimSpace(s.cfg.AccessKeyID) == "" ||
		strings.TrimSpace(s.cfg.AccessKeySecret) == "" ||
		strings.TrimSpace(s.cfg.Bucket) == "" {
		return errorsx.New(errorsx.CodeBadRequest, "对象存储未配置")
	}
	return nil
}

func (s *objectStorageService) publicURL(objectKey string) string {
	trimmedKey := strings.TrimPrefix(objectKey, "/")
	if strings.TrimSpace(s.cfg.PublicDomain) != "" {
		return strings.TrimRight(s.cfg.PublicDomain, "/") + "/" + trimmedKey
	}
	endpoint := strings.TrimPrefix(strings.TrimPrefix(s.cfg.Endpoint, "https://"), "http://")
	return "https://" + s.cfg.Bucket + "." + endpoint + "/" + trimmedKey
}
