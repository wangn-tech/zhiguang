package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
	"zhiguang/internal/middleware"
	"zhiguang/internal/service"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

func TestAuthFlow_RegisterLoginAndMe(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewAuthHandler(&fakeAuthService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	mountAuthRoutes(r, h)

	sendCodeBody := map[string]any{
		"scene":          "REGISTER",
		"identifierType": "PHONE",
		"identifier":     "13800000000",
	}
	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/send-code", sendCodeBody, "")
	if w.Code != http.StatusOK {
		t.Fatalf("send-code status = %d, want 200", w.Code)
	}

	registerBody := map[string]any{
		"identifierType": "PHONE",
		"identifier":     "13800000000",
		"code":           "123456",
		"password":       "Pass1234",
		"agreeTerms":     true,
	}
	w = performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/register", registerBody, "")
	if w.Code != http.StatusOK {
		t.Fatalf("register status = %d, want 200", w.Code)
	}
	var regResp map[string]any
	mustDecodeJSON(t, w.Body.Bytes(), &regResp)

	user, ok := regResp["user"].(map[string]any)
	if !ok {
		t.Fatalf("register response user should be object")
	}
	token, ok := regResp["token"].(map[string]any)
	if !ok {
		t.Fatalf("register response token should be object")
	}
	if user["phone"] != "13800000000" {
		t.Fatalf("user.phone = %v, want 13800000000", user["phone"])
	}
	accessToken, ok := token["accessToken"].(string)
	if !ok || accessToken == "" {
		t.Fatalf("token.accessToken should not be empty")
	}

	loginBody := map[string]any{
		"identifierType": "PHONE",
		"identifier":     "13800000000",
		"password":       "Pass1234",
	}
	w = performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/login", loginBody, "")
	if w.Code != http.StatusOK {
		t.Fatalf("login status = %d, want 200", w.Code)
	}
	var loginResp map[string]any
	mustDecodeJSON(t, w.Body.Bytes(), &loginResp)
	loginToken := loginResp["token"].(map[string]any)["accessToken"].(string)
	if loginToken == "" {
		t.Fatalf("login accessToken should not be empty")
	}

	w = performJSONRequest(t, r, http.MethodGet, "/api/v1/auth/me", nil, loginToken)
	if w.Code != http.StatusOK {
		t.Fatalf("me status = %d, want 200", w.Code)
	}
	var meResp map[string]any
	mustDecodeJSON(t, w.Body.Bytes(), &meResp)
	if meResp["phone"] != "13800000000" {
		t.Fatalf("me.phone = %v, want 13800000000", meResp["phone"])
	}

	refreshToken, ok := loginResp["token"].(map[string]any)["refreshToken"].(string)
	if !ok || refreshToken == "" {
		t.Fatalf("login refreshToken should not be empty")
	}

	w = performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/token/refresh", map[string]any{
		"refreshToken": refreshToken,
	}, "")
	if w.Code != http.StatusOK {
		t.Fatalf("refresh status = %d, want 200", w.Code)
	}

	w = performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/logout", map[string]any{
		"refreshToken": refreshToken,
	}, loginToken)
	if w.Code != http.StatusNoContent {
		t.Fatalf("logout status = %d, want 204", w.Code)
	}

	w = performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/password/reset", map[string]any{
		"identifierType": "PHONE",
		"identifier":     "13800000000",
		"code":           "123456",
		"newPassword":    "Pass5678",
	}, "")
	if w.Code != http.StatusNoContent {
		t.Fatalf("password reset status = %d, want 204", w.Code)
	}
}

func TestAuthRegister_TermsNotAccepted(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewAuthHandler(&fakeAuthService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	mountAuthRoutes(r, h)

	registerBody := map[string]any{
		"identifierType": "PHONE",
		"identifier":     "13900000000",
		"code":           "123456",
		"password":       "Pass1234",
		"agreeTerms":     false,
	}
	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/register", registerBody, "")
	if w.Code != http.StatusBadRequest {
		t.Fatalf("register status = %d, want 400", w.Code)
	}
	assertErrorCode(t, w.Body.Bytes(), "TERMS_NOT_ACCEPTED")
}

func TestAuthLogin_InvalidPassword(t *testing.T) {
	gin.SetMode(gin.TestMode)

	h := NewAuthHandler(&fakeAuthService{})

	r := gin.New()
	r.Use(middleware.ErrorHandler())
	mountAuthRoutes(r, h)

	w := performJSONRequest(t, r, http.MethodPost, "/api/v1/auth/login", map[string]any{
		"identifierType": "PHONE",
		"identifier":     "13700000000",
		"password":       "badpass",
	}, "")
	if w.Code != http.StatusBadRequest {
		t.Fatalf("login status = %d, want 400", w.Code)
	}
	assertErrorCode(t, w.Body.Bytes(), "INVALID_CREDENTIALS")
}

func performJSONRequest(t *testing.T, r *gin.Engine, method, path string, body any, accessToken string) *httptest.ResponseRecorder {
	t.Helper()

	var payload []byte
	var err error
	if body != nil {
		payload, err = json.Marshal(body)
		if err != nil {
			t.Fatalf("json marshal body: %v", err)
		}
	}

	req := httptest.NewRequest(method, path, bytes.NewReader(payload))
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if strings.TrimSpace(accessToken) != "" {
		req.Header.Set("Authorization", "Bearer "+accessToken)
	}

	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	return w
}

func mountAuthRoutes(r gin.IRouter, h *AuthHandler) {
	g := r.Group("/api/v1/auth")
	g.POST("/send-code", h.SendCode)
	g.POST("/register", h.Register)
	g.POST("/login", h.Login)
	g.POST("/token/refresh", h.Refresh)
	g.POST("/logout", h.Logout)
	g.POST("/password/reset", h.ResetPassword)
	g.GET("/me", h.Me)
}

func mustDecodeJSON(t *testing.T, raw []byte, v any) {
	t.Helper()
	if err := json.Unmarshal(raw, v); err != nil {
		t.Fatalf("json unmarshal: %v", err)
	}
}

func assertErrorCode(t *testing.T, raw []byte, want string) {
	t.Helper()

	var body map[string]any
	mustDecodeJSON(t, raw, &body)

	got, _ := body["code"].(string)
	if got != want {
		t.Fatalf("code = %s, want %s", got, want)
	}
}

type fakeAuthService struct{}

func (s *fakeAuthService) SendCode(_ context.Context, req service.SendCodeRequest) (service.SendCodeResponse, error) {
	return service.SendCodeResponse{
		Identifier:    req.Identifier,
		Scene:         req.Scene,
		ExpireSeconds: 300,
	}, nil
}

func (s *fakeAuthService) Register(_ context.Context, req service.RegisterRequest) (service.AuthResponse, error) {
	if !req.AgreeTerms {
		return service.AuthResponse{}, errorsx.New(errorsx.CodeTermsNotAccepted, "请先同意服务条款")
	}
	return fakeAuthResponse(req.Identifier), nil
}

func (s *fakeAuthService) Login(_ context.Context, req service.LoginRequest) (service.AuthResponse, error) {
	if req.Password == "badpass" {
		return service.AuthResponse{}, errorsx.New(errorsx.CodeInvalidCredentials, "登录凭证错误")
	}
	return fakeAuthResponse(req.Identifier), nil
}

func (s *fakeAuthService) CurrentUser(_ context.Context, accessToken string) (service.AuthUserResponse, error) {
	if strings.TrimSpace(accessToken) == "" {
		return service.AuthUserResponse{}, errorsx.New(errorsx.CodeInvalidCredentials, "未登录")
	}
	return service.AuthUserResponse{ID: 1, Nickname: "测试用户", Phone: "13800000000"}, nil
}

func (s *fakeAuthService) Refresh(_ context.Context, req service.TokenRefreshRequest) (service.TokenResponse, error) {
	if strings.TrimSpace(req.RefreshToken) == "" {
		return service.TokenResponse{}, errorsx.New(errorsx.CodeRefreshTokenInvalid, "刷新令牌无效")
	}
	now := time.Now()
	return service.TokenResponse{
		AccessToken:           "access-token-new",
		AccessTokenExpiresAt:  now.Add(15 * time.Minute),
		RefreshToken:          "refresh-token-new",
		RefreshTokenExpiresAt: now.Add(7 * 24 * time.Hour),
	}, nil
}

func (s *fakeAuthService) Logout(_ context.Context, _ service.LogoutRequest) error {
	return nil
}

func (s *fakeAuthService) ResetPassword(_ context.Context, _ service.PasswordResetRequest) error {
	return nil
}

func fakeAuthResponse(identifier string) service.AuthResponse {
	now := time.Now()
	return service.AuthResponse{
		User: service.AuthUserResponse{
			ID:       1,
			Nickname: "测试用户",
			Phone:    identifier,
		},
		Token: service.TokenResponse{
			AccessToken:           "access-token",
			AccessTokenExpiresAt:  now.Add(15 * time.Minute),
			RefreshToken:          "refresh-token",
			RefreshTokenExpiresAt: now.Add(7 * 24 * time.Hour),
		},
	}
}
