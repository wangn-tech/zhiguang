package dto

// ActionRequest 表示点赞/收藏行为请求。
type ActionRequest struct {
	EntityType string `json:"entityType" binding:"required"`
	EntityID   string `json:"entityId" binding:"required"`
}
