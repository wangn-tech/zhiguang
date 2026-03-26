package middleware

import (
	"net/http"
	"strings"
	"zhiguang/pkg/errorsx"
	"zhiguang/pkg/jwtx"

	"github.com/casbin/casbin/v2"
	"github.com/casbin/casbin/v2/model"
	"github.com/gin-gonic/gin"
)

const (
	authzSubjectAnonymous = "anonymous"
	authzSubjectUser      = "user"
)

// AuthzPolicy 定义一条访问控制规则。
type AuthzPolicy struct {
	Subject string
	Path    string
	Method  string
}

// NewCasbinEnforcer 创建最小可用权限引擎并写入默认策略。
func NewCasbinEnforcer() (*casbin.Enforcer, error) {
	m, err := model.NewModelFromString(`
	[request_definition]
	r = sub, obj, act

	[policy_definition]
	p = sub, obj, act

	[policy_effect]
	e = some(where (p.eft == allow))

	[matchers]
	m = keyMatch2(r.obj, p.obj) && regexMatch(r.act, p.act) && (p.sub == "*" || r.sub == p.sub)
	`)
	if err != nil {
		return nil, err
	}

	enforcer, err := casbin.NewEnforcer(m)
	if err != nil {
		return nil, err
	}

	for _, policy := range defaultAuthzPolicies() {
		if _, err := enforcer.AddPolicy(policy.Subject, policy.Path, policy.Method); err != nil {
			return nil, err
		}
	}

	return enforcer, nil
}

// Authz 基于 Casbin 和访问令牌校验请求权限。
func Authz(enforcer *casbin.Enforcer, tokenSecret string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.Method == http.MethodOptions {
			c.Next()
			return
		}

		path := c.Request.URL.Path
		method := c.Request.Method

		allowedAnonymous, err := enforcer.Enforce(authzSubjectAnonymous, path, method)
		if err != nil {
			c.Error(err)
			c.Abort()
			return
		}
		if allowedAnonymous {
			c.Next()
			return
		}

		token := parseBearerToken(c.GetHeader("Authorization"))
		if token == "" {
			c.Error(errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized))
			c.Abort()
			return
		}

		claims, err := jwtx.Parse(token, tokenSecret)
		if err != nil || claims.TokenType != "access" {
			c.Error(errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "未登录或登录已过期", http.StatusUnauthorized))
			c.Abort()
			return
		}

		allowedUser, err := enforcer.Enforce(authzSubjectUser, path, method)
		if err != nil {
			c.Error(err)
			c.Abort()
			return
		}
		if !allowedUser {
			c.Error(errorsx.NewWithStatus(errorsx.CodeInvalidCredentials, "无权限访问", http.StatusForbidden))
			c.Abort()
			return
		}

		c.Set("auth_user_id", claims.UserID)
		c.Next()
	}
}

func defaultAuthzPolicies() []AuthzPolicy {
	return []AuthzPolicy{
		{Subject: "*", Path: "/healthz", Method: "GET"},
		{Subject: "*", Path: "/readyz", Method: "GET"},
		{Subject: "*", Path: "/api/v1/auth/send-code", Method: "POST"},
		{Subject: "*", Path: "/api/v1/auth/register", Method: "POST"},
		{Subject: "*", Path: "/api/v1/auth/login", Method: "POST"},
		{Subject: "*", Path: "/api/v1/auth/token/refresh", Method: "POST"},
		{Subject: "*", Path: "/api/v1/auth/logout", Method: "POST"},
		{Subject: "*", Path: "/api/v1/auth/password/reset", Method: "POST"},
		{Subject: authzSubjectUser, Path: "/api/v1/auth/me", Method: "GET"},
		{Subject: authzSubjectUser, Path: "/api/v1/profile", Method: "GET"},
		{Subject: authzSubjectUser, Path: "/api/v1/profile", Method: "PATCH"},
		{Subject: authzSubjectUser, Path: "/api/v1/profile/avatar", Method: "POST"},
		{Subject: authzSubjectUser, Path: "/api/v1/storage/presign", Method: "POST"},
		{Subject: "*", Path: "/api/v1/knowposts/feed", Method: "GET"},
		{Subject: authzSubjectUser, Path: "/api/v1/knowposts/mine", Method: "GET"},
		{Subject: authzSubjectUser, Path: "/api/v1/knowposts/drafts", Method: "POST"},
		{Subject: authzSubjectUser, Path: "/api/v1/knowposts/:id/content/confirm", Method: "POST"},
		{Subject: authzSubjectUser, Path: "/api/v1/knowposts/:id", Method: "PATCH"},
		{Subject: authzSubjectUser, Path: "/api/v1/knowposts/:id/publish", Method: "POST"},
	}
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
