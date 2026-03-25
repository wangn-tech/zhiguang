package dto

// KnowPostContentConfirmRequest 表示正文上传确认请求。
type KnowPostContentConfirmRequest struct {
	ObjectKey string `json:"objectKey" binding:"required"`
	ETag      string `json:"etag" binding:"required"`
	Size      int64  `json:"size" binding:"required,gt=0"`
	SHA256    string `json:"sha256" binding:"required"`
}
