package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
	"zhiguang/pkg/jwtx"

	"github.com/gin-gonic/gin"
)

func TestAuthz_PublicRoute_AllowsAnonymous(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/auth/login", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ProtectedRoute_RejectsMissingToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/auth/me", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/me", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestAuthz_ProtectedRoute_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/auth/me", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/auth/me", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ProfilePatch_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.PATCH("/api/v1/profile", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPatch, "/api/v1/profile", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_StoragePresign_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/storage/presign", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/storage/presign", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ProfileGet_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/profile", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/profile", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostDraftCreate_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/knowposts/drafts", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/drafts", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostContentConfirm_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/knowposts/:id/content/confirm", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/123/content/confirm", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}
