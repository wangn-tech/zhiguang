## 项目说明

本仓库以后端开发为主，后端使用 Java 21 和 Maven。默认优先处理
`backend/`，除非任务明确涉及前端或基础设施。

## 目录结构

- `backend/`：Java 后端代码，源码位于 `src/main/java`，测试位于
  `src/test/java`。
- `frontend/`：React 前端 Git 子模块。修改前端时应在子模块仓库中提交，
  再在本仓库更新子模块指针。
- `deploy/mysql/schema.sql`：MySQL 首次创建数据卷时执行的初始化脚本。
- `compose.yaml`：本地开发使用的 MySQL 和 Redis。
- `.github/workflows/ci.yml`：前后端 CI 配置。

## 常用命令

### 后端构建与测试

```bash
cd backend
if [ -f mvnw ]; then ./mvnw verify; else mvn verify; fi
```

不要使用 `-DskipTests` 代替正常验证。

### 启动本地依赖

```bash
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

停止服务但保留数据：

```bash
docker compose down
```

`docker compose down -v` 会删除本地 MySQL 和 Redis 数据，不要在未明确要求时执行。
`deploy/mysql/schema.sql` 只在 MySQL 数据卷首次创建时自动执行。

### 前端验证

仅在任务涉及前端或子模块指针时执行：

```bash
git submodule update --init --recursive
cd frontend
npm ci
npm run lint
npm run build
```
