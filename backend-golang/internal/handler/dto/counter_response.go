package dto

// ActionLikeResponse 表示点赞类行为响应。
type ActionLikeResponse struct {
	Changed bool `json:"changed"`
	Liked   bool `json:"liked"`
}

// ActionFavResponse 表示收藏类行为响应。
type ActionFavResponse struct {
	Changed bool `json:"changed"`
	Faved   bool `json:"faved"`
}

// CounterCountsResponse 表示计数查询响应。
type CounterCountsResponse struct {
	EntityType string           `json:"entityType"`
	EntityID   string           `json:"entityId"`
	Counts     map[string]int64 `json:"counts"`
}
