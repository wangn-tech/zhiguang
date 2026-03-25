# backend-golang

## Phase 0-2 当前能力
- Gin HTTP 服务骨架
- 使用 `viper` 从 YAML 配置文件读取配置（默认 `configs/config.yaml`）
- 使用 `zap` 封装日志
- MySQL/GORM 与 Redis 客户端初始化
- 健康检查：`/healthz`
- 就绪检查：`/readyz`（检查 MySQL + Redis）
- 统一错误响应：`{ "code": "...", "message": "..." }`
- Casbin 最小鉴权策略（公开接口 + 登录保护接口）

## Auth 配置
```yaml
auth:
  jwt:
    secret: "zhiguang-dev-jwt-secret"
    access_token_ttl: "15m"
    refresh_token_ttl: "168h"
```

## 已支持的 Auth 接口
- `POST /api/v1/auth/send-code`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/token/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/password/reset`
- `GET /api/v1/auth/me`

## 常用启动命令
1. 先启动依赖（MySQL + Redis）：
   - `docker compose --env-file ../deploy/docker/.env -f ../deploy/docker/docker-compose.yaml --profile core up -d`
2. 启动服务（使用默认配置文件）：
   - `go run .`

## 验证
- `go test ./...`
- `curl http://localhost:8080/healthz`
- `curl http://localhost:8080/readyz`
