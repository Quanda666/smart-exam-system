# 262. 监考会话唯一身份迁移

## 背景

学生进入考试、心跳、提交、教师强制交卷都会写 `exam_monitor_session`。服务层使用 `INSERT ... ON DUPLICATE KEY UPDATE` 来维护“一次作答一条监考会话”，但这个前提是数据库里必须存在 `uk_monitor_attempt(attempt_id)`。

新库 schema 已经有该唯一键，旧库如果早期创建过监考会话表，启动迁移只建表不补唯一键，就可能留下同一 attempt 多条监考会话。这样会影响教师实时看板、管理员审计、监考处置记录和提交状态同步。

## 本批改动

- `ensureMonitorSessionTable` 增加旧表补列：
  - `status`
  - `last_heartbeat_at`
  - `last_event_at`
  - `event_count`
  - `risk_score`
  - `last_event_type`
  - `created_at`
  - `updated_at`
- 补齐旧表普通索引：
  - `idx_monitor_exam_status`
  - `idx_monitor_user`
  - `idx_monitor_risk`
- 新增 `ensureMonitorSessionUniqueIdentity`，在 `exam_monitor_action` 表可用后执行。
- 新增 `deduplicateMonitorSessionsBeforeUniqueIndex`：
  - 按 `attempt_id` 查找重复监考会话；
  - 优先保留 `SUBMITTED`，其次保留 `ONLINE`，再按心跳、事件数、风险值、更新时间选保留行；
  - 合并保留行的心跳、事件时间、事件数、风险值和创建时间；
  - 将 `exam_monitor_action.session_id` 从重复会话转挂到保留会话；
  - 删除重复会话后添加 `uk_monitor_attempt(attempt_id)`。
- 质量门新增检查，防止监考会话唯一身份迁移被回退。

## 三端协同影响

- 学生端：进入考试、心跳、交卷都只维护同一个监考会话，不会生成并行在线状态。
- 教师端：实时监考看板和强制交卷操作能稳定定位 attempt 的唯一监考会话。
- 管理员端：监考审计、风险聚合、处置记录导出不再因重复会话产生重复统计。

## 验收

- 任意数据库启动后，`exam_monitor_session` 必须存在 `uk_monitor_attempt`。
- 历史重复会话会在加唯一键前归并。
- 监考处置记录必须转挂到保留会话，不应丢失。
- `touchMonitorSession` 与 `markMonitorSessionSubmitted` 的 upsert 必须依赖该唯一键稳定工作。
