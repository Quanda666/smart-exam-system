# 168. 监考会话事件聚合校准

## 背景

第 167 批让 `cheat_event` 具备 `attempt_id/exam_id/user_id` 归属快照，监考原始事件已经可以作为审计事实源。但教师端监考看板读取的是 `exam_monitor_session` 上的聚合字段：

- `event_count`
- `risk_score`
- `last_event_at`
- `last_event_type`

这些字段主要由学生端上报事件时增量维护。旧库迁移、历史补传、重复修复或手工补数据后，会话聚合字段可能与原始事件不一致。本批把会话聚合改成可从 `cheat_event` 自愈。

## 本批改动

- 启动迁移新增 `ensureMonitorSessionEventAggregateConsistency`。
- 对有监考事件但缺少会话的答卷，自动补建 `exam_monitor_session`：
  - 归属来自 `exam_attempt`。
  - 已提交答卷会话状态为 `SUBMITTED`。
  - 未提交答卷会话状态保守设为 `OFFLINE`。
- 对已有会话，从 `cheat_event` 重算：
  - 事件数。
  - 风险分。
  - 最后事件时间。
  - 最后事件类型。
- 最后事件按 `event_time DESC, id DESC` 选择，和事件列表排序口径保持一致。
- 质量门新增约束，固定“原始事件 -> 会话聚合”的自愈链路。

## 风险分口径

迁移中的风险权重与运行时 `MonitorService.riskWeight` 保持一致：

- `PASTE`: 8
- `COPY`: 6
- `FULLSCREEN_EXIT`: 5
- `NETWORK_OFFLINE`: 4
- `VISIBILITY_HIDDEN`: 3
- `WINDOW_BLUR`: 3
- `HEARTBEAT_FAILED`: 2
- `NETWORK_ONLINE`: 1

## 三端协同影响

- 学生端：上报协议不变。
- 教师端：监考看板的事件数、风险分和最后事件更可信，可覆盖历史补传和迁移数据。
- 管理员端：审计导出、风险统计和后续告警任务可以把 `cheat_event` 作为事实源，把 `exam_monitor_session` 作为可重建聚合视图。

## 验收点

- 有 `cheat_event` 但无 `exam_monitor_session` 的旧数据，启动后会补建会话。
- 会话 `event_count/risk_score/last_event_at/last_event_type` 可从原始事件重算。
- 已提交答卷的补建会话状态为 `SUBMITTED`。
- 最后事件类型与事件列表的最新排序一致。
