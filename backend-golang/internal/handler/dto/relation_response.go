package dto

// RelationStatusResponse 表示关系三态响应。
type RelationStatusResponse struct {
	Following  bool `json:"following"`
	FollowedBy bool `json:"followedBy"`
	Mutual     bool `json:"mutual"`
}

// RelationCountersResponse 表示关系计数响应。
type RelationCountersResponse struct {
	Followings int64 `json:"followings"`
	Followers  int64 `json:"followers"`
	Posts      int64 `json:"posts"`
	LikedPosts int64 `json:"likedPosts"`
	FavedPosts int64 `json:"favedPosts"`
}
