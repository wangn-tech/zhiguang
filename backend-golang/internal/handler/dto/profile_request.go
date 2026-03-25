package dto

// ProfilePatchRequest 表示资料更新请求体。
type ProfilePatchRequest struct {
	Nickname *string `json:"nickname"`
	Bio      *string `json:"bio"`
	ZgID     *string `json:"zgId"`
	Gender   *string `json:"gender"`
	Birthday *string `json:"birthday"`
	School   *string `json:"school"`
	Email    *string `json:"email"`
	Phone    *string `json:"phone"`
	TagJSON  *string `json:"tagJson"`
}
