# 163. 监考会话归属修复

## 背景

第 162 批修复了 `exam_monitor_action` 的归属。继续向上追溯后，监考处置、教师实时看板、管理员监控统计都依赖 `exam_monitor_session` 聚合会话。

`exam_monitor_session.attempt_id` 是会话主锚点；`exam_id` 和 `user_id` 应从 `exam_attempt` 推导。如果历史 session 的考试或学生快照错误，后续 action 即使修正，也仍可能从脏 session 继承错误上下文。

## 本批改动

- 启动迁移：按 `exam_monitor_session.attempt_id -> exam_attempt` 修正 `exam_id`、`user_id`。
- 运行时写入：`upsertMonitorSession` 在重复上报时同步刷新 `exam_id`、`user_id`。
- 顺序保证：session 归属修正在 action 归属修正之前执行，避免 action 读取脏 session。
- 质量门禁：固定迁移必须修正 session 归属，固定 upsert 必须刷新 `exam_id`、`user_id`。

## 三端协同影响

- 学生端：监考事件上报仍保持幂等，重复上报不会污染会话归属。
- 教师端：监考看板按真实考试和学生聚合风险、状态和处置历史。
- 管理员端：全局监控统计和审计导出能基于修正后的 session 数据。

## 验收点

- 历史 session 的 `exam_id` 会与 `exam_attempt.exam_id` 保持一致。
- 历史 session 的 `user_id` 会与 `exam_attempt.user_id` 保持一致。
- 后续重复上报同一 attempt 的监考事件时，session 会刷新考试和学生归属。
- action 归属修正在 session 归属修正之后执行。
