# 162. 监考处置记录归属修复

## 背景

监考处置记录 `exam_monitor_action` 保存 `session_id`、`attempt_id`、`exam_id`、`user_id`。这些字段用于教师查看处置历史、导出处置记录、管理员审计强制交卷和警告动作。

如果历史数据里 action 的答卷、考试或学生字段与 `exam_monitor_session`、`exam_attempt` 不一致，监考处置会挂到错误考试或错误学生上。和阅卷、申诉日志一样，监考处置也需要一条明确的归属规则。

本批规则：

- `session_id` 是处置记录入口。
- 能通过 `session_id -> exam_monitor_session -> exam_attempt` 找到答卷时，以答卷所属考试和学生为准。
- 找不到会话或答卷时，保留原有 action 快照，避免迁移中断。

## 本批改动

- 启动迁移：按 `exam_monitor_action.session_id -> exam_monitor_session -> exam_attempt` 修正 `attempt_id`、`exam_id`、`user_id`。
- 写入路径：`insertMonitorAction` 写新处置前重新解析会话对应答卷归属。
- 导出与审计：现有监考处置列表和导出继续读取 `exam_monitor_action`，但数据来源被迁移和写入路径校正。
- 质量门禁：固定迁移必须修正监考处置归属，固定写入必须解析会话和答卷。

## 三端协同影响

- 学生端：收到警告或强制交卷通知时，关联的答卷更可信。
- 教师端：监考看板和处置历史不会因为旧快照把动作挂到错误学生或考试。
- 管理员端：全局监控审计、强制交卷追踪和导出处置记录有一致的归属依据。

## 验收点

- 历史 action 的 `attempt_id` 会与 `exam_monitor_session.attempt_id` 保持一致。
- 历史 action 的 `exam_id`、`user_id` 会与 `exam_attempt` 保持一致。
- 新写入 action 时，会先解析 `session_id` 对应的真实答卷归属。
- 监考处置列表和导出继续使用修正后的 action 数据。
