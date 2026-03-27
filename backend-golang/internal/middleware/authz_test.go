package middleware

import (
	"encoding/json"
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

func TestAuthz_KnowPostPatch_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.PATCH("/api/v1/knowposts/:id", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPatch, "/api/v1/knowposts/123", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestAuthz_KnowPostPublish_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/knowposts/:id/publish", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/123/publish", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestAuthz_KnowPostVisibility_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.PATCH("/api/v1/knowposts/:id/visibility", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPatch, "/api/v1/knowposts/123/visibility", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestAuthz_KnowPostDelete_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.DELETE("/api/v1/knowposts/:id", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodDelete, "/api/v1/knowposts/123", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestAuthz_KnowPostSuggestDescription_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/knowposts/description/suggest", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/knowposts/description/suggest", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostFeed_AllowsAnonymous(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/knowposts/feed", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/feed?page=1&size=20", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostMine_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/knowposts/mine", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/mine?page=1&size=20", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostDetail_AllowsAnonymous(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/knowposts/detail/:id", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/detail/123", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_AnonymousRoute_WithValidToken_SetsUserID(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/knowposts/detail/:id", func(c *gin.Context) {
		raw, ok := c.Get("auth_user_id")
		if !ok {
			c.Status(http.StatusInternalServerError)
			return
		}
		uid, ok := raw.(uint64)
		if !ok || uid != 1001 {
			c.Status(http.StatusInternalServerError)
			return
		}
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/knowposts/detail/123", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_KnowPostTop_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.PATCH("/api/v1/knowposts/:id/top", func(c *gin.Context) {
		c.Status(http.StatusNoContent)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPatch, "/api/v1/knowposts/123/top", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusNoContent)
	}
}

func TestAuthz_ActionLike_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/action/like", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_CounterGet_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/counter/:entityType/:entityId", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/123?metrics=like,fav", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationFollow_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/relation/follow", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/follow?toUserId=1002", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationFollowing_AllowsAnonymous(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/following", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ActionUnlike_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/action/unlike", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/unlike", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ActionFav_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/action/fav", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/fav", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_ActionUnfav_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/action/unfav", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/action/unfav", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationUnfollow_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.POST("/api/v1/relation/unfollow", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/relation/unfollow?toUserId=1002", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationStatus_RejectsMissingToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/status", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestAuthz_RelationStatus_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/status", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationFollowers_AllowsAnonymous(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/followers", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/followers?userId=1001", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_RelationCounter_RejectsMissingToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/counter", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/counter?userId=1001", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestAuthz_RelationCounter_AllowsValidAccessToken(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/counter", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/relation/counter?userId=1001", nil)
	req.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", w.Code, http.StatusOK)
	}
}

func TestAuthz_MixedFlow_PublicReadableAndProtectedStrict(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))

	var publicHasUser bool
	var publicUserID uint64
	r.GET("/api/v1/relation/following", func(c *gin.Context) {
		publicHasUser = false
		publicUserID = 0
		if raw, ok := c.Get("auth_user_id"); ok {
			uid, castOK := raw.(uint64)
			if castOK {
				publicHasUser = true
				publicUserID = uid
			}
		}
		c.Status(http.StatusOK)
	})
	r.GET("/api/v1/relation/status", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})
	r.POST("/api/v1/action/like", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})
	r.GET("/api/v1/counter/:entityType/:entityId", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	anonymousFollowingReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001", nil)
	anonymousFollowingW := httptest.NewRecorder()
	r.ServeHTTP(anonymousFollowingW, anonymousFollowingReq)
	if anonymousFollowingW.Code != http.StatusOK {
		t.Fatalf("anonymous following status = %d, want %d", anonymousFollowingW.Code, http.StatusOK)
	}
	if publicHasUser {
		t.Fatalf("anonymous following unexpectedly contains auth_user_id = %d", publicUserID)
	}

	accessFollowingReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/following?userId=1001", nil)
	accessFollowingReq.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	accessFollowingW := httptest.NewRecorder()
	r.ServeHTTP(accessFollowingW, accessFollowingReq)
	if accessFollowingW.Code != http.StatusOK {
		t.Fatalf("access following status = %d, want %d", accessFollowingW.Code, http.StatusOK)
	}
	if !publicHasUser || publicUserID != 1001 {
		t.Fatalf("access following auth context = (%v,%d), want (true,1001)", publicHasUser, publicUserID)
	}

	statusNoTokenReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	statusNoTokenW := httptest.NewRecorder()
	r.ServeHTTP(statusNoTokenW, statusNoTokenReq)
	if statusNoTokenW.Code != http.StatusUnauthorized {
		t.Fatalf("status without token = %d, want %d", statusNoTokenW.Code, http.StatusUnauthorized)
	}

	statusRefreshReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	statusRefreshReq.Header.Set("Authorization", "Bearer "+pair.RefreshToken)
	statusRefreshW := httptest.NewRecorder()
	r.ServeHTTP(statusRefreshW, statusRefreshReq)
	if statusRefreshW.Code != http.StatusUnauthorized {
		t.Fatalf("status with refresh token = %d, want %d", statusRefreshW.Code, http.StatusUnauthorized)
	}

	statusAccessReq := httptest.NewRequest(http.MethodGet, "/api/v1/relation/status?toUserId=1002", nil)
	statusAccessReq.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	statusAccessW := httptest.NewRecorder()
	r.ServeHTTP(statusAccessW, statusAccessReq)
	if statusAccessW.Code != http.StatusOK {
		t.Fatalf("status with access token = %d, want %d", statusAccessW.Code, http.StatusOK)
	}

	actionNoTokenReq := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", nil)
	actionNoTokenW := httptest.NewRecorder()
	r.ServeHTTP(actionNoTokenW, actionNoTokenReq)
	if actionNoTokenW.Code != http.StatusUnauthorized {
		t.Fatalf("action without token = %d, want %d", actionNoTokenW.Code, http.StatusUnauthorized)
	}

	actionAccessReq := httptest.NewRequest(http.MethodPost, "/api/v1/action/like", nil)
	actionAccessReq.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	actionAccessW := httptest.NewRecorder()
	r.ServeHTTP(actionAccessW, actionAccessReq)
	if actionAccessW.Code != http.StatusOK {
		t.Fatalf("action with access token = %d, want %d", actionAccessW.Code, http.StatusOK)
	}

	counterAccessReq := httptest.NewRequest(http.MethodGet, "/api/v1/counter/knowpost/123?metrics=like,fav", nil)
	counterAccessReq.Header.Set("Authorization", "Bearer "+pair.AccessToken)
	counterAccessW := httptest.NewRecorder()
	r.ServeHTTP(counterAccessW, counterAccessReq)
	if counterAccessW.Code != http.StatusOK {
		t.Fatalf("counter with access token = %d, want %d", counterAccessW.Code, http.StatusOK)
	}
}

func TestAuthz_ProtectedEndpoints_MissingToken_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/status", func(c *gin.Context) { c.Status(http.StatusOK) })
	r.POST("/api/v1/action/like", func(c *gin.Context) { c.Status(http.StatusOK) })
	r.GET("/api/v1/counter/:entityType/:entityId", func(c *gin.Context) { c.Status(http.StatusOK) })

	testCases := []struct {
		name   string
		method string
		path   string
	}{
		{name: "relation status", method: http.MethodGet, path: "/api/v1/relation/status?toUserId=1002"},
		{name: "action like", method: http.MethodPost, path: "/api/v1/action/like"},
		{name: "counter get", method: http.MethodGet, path: "/api/v1/counter/knowpost/123?metrics=like,fav"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, nil)
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			if w.Code != http.StatusUnauthorized {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
			}
			if code := authzErrorCodeFromBody(t, w.Body.Bytes()); code != "INVALID_CREDENTIALS" {
				t.Fatalf("code = %s, want INVALID_CREDENTIALS", code)
			}
		})
	}
}

func TestAuthz_ProtectedEndpoints_RefreshToken_ErrorContract(t *testing.T) {
	gin.SetMode(gin.TestMode)

	enforcer, err := NewCasbinEnforcer()
	if err != nil {
		t.Fatalf("NewCasbinEnforcer() error = %v", err)
	}

	r := gin.New()
	r.Use(ErrorHandler(), Authz(enforcer, "test-secret"))
	r.GET("/api/v1/relation/status", func(c *gin.Context) { c.Status(http.StatusOK) })
	r.POST("/api/v1/action/like", func(c *gin.Context) { c.Status(http.StatusOK) })
	r.GET("/api/v1/counter/:entityType/:entityId", func(c *gin.Context) { c.Status(http.StatusOK) })

	pair, err := jwtx.IssueTokenPair(1001, 15*time.Minute, 7*24*time.Hour, "test-secret")
	if err != nil {
		t.Fatalf("IssueTokenPair() error = %v", err)
	}

	testCases := []struct {
		name   string
		method string
		path   string
	}{
		{name: "relation status", method: http.MethodGet, path: "/api/v1/relation/status?toUserId=1002"},
		{name: "action like", method: http.MethodPost, path: "/api/v1/action/like"},
		{name: "counter get", method: http.MethodGet, path: "/api/v1/counter/knowpost/123?metrics=like,fav"},
	}

	for _, tc := range testCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(tc.method, tc.path, nil)
			req.Header.Set("Authorization", "Bearer "+pair.RefreshToken)
			w := httptest.NewRecorder()
			r.ServeHTTP(w, req)
			if w.Code != http.StatusUnauthorized {
				t.Fatalf("status = %d, want %d", w.Code, http.StatusUnauthorized)
			}
			if code := authzErrorCodeFromBody(t, w.Body.Bytes()); code != "INVALID_CREDENTIALS" {
				t.Fatalf("code = %s, want INVALID_CREDENTIALS", code)
			}
		})
	}
}

func authzErrorCodeFromBody(t *testing.T, body []byte) string {
	t.Helper()
	var resp map[string]any
	if err := json.Unmarshal(body, &resp); err != nil {
		t.Fatalf("decode authz error body: %v", err)
	}
	code, _ := resp["code"].(string)
	return code
}
