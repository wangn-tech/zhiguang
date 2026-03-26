package dto

import "time"

// KnowPostDraftCreateResponse 表示创建草稿接口响应。
type KnowPostDraftCreateResponse struct {
	ID string `json:"id"`
}

// KnowPostFeedItemResponse 表示 feed 列表中的单条知文。
type KnowPostFeedItemResponse struct {
	ID             string   `json:"id"`
	Title          string   `json:"title"`
	Description    string   `json:"description"`
	CoverImage     string   `json:"coverImage,omitempty"`
	Tags           []string `json:"tags"`
	TagJSON        string   `json:"tagJson,omitempty"`
	AuthorAvatar   string   `json:"authorAvatar,omitempty"`
	AuthorNickname string   `json:"authorNickname"`
	LikeCount      int64    `json:"likeCount"`
	FavoriteCount  int64    `json:"favoriteCount"`
	Liked          bool     `json:"liked"`
	Faved          bool     `json:"faved"`
	IsTop          bool     `json:"isTop"`
	Visible        string   `json:"visible,omitempty"`
}

// KnowPostFeedResponse 表示公开 feed 分页响应。
type KnowPostFeedResponse struct {
	Items   []KnowPostFeedItemResponse `json:"items"`
	Page    int                        `json:"page"`
	Size    int                        `json:"size"`
	HasMore bool                       `json:"hasMore"`
}

// KnowPostDetailResponse 表示知文详情响应。
type KnowPostDetailResponse struct {
	ID             string     `json:"id"`
	Title          string     `json:"title"`
	Description    string     `json:"description"`
	ContentURL     string     `json:"contentUrl"`
	Images         []string   `json:"images"`
	Tags           []string   `json:"tags"`
	AuthorAvatar   string     `json:"authorAvatar,omitempty"`
	AuthorNickname string     `json:"authorNickname"`
	AuthorID       uint64     `json:"authorId,omitempty"`
	AuthorTagJSON  string     `json:"authorTagJson,omitempty"`
	LikeCount      int64      `json:"likeCount"`
	FavoriteCount  int64      `json:"favoriteCount"`
	Liked          bool       `json:"liked"`
	Faved          bool       `json:"faved"`
	IsTop          bool       `json:"isTop"`
	Visible        string     `json:"visible"`
	Type           string     `json:"type"`
	PublishTime    *time.Time `json:"publishTime,omitempty"`
}
