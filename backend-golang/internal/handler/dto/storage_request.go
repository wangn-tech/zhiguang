package dto

// StoragePresignRequest 表示预签名请求参数。
type StoragePresignRequest struct {
	Scene       string `json:"scene" binding:"required"`
	PostID      string `json:"postId" binding:"required"`
	ContentType string `json:"contentType" binding:"required"`
	Ext         string `json:"ext"`
}

// StoragePresignResponse 表示预签名响应参数。
type StoragePresignResponse struct {
	ObjectKey string            `json:"objectKey"`
	PutURL    string            `json:"putUrl"`
	Headers   map[string]string `json:"headers"`
	ExpiresIn int               `json:"expiresIn"`
}
