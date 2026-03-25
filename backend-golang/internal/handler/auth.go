package handler

import (
	"net/http"
	"strings"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

// AuthHandler 处理认证域相关接口。
type AuthHandler struct {
	service service.AuthService
}

// NewAuthHandler 创建认证处理器。
func NewAuthHandler(service service.AuthService) *AuthHandler {
	return &AuthHandler{service: service}
}

// SendCode 发送验证码。
func (h *AuthHandler) SendCode(c *gin.Context) {
	var req dto.SendCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.service.SendCode(c.Request.Context(), service.SendCodeRequest{
		Scene:          req.Scene,
		IdentifierType: req.IdentifierType,
		Identifier:     req.Identifier,
	})
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, dto.SendCodeResponse{
		Identifier:    resp.Identifier,
		Scene:         resp.Scene,
		ExpireSeconds: resp.ExpireSeconds,
	})
}

// Register 处理账号注册。
func (h *AuthHandler) Register(c *gin.Context) {
	var req dto.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.service.Register(c.Request.Context(), service.RegisterRequest{
		IdentifierType: req.IdentifierType,
		Identifier:     req.Identifier,
		Code:           req.Code,
		Password:       req.Password,
		AgreeTerms:     req.AgreeTerms,
		ClientIP:       c.ClientIP(),
		UserAgent:      c.Request.UserAgent(),
	})
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, mapAuthResponse(resp))
}

// Login 处理账号登录。
func (h *AuthHandler) Login(c *gin.Context) {
	var req dto.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.service.Login(c.Request.Context(), service.LoginRequest{
		IdentifierType: req.IdentifierType,
		Identifier:     req.Identifier,
		Code:           req.Code,
		Password:       req.Password,
		ClientIP:       c.ClientIP(),
		UserAgent:      c.Request.UserAgent(),
	})
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, mapAuthResponse(resp))
}

// Me 查询当前登录用户。
func (h *AuthHandler) Me(c *gin.Context) {
	token := parseBearerToken(c.GetHeader("Authorization"))
	if token == "" {
		c.Error(errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized))
		return
	}

	resp, err := h.service.CurrentUser(c.Request.Context(), token)
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, mapAuthUser(resp))
}

// Refresh 刷新令牌。
func (h *AuthHandler) Refresh(c *gin.Context) {
	var req dto.TokenRefreshRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.service.Refresh(c.Request.Context(), service.TokenRefreshRequest{RefreshToken: req.RefreshToken})
	if err != nil {
		c.Error(err)
		return
	}
	c.JSON(http.StatusOK, mapToken(resp))
}

// Logout 撤销 refresh token。
func (h *AuthHandler) Logout(c *gin.Context) {
	var req dto.LogoutRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	if err := h.service.Logout(c.Request.Context(), service.LogoutRequest{RefreshToken: req.RefreshToken}); err != nil {
		c.Error(err)
		return
	}
	c.Status(http.StatusNoContent)
}

// ResetPassword 重置密码。
func (h *AuthHandler) ResetPassword(c *gin.Context) {
	var req dto.PasswordResetRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	if err := h.service.ResetPassword(c.Request.Context(), service.PasswordResetRequest{
		IdentifierType: req.IdentifierType,
		Identifier:     req.Identifier,
		Code:           req.Code,
		NewPassword:    req.NewPassword,
	}); err != nil {
		c.Error(err)
		return
	}
	c.Status(http.StatusNoContent)
}

func parseBearerToken(raw string) string {
	parts := strings.SplitN(strings.TrimSpace(raw), " ", 2)
	if len(parts) != 2 {
		return ""
	}
	if !strings.EqualFold(parts[0], "Bearer") {
		return ""
	}
	return strings.TrimSpace(parts[1])
}

func mapAuthResponse(resp service.AuthResponse) dto.AuthResponse {
	return dto.AuthResponse{
		User:  mapAuthUser(resp.User),
		Token: mapToken(resp.Token),
	}
}

func mapAuthUser(user service.AuthUserResponse) dto.AuthUserResponse {
	return dto.AuthUserResponse{
		ID:       int64(user.ID),
		Nickname: user.Nickname,
		Avatar:   user.Avatar,
		Phone:    user.Phone,
		ZhID:     user.ZhID,
		Birthday: user.Birthday,
		School:   user.School,
		Bio:      user.Bio,
		Gender:   user.Gender,
		TagJSON:  user.TagJSON,
	}
}

func mapToken(token service.TokenResponse) dto.TokenResponse {
	return dto.TokenResponse{
		AccessToken:           token.AccessToken,
		AccessTokenExpiresAt:  token.AccessTokenExpiresAt,
		RefreshToken:          token.RefreshToken,
		RefreshTokenExpiresAt: token.RefreshTokenExpiresAt,
	}
}
