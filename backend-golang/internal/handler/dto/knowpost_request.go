package dto

// KnowPostContentConfirmRequest 表示正文上传确认请求。
type KnowPostContentConfirmRequest struct {
	ObjectKey string `json:"objectKey" binding:"required"`
	ETag      string `json:"etag" binding:"required"`
	Size      int64  `json:"size" binding:"required,gt=0"`
	SHA256    string `json:"sha256" binding:"required"`
}

// KnowPostPatchRequest 表示知文元数据更新请求。
type KnowPostPatchRequest struct {
	Title       *string   `json:"title"`
	TagID       *int64    `json:"tagId"`
	Tags        *[]string `json:"tags"`
	ImageURLs   *[]string `json:"imgUrls"`
	Visible     *string   `json:"visible"`
	IsTop       *bool     `json:"isTop"`
	Description *string   `json:"description"`
}

// KnowPostTopPatchRequest 表示置顶状态更新请求。
type KnowPostTopPatchRequest struct {
	IsTop *bool `json:"isTop" binding:"required"`
}

// KnowPostVisibilityPatchRequest 表示可见性更新请求。
type KnowPostVisibilityPatchRequest struct {
	Visible *string `json:"visible" binding:"required"`
}

// KnowPostDescriptionSuggestRequest 表示摘要建议请求。
type KnowPostDescriptionSuggestRequest struct {
	Content string `json:"content" binding:"required"`
}
