package service

import (
	"context"
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"
	"zhiguang/pkg/jwtx"

	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
)

const (
	defaultAccessTokenTTL   = 15 * time.Minute
	defaultRefreshTokenTTL  = 7 * 24 * time.Hour
	verificationCodeTTL     = 5 * time.Minute
	verificationCodeFixed   = "123456"
	verificationMaxAttempts = 5
	defaultTokenSecret      = "zhiguang-dev-jwt-secret"
)

var (
	phonePattern = regexp.MustCompile(`^1\d{10}$`)
	emailPattern = regexp.MustCompile(`^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$`)
)

// AuthService 定义认证业务能力。
type AuthService interface {
	SendCode(ctx context.Context, req SendCodeRequest) (SendCodeResponse, error)
	Register(ctx context.Context, req RegisterRequest) (AuthResponse, error)
	Login(ctx context.Context, req LoginRequest) (AuthResponse, error)
	CurrentUser(ctx context.Context, accessToken string) (AuthUserResponse, error)
	Refresh(ctx context.Context, req TokenRefreshRequest) (TokenResponse, error)
	Logout(ctx context.Context, req LogoutRequest) error
	ResetPassword(ctx context.Context, req PasswordResetRequest) error
}

type authService struct {
	users           *repository.UserRepository
	loginLogs       *repository.LoginLogRepository
	redis           *redis.Client
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
	tokenSecret     string
}

// AuthOptions 定义认证服务运行参数。
type AuthOptions struct {
	TokenSecret     string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
}

// NewAuthService 创建认证服务。
func NewAuthService(users *repository.UserRepository, loginLogs *repository.LoginLogRepository, redisClient *redis.Client, opts AuthOptions) AuthService {
	if opts.AccessTokenTTL <= 0 {
		opts.AccessTokenTTL = defaultAccessTokenTTL
	}
	if opts.RefreshTokenTTL <= 0 {
		opts.RefreshTokenTTL = defaultRefreshTokenTTL
	}
	if strings.TrimSpace(opts.TokenSecret) == "" {
		opts.TokenSecret = defaultTokenSecret
	}

	return &authService{
		users:           users,
		loginLogs:       loginLogs,
		redis:           redisClient,
		accessTokenTTL:  opts.AccessTokenTTL,
		refreshTokenTTL: opts.RefreshTokenTTL,
		tokenSecret:     opts.TokenSecret,
	}
}

// SendCodeRequest 是发送验证码服务请求。
type SendCodeRequest struct {
	Scene          string
	IdentifierType string
	Identifier     string
}

// SendCodeResponse 是发送验证码服务响应。
type SendCodeResponse struct {
	Identifier    string
	Scene         string
	ExpireSeconds int
}

// RegisterRequest 是注册服务请求。
type RegisterRequest struct {
	IdentifierType string
	Identifier     string
	Code           string
	Password       string
	AgreeTerms     bool
	ClientIP       string
	UserAgent      string
}

// LoginRequest 是登录服务请求。
type LoginRequest struct {
	IdentifierType string
	Identifier     string
	Code           string
	Password       string
	ClientIP       string
	UserAgent      string
}

// PasswordResetRequest 是重置密码服务请求。
type PasswordResetRequest struct {
	IdentifierType string
	Identifier     string
	Code           string
	NewPassword    string
}

// TokenRefreshRequest 是刷新令牌服务请求。
type TokenRefreshRequest struct {
	RefreshToken string
}

// LogoutRequest 是登出服务请求。
type LogoutRequest struct {
	RefreshToken string
}

// AuthUserResponse 是认证用户信息服务响应。
type AuthUserResponse struct {
	ID       uint64
	Nickname string
	Avatar   string
	Phone    string
	ZhID     *string
	Birthday *string
	School   *string
	Bio      *string
	Gender   *string
	TagJSON  *string
}

// TokenResponse 是令牌服务响应。
type TokenResponse struct {
	AccessToken           string
	AccessTokenExpiresAt  time.Time
	RefreshToken          string
	RefreshTokenExpiresAt time.Time
}

// AuthResponse 是登录/注册服务响应。
type AuthResponse struct {
	User  AuthUserResponse
	Token TokenResponse
}

func (s *authService) SendCode(ctx context.Context, req SendCodeRequest) (SendCodeResponse, error) {
	identifier, err := normalizeIdentifier(req.IdentifierType, req.Identifier)
	if err != nil {
		return SendCodeResponse{}, err
	}

	exists, err := s.identifierExists(ctx, req.IdentifierType, identifier)
	if err != nil {
		return SendCodeResponse{}, err
	}

	scene := strings.ToUpper(strings.TrimSpace(req.Scene))
	if scene == "REGISTER" && exists {
		return SendCodeResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "账号已存在")
	}
	if (scene == "LOGIN" || scene == "RESET_PASSWORD") && !exists {
		return SendCodeResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
	}

	if err := s.redis.Set(ctx, verificationCodeKey(scene, req.IdentifierType, identifier), verificationCodeFixed, verificationCodeTTL).Err(); err != nil {
		return SendCodeResponse{}, fmt.Errorf("set verification code: %w", err)
	}
	if err := s.redis.Del(ctx, verificationAttemptsKey(scene, req.IdentifierType, identifier)).Err(); err != nil {
		return SendCodeResponse{}, fmt.Errorf("reset verification attempts: %w", err)
	}

	return SendCodeResponse{
		Identifier:    identifier,
		Scene:         scene,
		ExpireSeconds: int(verificationCodeTTL.Seconds()),
	}, nil
}

func (s *authService) Register(ctx context.Context, req RegisterRequest) (AuthResponse, error) {
	if !req.AgreeTerms {
		return AuthResponse{}, errorsx.New(errorsx.CodeTermsNotAccepted, "请先同意服务条款")
	}

	identifier, err := normalizeIdentifier(req.IdentifierType, req.Identifier)
	if err != nil {
		return AuthResponse{}, err
	}

	exists, err := s.identifierExists(ctx, req.IdentifierType, identifier)
	if err != nil {
		return AuthResponse{}, err
	}
	if exists {
		return AuthResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "账号已存在")
	}

	if err := s.verifyCode(ctx, "REGISTER", req.IdentifierType, identifier, req.Code); err != nil {
		return AuthResponse{}, err
	}

	passwordHash := ""
	if strings.TrimSpace(req.Password) != "" {
		if err := validatePassword(req.Password); err != nil {
			return AuthResponse{}, err
		}
		hash, err := hashPassword(req.Password)
		if err != nil {
			return AuthResponse{}, fmt.Errorf("hash password: %w", err)
		}
		passwordHash = hash
	}

	user := &model.User{
		PasswordHash: passwordHash,
		Nickname:     generateNickname(),
		TagsJSON:     stringPtr("[]"),
	}
	if strings.EqualFold(req.IdentifierType, "PHONE") {
		user.Phone = &identifier
	}
	if strings.EqualFold(req.IdentifierType, "EMAIL") {
		user.Email = &identifier
	}

	if err := s.users.Create(ctx, user); err != nil {
		if isDuplicateKey(err) {
			return AuthResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "账号已存在")
		}
		return AuthResponse{}, err
	}

	pair, err := jwtx.IssueTokenPair(user.ID, s.accessTokenTTL, s.refreshTokenTTL, s.tokenSecret)
	if err != nil {
		return AuthResponse{}, fmt.Errorf("issue token pair: %w", err)
	}
	if err := s.storeRefreshToken(ctx, user.ID, pair.RefreshTokenID, pair.RefreshTokenExpiresAt); err != nil {
		return AuthResponse{}, err
	}

	s.recordLogin(ctx, user.ID, identifier, "REGISTER", req.ClientIP, req.UserAgent, "SUCCESS")
	return mapAuthResponse(user, pair), nil
}

func (s *authService) Login(ctx context.Context, req LoginRequest) (AuthResponse, error) {
	identifier, err := normalizeIdentifier(req.IdentifierType, req.Identifier)
	if err != nil {
		return AuthResponse{}, err
	}

	user, err := s.findUserByIdentifier(ctx, req.IdentifierType, identifier)
	if err != nil {
		return AuthResponse{}, err
	}
	if user == nil {
		return AuthResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
	}

	channel := ""
	if strings.TrimSpace(req.Password) != "" {
		channel = "PASSWORD"
		if !verifyPassword(user.PasswordHash, req.Password) {
			s.recordLogin(ctx, user.ID, identifier, channel, req.ClientIP, req.UserAgent, "FAILED")
			return AuthResponse{}, errorsx.New(errorsx.CodeInvalidCredentials, "登录凭证错误")
		}
	} else if strings.TrimSpace(req.Code) != "" {
		channel = "CODE"
		if err := s.verifyCode(ctx, "LOGIN", req.IdentifierType, identifier, req.Code); err != nil {
			return AuthResponse{}, err
		}
	} else {
		return AuthResponse{}, errorsx.New(errorsx.CodeBadRequest, "请提供验证码或密码")
	}

	pair, err := jwtx.IssueTokenPair(user.ID, s.accessTokenTTL, s.refreshTokenTTL, s.tokenSecret)
	if err != nil {
		return AuthResponse{}, fmt.Errorf("issue token pair: %w", err)
	}
	if err := s.storeRefreshToken(ctx, user.ID, pair.RefreshTokenID, pair.RefreshTokenExpiresAt); err != nil {
		return AuthResponse{}, err
	}

	s.recordLogin(ctx, user.ID, identifier, channel, req.ClientIP, req.UserAgent, "SUCCESS")
	return mapAuthResponse(user, pair), nil
}

func (s *authService) CurrentUser(ctx context.Context, accessToken string) (AuthUserResponse, error) {
	claims, err := jwtx.Parse(accessToken, s.tokenSecret)
	if err != nil || claims.TokenType != "access" {
		return AuthUserResponse{}, errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized)
	}

	user, err := s.users.FindByID(ctx, claims.UserID)
	if err != nil {
		return AuthUserResponse{}, err
	}
	if user == nil {
		return AuthUserResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
	}
	return mapAuthUser(user), nil
}

func (s *authService) Refresh(ctx context.Context, req TokenRefreshRequest) (TokenResponse, error) {
	claims, err := jwtx.Parse(req.RefreshToken, s.tokenSecret)
	if err != nil || claims.TokenType != "refresh" {
		return TokenResponse{}, errorsx.New(errorsx.CodeRefreshTokenInvalid, "刷新令牌无效")
	}

	key := refreshTokenKey(claims.UserID, claims.TokenID)
	exists, err := s.redis.Exists(ctx, key).Result()
	if err != nil {
		return TokenResponse{}, fmt.Errorf("check refresh token whitelist: %w", err)
	}
	if exists == 0 {
		return TokenResponse{}, errorsx.New(errorsx.CodeRefreshTokenInvalid, "刷新令牌无效")
	}

	user, err := s.users.FindByID(ctx, claims.UserID)
	if err != nil {
		return TokenResponse{}, err
	}
	if user == nil {
		return TokenResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
	}

	pair, err := jwtx.IssueTokenPair(user.ID, s.accessTokenTTL, s.refreshTokenTTL, s.tokenSecret)
	if err != nil {
		return TokenResponse{}, fmt.Errorf("issue token pair: %w", err)
	}
	if err := s.redis.Del(ctx, key).Err(); err != nil {
		return TokenResponse{}, fmt.Errorf("revoke old refresh token: %w", err)
	}
	if err := s.storeRefreshToken(ctx, user.ID, pair.RefreshTokenID, pair.RefreshTokenExpiresAt); err != nil {
		return TokenResponse{}, err
	}

	return TokenResponse{
		AccessToken:           pair.AccessToken,
		AccessTokenExpiresAt:  pair.AccessTokenExpiresAt,
		RefreshToken:          pair.RefreshToken,
		RefreshTokenExpiresAt: pair.RefreshTokenExpiresAt,
	}, nil
}

func (s *authService) Logout(ctx context.Context, req LogoutRequest) error {
	claims, err := jwtx.Parse(req.RefreshToken, s.tokenSecret)
	if err != nil {
		return nil
	}
	if claims.TokenType != "refresh" {
		return nil
	}
	if err := s.redis.Del(ctx, refreshTokenKey(claims.UserID, claims.TokenID)).Err(); err != nil {
		return fmt.Errorf("revoke refresh token: %w", err)
	}
	return nil
}

func (s *authService) ResetPassword(ctx context.Context, req PasswordResetRequest) error {
	identifier, err := normalizeIdentifier(req.IdentifierType, req.Identifier)
	if err != nil {
		return err
	}
	if err := validatePassword(req.NewPassword); err != nil {
		return err
	}

	user, err := s.findUserByIdentifier(ctx, req.IdentifierType, identifier)
	if err != nil {
		return err
	}
	if user == nil {
		return errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
	}

	if err := s.verifyCode(ctx, "RESET_PASSWORD", req.IdentifierType, identifier, req.Code); err != nil {
		return err
	}

	hash, err := hashPassword(req.NewPassword)
	if err != nil {
		return fmt.Errorf("hash new password: %w", err)
	}
	if err := s.users.UpdatePassword(ctx, user.ID, hash); err != nil {
		if err == gorm.ErrRecordNotFound {
			return errorsx.New(errorsx.CodeIdentifierNotFound, "账号不存在")
		}
		return err
	}

	if err := s.revokeAllRefreshTokens(ctx, user.ID); err != nil {
		return err
	}
	return nil
}

func (s *authService) verifyCode(ctx context.Context, scene string, identifierType string, identifier string, inputCode string) error {
	codeKey := verificationCodeKey(scene, identifierType, identifier)
	attemptsKey := verificationAttemptsKey(scene, identifierType, identifier)

	storedCode, err := s.redis.Get(ctx, codeKey).Result()
	if err == redis.Nil {
		return errorsx.New(errorsx.CodeVerificationNotFound, "验证码不存在或已过期")
	}
	if err != nil {
		return fmt.Errorf("load verification code: %w", err)
	}

	attempts, err := s.readAttempts(ctx, attemptsKey)
	if err != nil {
		return err
	}
	if attempts >= verificationMaxAttempts {
		return errorsx.New(errorsx.CodeVerificationTooManyAttempt, "验证码尝试次数过多")
	}

	if strings.TrimSpace(inputCode) != storedCode {
		newAttempts, err := s.redis.Incr(ctx, attemptsKey).Result()
		if err != nil {
			return fmt.Errorf("increase verification attempts: %w", err)
		}
		if newAttempts == 1 {
			if err := s.redis.Expire(ctx, attemptsKey, verificationCodeTTL).Err(); err != nil {
				return fmt.Errorf("set attempts ttl: %w", err)
			}
		}
		if int(newAttempts) >= verificationMaxAttempts {
			return errorsx.New(errorsx.CodeVerificationTooManyAttempt, "验证码尝试次数过多")
		}
		return errorsx.New(errorsx.CodeVerificationMismatch, "验证码错误")
	}

	if err := s.redis.Del(ctx, codeKey, attemptsKey).Err(); err != nil {
		return fmt.Errorf("clear verification code: %w", err)
	}
	return nil
}

func (s *authService) readAttempts(ctx context.Context, key string) (int, error) {
	val, err := s.redis.Get(ctx, key).Result()
	if err == redis.Nil {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("load verification attempts: %w", err)
	}
	attempts, convErr := strconv.Atoi(val)
	if convErr != nil {
		return 0, fmt.Errorf("parse verification attempts: %w", convErr)
	}
	return attempts, nil
}

func (s *authService) identifierExists(ctx context.Context, identifierType string, identifier string) (bool, error) {
	user, err := s.findUserByIdentifier(ctx, identifierType, identifier)
	if err != nil {
		return false, err
	}
	return user != nil, nil
}

func (s *authService) findUserByIdentifier(ctx context.Context, identifierType string, identifier string) (*model.User, error) {
	if strings.EqualFold(identifierType, "PHONE") {
		return s.users.FindByPhone(ctx, identifier)
	}
	return s.users.FindByEmail(ctx, identifier)
}

func (s *authService) storeRefreshToken(ctx context.Context, userID uint64, tokenID string, expiresAt time.Time) error {
	ttl := time.Until(expiresAt)
	if ttl <= 0 {
		ttl = time.Second
	}
	if err := s.redis.Set(ctx, refreshTokenKey(userID, tokenID), "1", ttl).Err(); err != nil {
		return fmt.Errorf("store refresh token whitelist: %w", err)
	}
	return nil
}

func (s *authService) revokeAllRefreshTokens(ctx context.Context, userID uint64) error {
	pattern := fmt.Sprintf("auth:refresh:%d:*", userID)
	iter := s.redis.Scan(ctx, 0, pattern, 200).Iterator()
	keys := make([]string, 0, 16)
	for iter.Next(ctx) {
		keys = append(keys, iter.Val())
	}
	if err := iter.Err(); err != nil {
		return fmt.Errorf("scan refresh tokens: %w", err)
	}
	if len(keys) == 0 {
		return nil
	}
	if err := s.redis.Del(ctx, keys...).Err(); err != nil {
		return fmt.Errorf("revoke all refresh tokens: %w", err)
	}
	return nil
}

func (s *authService) recordLogin(ctx context.Context, userID uint64, identifier, channel, ip, userAgent, status string) {
	if s.loginLogs == nil {
		return
	}
	log := &model.LoginLog{
		UserID:     uint64Ptr(userID),
		Identifier: identifier,
		Channel:    channel,
		IP:         stringNilIfBlank(ip),
		UserAgent:  stringNilIfBlank(userAgent),
		Status:     status,
	}
	_ = s.loginLogs.Create(ctx, log)
}

func normalizeIdentifier(identifierType string, identifier string) (string, error) {
	normalized := strings.TrimSpace(identifier)
	if normalized == "" {
		return "", errorsx.New(errorsx.CodeBadRequest, "标识不能为空")
	}

	if strings.EqualFold(identifierType, "PHONE") {
		if !phonePattern.MatchString(normalized) {
			return "", errorsx.New(errorsx.CodeBadRequest, "手机号格式错误")
		}
		return normalized, nil
	}

	normalized = strings.ToLower(normalized)
	if !emailPattern.MatchString(normalized) {
		return "", errorsx.New(errorsx.CodeBadRequest, "邮箱格式错误")
	}
	return normalized, nil
}

func validatePassword(password string) error {
	trimmed := strings.TrimSpace(password)
	if trimmed == "" {
		return errorsx.New(errorsx.CodePasswordPolicyViolation, "密码不能为空")
	}
	if len(trimmed) < 8 {
		return errorsx.New(errorsx.CodePasswordPolicyViolation, "密码长度至少8位")
	}

	hasLetter := false
	hasDigit := false
	for _, ch := range trimmed {
		if ch >= '0' && ch <= '9' {
			hasDigit = true
		}
		if (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') {
			hasLetter = true
		}
	}
	if !hasLetter || !hasDigit {
		return errorsx.New(errorsx.CodePasswordPolicyViolation, "密码需包含字母和数字")
	}
	return nil
}

func hashPassword(password string) (string, error) {
	hashed, err := bcrypt.GenerateFromPassword([]byte(strings.TrimSpace(password)), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(hashed), nil
}

func verifyPassword(passwordHash, password string) bool {
	if strings.TrimSpace(passwordHash) == "" {
		return false
	}
	return bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(strings.TrimSpace(password))) == nil
}

func mapAuthResponse(user *model.User, pair jwtx.TokenPair) AuthResponse {
	return AuthResponse{
		User: mapAuthUser(user),
		Token: TokenResponse{
			AccessToken:           pair.AccessToken,
			AccessTokenExpiresAt:  pair.AccessTokenExpiresAt,
			RefreshToken:          pair.RefreshToken,
			RefreshTokenExpiresAt: pair.RefreshTokenExpiresAt,
		},
	}
}

func mapAuthUser(user *model.User) AuthUserResponse {
	birthday := (*string)(nil)
	if user.Birthday != nil {
		formatted := user.Birthday.Format("2006-01-02")
		birthday = &formatted
	}

	phone := ""
	if user.Phone != nil {
		phone = *user.Phone
	}

	avatar := ""
	if user.Avatar != nil {
		avatar = *user.Avatar
	}

	return AuthUserResponse{
		ID:       user.ID,
		Nickname: user.Nickname,
		Avatar:   avatar,
		Phone:    phone,
		ZhID:     user.ZgID,
		Birthday: birthday,
		School:   user.School,
		Bio:      user.Bio,
		Gender:   user.Gender,
		TagJSON:  user.TagsJSON,
	}
}

func generateNickname() string {
	return "知光用户" + strconv.FormatInt(time.Now().UnixNano()%100000000, 10)
}

func verificationCodeKey(scene, identifierType, identifier string) string {
	return fmt.Sprintf("auth:code:%s:%s:%s", strings.ToUpper(strings.TrimSpace(scene)), strings.ToUpper(strings.TrimSpace(identifierType)), identifier)
}

func verificationAttemptsKey(scene, identifierType, identifier string) string {
	return fmt.Sprintf("auth:code:attempts:%s:%s:%s", strings.ToUpper(strings.TrimSpace(scene)), strings.ToUpper(strings.TrimSpace(identifierType)), identifier)
}

func refreshTokenKey(userID uint64, tokenID string) string {
	return fmt.Sprintf("auth:refresh:%d:%s", userID, tokenID)
}

func isDuplicateKey(err error) bool {
	if err == nil {
		return false
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "duplicate") && strings.Contains(msg, "entry")
}

func stringPtr(v string) *string {
	copyV := v
	return &copyV
}

func stringNilIfBlank(v string) *string {
	trimmed := strings.TrimSpace(v)
	if trimmed == "" {
		return nil
	}
	return &trimmed
}

func uint64Ptr(v uint64) *uint64 {
	copyV := v
	return &copyV
}
