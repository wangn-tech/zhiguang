# backend-golang

## Phase 0 基线能力
- Gin HTTP 服务骨架
- 使用 `viper` 从 YAML 配置文件读取配置
- 使用 `zap` 封装日志
- MySQL/GORM 与 Redis 客户端初始化
- 健康检查：`/healthz`
- 就绪检查：`/readyz`（检查 MySQL + Redis）

## 默认配置文件
- 路径：`configs/config.yaml`

## API 响应约定（与 backend-java 对齐）
- 成功响应：返回业务 DTO（不额外包裹 `code/data`）。
- 失败响应：统一返回 JSON `{ "code": "...", "message": "..." }`。
- 通用错误处理：由 Gin 全局异常处理中间件统一处理 panic 与链路错误。

## 常用启动命令
1. 先启动依赖（MySQL + Redis）：
   - `docker compose --env-file ../deploy/docker/.env -f ../deploy/docker/docker-compose.yaml --profile core up -d`
2. 启动服务（使用默认配置文件）：
   - `go run ./cmd/server`

## 验证
- `go test ./...`
- `curl http://localhost:8080/healthz`
- `curl http://localhost:8080/readyz`
