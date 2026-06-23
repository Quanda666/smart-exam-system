# 35. 草稿缓存告警与运维可视化

## 目标

- 把 Redis 草稿缓存从“有指标”升级为“可判断风险”。
- 管理员能在系统配置页看到 dirty 草稿是否超阈值、最近是否刷盘、Redis 是否可用。
- 阈值进入 `system_config`，便于不同考试规模按生产容量调整。

## 后端改造

- `ExamDraftCacheService` 新增最近刷盘批次指标：
  - `lastFlushAtEpochMillis`
  - `lastFlushChecked`
  - `lastFlushFlushed`
  - `lastFlushSkipped`
  - `lastFlushCleaned`
- 定时刷盘和提交前单 attempt 刷盘都会记录最近刷盘信息。
- `GET /api/exams/draft-cache/status` 新增告警字段：
  - `dirtyWarningThreshold`
  - `dirtyHighThreshold`
  - `errorWarningThreshold`
  - `staleFlushWarningSeconds`
  - `alertLevel`
  - `alertMessage`
- 告警口径：
  - `DISABLED`：Redis 草稿缓存关闭。
  - `HIGH`：写回模式已开但 Redis 不可用，或 dirty 数达到高风险阈值。
  - `WARN`：Redis 不可用、dirty 数达到预警阈值、错误数达到阈值、写回模式下 dirty 草稿长时间未刷盘。
  - `OK`：当前缓存状态正常。

## 系统配置

新增默认配置：

- `exam.draftCacheDirtyWarningThreshold=100`
- `exam.draftCacheDirtyHighThreshold=500`
- `exam.draftCacheErrorWarningThreshold=5`
- `exam.draftCacheStaleFlushWarningSeconds=300`

旧库通过启动自愈迁移 `INSERT IGNORE` 补齐，不覆盖已存在配置。

## 前端改造

- 系统配置页“草稿缓存”状态卡新增：
  - 写回/写穿模式。
  - 正常/预警/高风险/关闭状态。
  - dirty 数与高风险阈值。
  - Redis 错误计数。
  - 最近刷盘时间。
  - 最近刷盘批次 flushed/checked。

## 三端协同

- 学生端：继续只负责保存草稿和提交 token，不直接感知运维阈值。
- 教师端：阅卷和监考仍读取 MySQL 最终状态，避免直接依赖 Redis。
- 管理员端：通过系统配置页观察 Redis 草稿缓存积压，必要时调整阈值或关闭写回模式回退到写穿。

## 下一批建议

- 增加作答链路 E2E 脚本，覆盖草稿保存、提交前刷盘、重复提交响应回放。
- 为草稿缓存告警增加操作日志或通知策略，便于考试期间主动提醒管理员。
