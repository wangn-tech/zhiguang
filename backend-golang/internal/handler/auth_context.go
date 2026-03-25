package handler

import (
	"net/http"
	"zhiguang/pkg/errorsx"

	"github.com/gin-gonic/gin"
)

// AuthUserIDFromContext 从上下文读取鉴权中间件注入的用户 ID。
func AuthUserIDFromContext(c *gin.Context) (uint64, error) {
	raw, ok := c.Get("auth_user_id")
	if !ok {
		return 0, errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized)
	}
	userID, ok := raw.(uint64)
	if !ok || userID == 0 {
		return 0, errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized)
	}
	return userID, nil
}
