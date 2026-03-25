package dto

// SendCodeRequest 是发送验证码请求。
type SendCodeRequest struct {
	Scene          string `json:"scene" binding:"required,oneof=REGISTER LOGIN RESET_PASSWORD"`
	IdentifierType string `json:"identifierType" binding:"required,oneof=PHONE EMAIL"`
	Identifier     string `json:"identifier" binding:"required"`
}

// RegisterRequest 是注册请求。
type RegisterRequest struct {
	IdentifierType string `json:"identifierType" binding:"required,oneof=PHONE EMAIL"`
	Identifier     string `json:"identifier" binding:"required"`
	Code           string `json:"code" binding:"required"`
	Password       string `json:"password"`
	AgreeTerms     bool   `json:"agreeTerms"`
}

// LoginRequest 是登录请求。
type LoginRequest struct {
	IdentifierType string `json:"identifierType" binding:"required,oneof=PHONE EMAIL"`
	Identifier     string `json:"identifier" binding:"required"`
	Code           string `json:"code"`
	Password       string `json:"password"`
}

// TokenRefreshRequest 是刷新令牌请求。
type TokenRefreshRequest struct {
	RefreshToken string `json:"refreshToken" binding:"required"`
}

// LogoutRequest 是登出请求。
type LogoutRequest struct {
	RefreshToken string `json:"refreshToken" binding:"required"`
}

// PasswordResetRequest 是密码重置请求。
type PasswordResetRequest struct {
	IdentifierType string `json:"identifierType" binding:"required,oneof=PHONE EMAIL"`
	Identifier     string `json:"identifier" binding:"required"`
	Code           string `json:"code" binding:"required"`
	NewPassword    string `json:"newPassword" binding:"required"`
}
