package dto

import "time"

// SendCodeResponse 是发送验证码响应。
type SendCodeResponse struct {
	Identifier    string `json:"identifier"`
	Scene         string `json:"scene"`
	ExpireSeconds int    `json:"expireSeconds"`
}

// AuthUserResponse 是当前认证用户信息。
type AuthUserResponse struct {
	ID       int64   `json:"id"`
	Nickname string  `json:"nickname"`
	Avatar   string  `json:"avatar"`
	Phone    string  `json:"phone"`
	ZhID     *string `json:"zhId,omitempty"`
	Birthday *string `json:"birthday,omitempty"`
	School   *string `json:"school,omitempty"`
	Bio      *string `json:"bio,omitempty"`
	Gender   *string `json:"gender,omitempty"`
	TagJSON  *string `json:"tagJson,omitempty"`
}

// TokenResponse 是登录/刷新令牌响应。
type TokenResponse struct {
	AccessToken           string    `json:"accessToken"`
	AccessTokenExpiresAt  time.Time `json:"accessTokenExpiresAt"`
	RefreshToken          string    `json:"refreshToken"`
	RefreshTokenExpiresAt time.Time `json:"refreshTokenExpiresAt"`
}

// AuthResponse 是登录/注册响应。
type AuthResponse struct {
	User  AuthUserResponse `json:"user"`
	Token TokenResponse    `json:"token"`
}
