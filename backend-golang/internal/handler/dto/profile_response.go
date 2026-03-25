package dto

// ProfileResponse 表示资料接口响应。
type ProfileResponse struct {
	ID       int64   `json:"id"`
	Nickname string  `json:"nickname"`
	Avatar   string  `json:"avatar"`
	Bio      *string `json:"bio,omitempty"`
	ZgID     *string `json:"zgId,omitempty"`
	Gender   *string `json:"gender,omitempty"`
	Birthday *string `json:"birthday,omitempty"`
	School   *string `json:"school,omitempty"`
	Phone    *string `json:"phone,omitempty"`
	Email    *string `json:"email,omitempty"`
	TagJSON  *string `json:"tagJson,omitempty"`
}
