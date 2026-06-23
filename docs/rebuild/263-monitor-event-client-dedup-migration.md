# 263. 监考事件客户端幂等键迁移

## 背景

学生端监考事件采用批量上报，前端会为每条事件生成 `clientEventId`。后端通过 `cheat_event(attempt_id, client_event_id)` 唯一键和 `INSERT IGNORE` 实现幂等，避免网络重试、页面恢复、提交前强制 flush 导致同一事件被重复计入风险分。

新库 schema 已有 `uk_cheat_attempt_client_event`，但旧库迁移原来是补列后直接加唯一键。如果历史数据中已经存在相同 `(attempt_id, client_event_id)` 的重复事件，迁移可能失败；即使没失败，重复事件也会污染监考会话的 `event_count` 和 `risk_score` 聚合。

## 本批改动

- 新增 `deduplicateCheatEventsBeforeClientEventUniqueIndex`。
- 在 `ensureCheatEventBatchColumns` 中，补齐 `client_event_id`、`client_event_time` 后，先执行重复事件清理，再添加唯一键。
- 清理策略：
  - 只处理 `client_event_id IS NOT NULL` 的客户端幂等事件；
  - 按 `(attempt_id, client_event_id)` 分组；
  - 保留最早客户端事件时间、最早服务端记录时间、最小 id 的事件；
  - 删除其余重复事件。
- 质量门新增检查，确保后续不会跳过去重直接添加唯一键。

## 三端协同影响

- 学生端：监考事件重试上报仍然是安全的，同一客户端事件不会重复入库。
- 教师端：实时监考看板的事件数和风险分不会被历史重复事件放大。
- 管理员端：监考事件导出、审计和风险统计按客户端事件身份保持幂等。

## 验收

- 任意数据库启动后，`cheat_event` 必须存在 `uk_cheat_attempt_client_event(attempt_id, client_event_id)`。
- 历史重复客户端事件会在添加唯一键前清理。
- `client_event_id IS NULL` 的历史事件不参与该唯一身份去重，避免误删无客户端幂等信息的旧审计记录。
- `MonitorService` 继续通过 `INSERT IGNORE` 依赖该唯一键实现批量上报幂等。
