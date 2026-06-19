# 32. Redis 草稿缓存与提交恢复增强

## 目标
- 在 Batch 31 的草稿 revision 和提交 token 基础上，引入 Redis 草稿缓存能力。
- Redis 作为作答草稿加速层，数据库仍是最终落点。
- 默认不依赖 Redis，本地开发和单机部署不受影响。

## 依赖与配置
- 后端新增 `spring-boot-starter-data-redis`。
- 新增 Redis 连接配置：
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_PASSWORD`
  - `REDIS_DATABASE`
  - `REDIS_TIMEOUT`
- 新增业务开关：
  - `EXAM_DRAFT_REDIS_ENABLED=false`
  - `EXAM_DRAFT_REDIS_TTL_SECONDS=21600`
- Redis health 默认关闭：
  - `REDIS_HEALTH_ENABLED=false`
  - 生产启用 Redis 草稿缓存后可同步打开。

## 后端改造
- 新增 `ExamDraftCacheService`：
  - Redis key：`smart-exam:attempt-draft:{attemptId}`。
  - 缓存内容：`answers`、`clientDraftId`、`revision`、`savedAt`、`cachedAt`。
  - 所有 Redis 异常都自动回落，不影响学生保存和提交。
- `saveDraft`
  - 先按 Batch 31 规则写入数据库。
  - 数据库接受本次 revision 后，再写入 Redis。
  - 返回 `draftSource` 和 `cacheEnabled`。
- `startExam`、`heartbeat`、超时自动交卷、强制交卷
  - 读取草稿时比较 DB 与 Redis 的 `revision`。
  - Redis revision 更新时优先使用 Redis。
  - Redis 不可用或未启用时使用 DB 草稿。
- `finalizeAttempt`
  - 交卷成功后删除 DB 草稿并尝试删除 Redis 草稿。

## 前端改造
- 学生作答页接收 `draftSource`。
- 页面头部增加恢复来源提示：
  - `草稿恢复`：来自 DB。
  - `缓存恢复`：来自 Redis。
- 保存草稿成功后，如果后端返回 `draftSource`，前端同步更新提示。

## 设计边界
- 当前是“写穿缓存”：
  - 每次草稿保存仍同步写 DB。
  - Redis 用于快速恢复和后续演进，不牺牲当前数据安全。
- 下一阶段可演进为“Redis 高频写 + DB 定时刷盘”：
  - 需要后台 flush job。
  - 需要提交前强制 flush。
  - 需要 Redis 宕机时的降级告警。

## 三端协同价值
- 学生端：
  - 刷新/重进考试时可以优先恢复最新 Redis 草稿。
  - Redis 不可用时仍可从数据库恢复。
- 教师端：
  - 强制交卷和超时交卷会使用最新可用草稿。
- 管理员端：
  - 之后可在容量监控中观察 Redis 草稿缓存命中与回落情况。

## 后续建议
- Batch 33 建议补 Redis 草稿刷盘与监控：
  - 草稿保存改为 Redis 高频写。
  - 后台任务批量 flush 到 DB。
  - 提交前强制 flush 当前 attempt。
  - 管理员端增加草稿缓存健康与回落统计。
