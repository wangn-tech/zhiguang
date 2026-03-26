package service

import (
	"context"
	"mime/multipart"
	"regexp"
	"strings"
	"testing"
)

type fakeStoragePresignObjectStorage struct {
	putURL          string
	err             error
	lastObjectKey   string
	lastContentType string
	lastExpiresIn   int
}

func (f *fakeStoragePresignObjectStorage) UploadAvatar(_ context.Context, _ uint64, _ *multipart.FileHeader) (string, error) {
	return "", nil
}

func (f *fakeStoragePresignObjectStorage) GeneratePresignedPutURL(_ context.Context, objectKey string, contentType string, expiresInSeconds int) (string, error) {
	if f.err != nil {
		return "", f.err
	}
	f.lastObjectKey = objectKey
	f.lastContentType = contentType
	f.lastExpiresIn = expiresInSeconds
	if f.putURL == "" {
		return "https://oss.example.com/upload", nil
	}
	return f.putURL, nil
}

type fakeStorageKnowPostOwnershipChecker struct {
	owned bool
	err   error
}

func (f *fakeStorageKnowPostOwnershipChecker) IsOwnedBy(_ context.Context, _ uint64, _ uint64) (bool, error) {
	if f.err != nil {
		return false, f.err
	}
	return f.owned, nil
}

func TestStoragePresignService_PresignContent_Success(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{putURL: "https://oss.example.com/upload"}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	resp, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_content",
		PostID:      "123",
		ContentType: "text/markdown; charset=utf-8",
		Ext:         "",
	})
	if err != nil {
		t.Fatalf("Presign() error = %v", err)
	}

	if resp.ObjectKey != "posts/123/content.md" {
		t.Fatalf("objectKey = %s, want posts/123/content.md", resp.ObjectKey)
	}
	if storage.lastContentType != "text/markdown" {
		t.Fatalf("contentType = %s, want text/markdown", storage.lastContentType)
	}
	if storage.lastExpiresIn != 600 {
		t.Fatalf("expiresIn = %d, want 600", storage.lastExpiresIn)
	}
	if got := resp.Headers["Content-Type"]; got != "text/markdown" {
		t.Fatalf("header content-type = %s, want text/markdown", got)
	}
}

func TestStoragePresignService_PresignImage_Success(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	resp, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_image",
		PostID:      "123",
		ContentType: "image/png",
		Ext:         "PNG",
	})
	if err != nil {
		t.Fatalf("Presign() error = %v", err)
	}

	matched, _ := regexp.MatchString(`^posts/123/images/\d{8}/[0-9a-f]{8}\.png$`, resp.ObjectKey)
	if !matched {
		t.Fatalf("objectKey = %s, does not match image key format", resp.ObjectKey)
	}
}

func TestStoragePresignService_Presign_InvalidScene(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	_, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "avatar",
		PostID:      "123",
		ContentType: "image/png",
		Ext:         ".png",
	})
	if err == nil || !strings.Contains(err.Error(), "不支持的上传场景") {
		t.Fatalf("err = %v, want scene not supported", err)
	}
}

func TestStoragePresignService_Presign_InvalidContentType(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	_, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_content",
		PostID:      "123",
		ContentType: "application/pdf",
		Ext:         ".pdf",
	})
	if err == nil || !strings.Contains(err.Error(), "contentType 非法或不支持") {
		t.Fatalf("err = %v, want contentType invalid", err)
	}
}

func TestStoragePresignService_Presign_ExtMismatch(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	_, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_image",
		PostID:      "123",
		ContentType: "image/png",
		Ext:         ".jpg",
	})
	if err == nil || !strings.Contains(err.Error(), "ext 与 contentType 不匹配") {
		t.Fatalf("err = %v, want ext mismatch", err)
	}
}

func TestStoragePresignService_Presign_NotOwned(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: false}
	svc := NewStoragePresignService(storage, checker, 600)

	_, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_content",
		PostID:      "123",
		ContentType: "text/markdown",
		Ext:         ".md",
	})
	if err == nil || !strings.Contains(err.Error(), "草稿不存在或无权限") {
		t.Fatalf("err = %v, want ownership error", err)
	}
}

func TestStoragePresignService_Presign_InvalidPostID(t *testing.T) {
	storage := &fakeStoragePresignObjectStorage{}
	checker := &fakeStorageKnowPostOwnershipChecker{owned: true}
	svc := NewStoragePresignService(storage, checker, 600)

	_, err := svc.Presign(context.Background(), 1001, StoragePresignRequest{
		Scene:       "knowpost_content",
		PostID:      "abc",
		ContentType: "text/markdown",
		Ext:         ".md",
	})
	if err == nil || !strings.Contains(err.Error(), "postId 非法") {
		t.Fatalf("err = %v, want invalid postId", err)
	}
}
