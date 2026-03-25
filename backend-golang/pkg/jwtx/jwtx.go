package jwtx

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
)

var (
	errInvalidTokenFormat = errors.New("invalid token format")
	errInvalidTokenSign   = errors.New("invalid token signature")
	errTokenExpired       = errors.New("token expired")
)

// Claims 是认证令牌声明。
type Claims struct {
	UserID    uint64 `json:"uid"`
	TokenType string `json:"typ"`
	TokenID   string `json:"jti"`
	IssuedAt  int64  `json:"iat"`
	ExpiresAt int64  `json:"exp"`
}

// TokenPair 表示一对访问令牌与刷新令牌。
type TokenPair struct {
	AccessToken           string
	AccessTokenExpiresAt  time.Time
	RefreshToken          string
	RefreshTokenExpiresAt time.Time
	RefreshTokenID        string
}

type header struct {
	Alg string `json:"alg"`
	Typ string `json:"typ"`
}

var defaultHeader = header{Alg: "HS256", Typ: "JWT"}

// IssueTokenPair 生成 access/refresh 双令牌。
func IssueTokenPair(userID uint64, accessTTL, refreshTTL time.Duration, secret string) (TokenPair, error) {
	now := time.Now()
	accessExp := now.Add(accessTTL)
	refreshExp := now.Add(refreshTTL)

	accessJTI, err := randomID(16)
	if err != nil {
		return TokenPair{}, fmt.Errorf("new access jti: %w", err)
	}
	refreshJTI, err := randomID(16)
	if err != nil {
		return TokenPair{}, fmt.Errorf("new refresh jti: %w", err)
	}

	accessToken, err := issue(Claims{
		UserID:    userID,
		TokenType: "access",
		TokenID:   accessJTI,
		IssuedAt:  now.Unix(),
		ExpiresAt: accessExp.Unix(),
	}, secret)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue access token: %w", err)
	}

	refreshToken, err := issue(Claims{
		UserID:    userID,
		TokenType: "refresh",
		TokenID:   refreshJTI,
		IssuedAt:  now.Unix(),
		ExpiresAt: refreshExp.Unix(),
	}, secret)
	if err != nil {
		return TokenPair{}, fmt.Errorf("issue refresh token: %w", err)
	}

	return TokenPair{
		AccessToken:           accessToken,
		AccessTokenExpiresAt:  accessExp,
		RefreshToken:          refreshToken,
		RefreshTokenExpiresAt: refreshExp,
		RefreshTokenID:        refreshJTI,
	}, nil
}

// Parse 解析并校验 token。
func Parse(token string, secret string) (Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Claims{}, errInvalidTokenFormat
	}

	headerPart, payloadPart, signPart := parts[0], parts[1], parts[2]
	signed := headerPart + "." + payloadPart
	expected := signHS256(signed, secret)
	if !hmac.Equal([]byte(expected), []byte(signPart)) {
		return Claims{}, errInvalidTokenSign
	}

	var claims Claims
	if err := decodePart(payloadPart, &claims); err != nil {
		return Claims{}, fmt.Errorf("decode payload: %w", err)
	}
	if time.Now().Unix() > claims.ExpiresAt {
		return Claims{}, errTokenExpired
	}
	if claims.UserID == 0 || claims.TokenType == "" || claims.TokenID == "" {
		return Claims{}, errInvalidTokenFormat
	}

	return claims, nil
}

func issue(claims Claims, secret string) (string, error) {
	headerPart, err := encodePart(defaultHeader)
	if err != nil {
		return "", fmt.Errorf("encode header: %w", err)
	}
	payloadPart, err := encodePart(claims)
	if err != nil {
		return "", fmt.Errorf("encode payload: %w", err)
	}
	signed := headerPart + "." + payloadPart
	signPart := signHS256(signed, secret)
	return signed + "." + signPart, nil
}

func encodePart(v any) (string, error) {
	raw, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func decodePart(raw string, out any) error {
	decoded, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return err
	}
	return json.Unmarshal(decoded, out)
}

func signHS256(data, secret string) string {
	h := hmac.New(sha256.New, []byte(secret))
	h.Write([]byte(data))
	return base64.RawURLEncoding.EncodeToString(h.Sum(nil))
}

func randomID(n int) (string, error) {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
