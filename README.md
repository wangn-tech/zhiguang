# 知光平台后端：项目亮点

知光是一个面向知识发布、发现和互动的社区平台。本仓库以 `backend/` 为后端主工程，基于 Java 21
和 Spring Boot 构建；前端以 Git Submodule 方式维护。当前已完成用户、认证、个人资料、对象存储、
知文、关系、互动计数、缓存与搜索等基础模块。

> 本文仅描述仓库中已经落地的能力。LLM / RAG 问答模块仍在后续规划中，不将其作为当前项目亮点。

## 技术栈

- Java 21、Spring Boot、Spring Security、MyBatis、MySQL
- Redis、Redisson、Caffeine、Kafka、Canal
- Elasticsearch + IK 中文分词器
- 阿里云 OSS Java SDK V2
- Docker Compose、JUnit 5、Testcontainers

## 认证与会话安全

- 基于 Spring Security 的 RS256 双 JWT 认证：Access Token 用于无状态接口鉴权，Refresh Token
  通过 Redis `jti` 白名单管理。
- Refresh Token 轮换使用 Redis 原子操作，同一个旧令牌并发刷新时只允许一个请求成功；支持退出当前
  会话和密码重置后撤销全部刷新会话。
- 注册、登录和重置密码使用按场景隔离的验证码；发送冷却、每日次数、校验尝试次数与正确验证码消费
  均由 Redis Lua 保证原子性。
- 密码使用 BCrypt（strength 12）保存；认证审计日志避免记录密码、验证码和令牌等敏感数据。

## 高并发互动计数与自愈

- 点赞、收藏事实以 Redis 分片位图保存。Lua 脚本原子完成状态判断、置位/清位、分片索引维护和事件
  序号递增，因此重复操作天然幂等。
- 实体计数采用固定长度的 Redis SDS 二进制结构，降低多个数值字段分别存储带来的空间和网络开销。
- 开启 Kafka 后，互动变更写入 `counter-events`，消费者先写入 Redis 聚合桶，再定时通过 Lua 原子
  折叠到 SDS，削峰并避免频繁修改同一计数键。
- SDS 缺失或损坏时，按需从位图事实层执行 `BITCOUNT` 重建。Redisson 锁、限流器和指数退避用于协调
  多实例恢复；恢复围栏结合实体单调事件序号，能跳过恢复前延迟抵达的 Kafka 消息，避免重复计数。

## 可靠的事件驱动关系与索引同步

- 关注、取关和知文索引请求与 MySQL 业务写入处于同一事务中，通过 Outbox 表避免“业务已提交但事件
  丢失”的双写问题。
- Canal 仅在事务提交后订阅 Outbox binlog，并转发到 Kafka；Kafka 发送成功后才确认 Canal 位点，
  提供至少一次投递语义。
- 下游以 Outbox ID 去重：关系消费者异步更新粉丝/关注缓存与用户计数，搜索消费者以知文 ID 幂等
  更新 Elasticsearch 索引。重复投递不会破坏最终结果。

## 两级缓存与 singleflight 防击穿

- 公开知文详情、公共 Feed、作者 Feed 使用 Caffeine L1 + Redis L2 两级缓存；共享快照只保存稳定
  内容字段，不缓存任何访问者专属数据。
- 每次响应再从计数服务补齐实时点赞/收藏数，并按当前用户读取 `liked`、`faved`，避免跨用户缓存污染。
- 知文发布、编辑、可见性变更、置顶和删除通过事务提交后的应用事件统一失效详情和 Feed 缓存；Feed
  页面使用 Redis Set 反向索引并通过 `SSCAN` 分批删除，避免阻塞 Redis 的 `KEYS`。
- 自研进程内 `CacheSingleFlight` 合并同一缓存键的并发回源：leader 负责查询 MySQL 与回填缓存，其余
  请求等待同一个 `CompletableFuture`，异常时自动清理飞行任务并允许下一次请求重试。

## 可重建的中文搜索

- Elasticsearch 仅保存已发布、公开知文，是可删除、可重建的派生索引；MySQL 仍是内容事实来源，Redis
  位图/SDS 仍是互动事实来源。
- 使用 IK 分词器实现中文全文检索，支持标题、摘要、正文的加权搜索、标签过滤、`search_after` 游标
  分页与 completion 标题联想。
- 搜索索引通过 Outbox → Canal → Kafka 异步更新；消费者按知文 ID 覆盖写入或写入 tombstone，适配
  至少一次投递下的安全重放。
- Elasticsearch 临时不可用时，搜索和联想降级到 MySQL；游标显式携带数据源标记，确保同一次分页不会
  在 ES 与 MySQL 的不同排序规则间切换。

## 安全的对象存储与知文发布流程

- 使用阿里云 OSS SDK V2；AccessKey 仅从运行环境注入，不进入 YAML、代码、日志或 Git。
- 头像由后端校验 MIME/文件头后上传；知文正文和图片采用“创建草稿 → 后端生成受控对象键预签名 URL
  → 浏览器直传 OSS → 后端确认内容元数据”的渐进式流程。
- 预签名接口校验草稿归属、上传场景、类型、大小和对象键前缀，避免客户端借用系统凭证写入任意路径。

## 工程化与可交付性

- 使用 Snowflake ID，并通过环境变量显式分配 `workerId`、`datacenterId`，适配多节点部署。
- Docker Compose 提供 MySQL、Redis、Kafka、Elasticsearch、Canal 等本地依赖；搜索、关系事件、互动
  事件均支持环境变量开关，基础模块可在未启用这些组件时运行。
- Redis、Elasticsearch 等关键链路使用 Testcontainers 真实容器集成测试；并覆盖缓存回源合并、计数恢复
  围栏、事件幂等和搜索索引链路等边界场景。
