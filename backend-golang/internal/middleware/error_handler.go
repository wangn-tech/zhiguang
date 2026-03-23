package middleware

import (
	"fmt"
	"runtime/debug"
	"zhiguang/pkg/errorsx"
	"zhiguang/pkg/logx"

	"github.com/gin-gonic/gin"
)

// ErrorHandler 统一处理 panic 与请求链路错误，输出标准异常响应。
func ErrorHandler() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if rec := recover(); rec != nil {
				logx.S().Errorw("panic recovered",
					"panic", rec,
					"path", c.Request.URL.Path,
					"method", c.Request.Method,
					"stack", string(debug.Stack()),
				)

				if !c.Writer.Written() {
					status, body := errorsx.Normalize(fmt.Errorf("panic: %v", rec))
					c.AbortWithStatusJSON(status, body)
				}
			}
		}()

		c.Next()

		if c.Writer.Written() || len(c.Errors) == 0 {
			return
		}

		last := c.Errors.Last()
		if last == nil || last.Err == nil {
			return
		}

		status, body := errorsx.Normalize(last.Err)
		c.AbortWithStatusJSON(status, body)
	}
}
