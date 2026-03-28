# backend-golang

## 当前能力（Phase 0-4 部分）
- Gin HTTP 服务骨架
- 使用 `viper` 从 YAML 配置文件读取配置（默认 `configs/config.yaml`）
- 使用 `zap` 封装日志
- MySQL/GORM 与 Redis 客户端初始化
- 健康检查：`/healthz`
- 就绪检查：`/readyz`（检查 MySQL + Redis）
- 统一错误响应：`{ "code": "...", "message": "..." }`
- Casbin 最小鉴权策略（公开接口 + 登录保护接口）
- Outbox 事件持久化写入（relation/counter 业务动作）
- Outbox relay 最小骨架（按主键顺序拉取 + sink 抽象 + 一次执行入口）

## 关键配置
```yaml
auth:
  jwt:
    secret: "zhiguang-dev-jwt-secret"
    access_token_ttl: "15m"
    refresh_token_ttl: "168h"

oss:
  endpoint: ""
  access_key_id: ""
  access_key_secret: ""
  bucket: ""
  public_domain: ""
  folder: "avatars"
  presign_expire_seconds: 600

outbox:
  relay:
    batch_size: 100
    sink: "stdout" # stdout | kafka
  kafka:
    topic_default: "outbox.events"
    aggregate_topics: {}
```

## 已支持接口
### Auth
- `POST /api/v1/auth/send-code`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/token/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/password/reset`
- `GET /api/v1/auth/me`

### Profile
- `GET /api/v1/profile`
- `PATCH /api/v1/profile`
- `POST /api/v1/profile/avatar`

### Storage
- `POST /api/v1/storage/presign`

### Relation
- `POST /api/v1/relation/follow`
- `POST /api/v1/relation/unfollow`
- `GET /api/v1/relation/status`
- `GET /api/v1/relation/following`
- `GET /api/v1/relation/followers`
- `GET /api/v1/relation/counter`

### Action / Counter
- `POST /api/v1/action/like`
- `POST /api/v1/action/unlike`
- `POST /api/v1/action/fav`
- `POST /api/v1/action/unfav`
- `GET /api/v1/counter/:entityType/:entityId`

## 常用启动命令
1. 先启动依赖（MySQL + Redis）：
   - `docker compose --env-file ../deploy/docker/.env -f ../deploy/docker/docker-compose.yaml --profile core up -d`
2. 启动 HTTP 服务（使用默认配置文件）：
   - `go run .`
3. 手动执行一次 outbox relay（默认输出到 stdout）：
   - `go run ./cmd/outbox-relay-once`

## 验证
- `go test ./...`
- `curl http://localhost:8080/healthz`
- `curl http://localhost:8080/readyz`
