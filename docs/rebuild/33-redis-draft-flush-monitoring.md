# 33. Redis 草稿刷盘与监控

## 目标
- 在 Batch 32 的 Redis 写穿缓存基础上，补齐可选写回模式。
- 增加后台刷盘任务，把 Redis dirty 草稿批量落回数据库。
- 增加管理员可见的草稿缓存运行状态。

## 后端改造
- `ExamDraftCacheService` 增强：
  - 草稿缓存增加 `dirty` 标记。
  - 支持扫描 dirty 草稿。
  - 统计 `reads`、`hits`、`writes`、`deletes`、`errors`、`flushSuccess`、`flushSkipped`、`dirtyCount`。
- 新增写回配置：
  - `exam.draftRedisWriteBackEnabled`：默认 `false`。
  - `exam.draftRedisFlushBatchSize`：默认 `200`。
- `saveDraft`：
  - 默认仍为写穿模式：DB 接受 revision 后写 Redis clean cache。
  - 写回模式开启且 Redis 可用时：只写 Redis dirty cache，并更新 attempt 草稿元信息。
  - Redis 不可用时自动回到写穿 DB。
- 新增 `ExamDraftFlushScheduler`：
  - 默认启动后 180 秒开始。
  - 默认每 60 秒刷盘一次。
  - 使用 `JobLockService`，多实例只允许一个节点执行。
  - 跳过已提交、不存在、或 revision 落后的缓存。
- 新增管理员接口：
  - `GET /api/exams/draft-cache/status`
  - 返回 Redis 草稿缓存开关、可用性、dirty 数、刷盘次数、错误数、活跃 attempt 和 DB 草稿数。

## 前端改造
- 系统配置页新增“草稿缓存”状态卡：
  - 未启用 / 不可用 / 写穿 / 写回。
  - 显示 dirty、flush、errors。
- 系统配置页保存配置后会刷新草稿缓存状态。

## 运维配置
- 应用属性：
  - `EXAM_DRAFT_REDIS_ENABLED=false`
  - `EXAM_DRAFT_REDIS_TTL_SECONDS=21600`
  - `EXAM_DRAFT_FLUSH_DELAY_MS=60000`
  - `EXAM_DRAFT_FLUSH_INITIAL_DELAY_MS=180000`
- 系统配置：
  - `exam.draftRedisWriteBackEnabled=false`
  - `exam.draftRedisFlushBatchSize=200`

## 设计边界
- 写回模式是可选增强，默认不启用。
- 写回模式需要 Redis 稳定可用；否则自动回落 DB 写穿。
- 刷盘任务按 revision 防旧草稿覆盖新草稿。
- 提交、超时提交、强制提交都会通过 `loadBestDraftState` 读取 Redis/DB 中 revision 最新的草稿。

## 后续建议
- Batch 34 可补提交前强制 flush 当前 attempt：
  - 如果 Redis 中有 dirty 草稿，提交前先落 DB。
  - 增加提交幂等响应缓存，支持完整 response replay。
  - 管理员端增加草稿缓存告警阈值。
