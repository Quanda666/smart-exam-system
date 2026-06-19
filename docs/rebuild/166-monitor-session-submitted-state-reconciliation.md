# 166. 监考会话提交态持久化校准

## 背景

上一批已经把监考会话列表改为按 `exam_attempt` 的归属加载，并在展示层用答卷状态推导已提交状态。但真实系统不能只依赖列表查询里的临时 `CASE`：

- 导出、审计、后台任务、告警聚合可能直接读取 `exam_monitor_session.status`。
- 历史数据或并发心跳可能让已交卷答卷对应的会话仍停留在 `ONLINE/OFFLINE`。
- 监考状态一旦与答卷终态分叉，教师端看板、管理员审计和后续统计会出现口径不一致。

因此本批把 `SUBMITTED` 从展示推导升级为持久状态约束。

## 本批改动

- 启动迁移新增 `ensureMonitorSessionSubmittedStateConsistency`：
  - 按 `exam_monitor_session.attempt_id -> exam_attempt.id` 关联。
  - 当 `exam_attempt.status >= 2` 且会话不是 `SUBMITTED` 时，统一修正为 `SUBMITTED`。
- `ExamService.touchMonitorSession` 改为保留已提交状态：
  - 心跳或重新进入考试只能更新未提交会话。
  - 已经是 `SUBMITTED` 的会话不会被降级为 `ONLINE`。
- `ExamService.markMonitorSessionSubmitted` 在重复键更新时同步刷新 `exam_id/user_id`：
  - 避免旧 session 快照污染后续审计。
  - 与前几批“按答卷归属校准监考数据”的原则保持一致。
- 质量门新增检查：
  - 提交态迁移必须存在。
  - 运行时写入必须保留 `SUBMITTED`。
  - 提交标记必须同步刷新会话归属字段。

## 三端协同影响

- 学生端：交卷后即使还有延迟心跳或重试请求，也不会把监考会话重新写成在线。
- 教师端：监考看板和导出的会话状态与答卷终态一致，避免“已交卷但仍在线”的误判。
- 管理员端：审计、告警聚合、后续离线任务可以直接信任 `exam_monitor_session.status` 的提交态。

## 验收点

- 已提交、已阅卷、已发布成绩等 `exam_attempt.status >= 2` 的答卷，对应会话持久状态为 `SUBMITTED`。
- `SUBMITTED` 会话不会被学生端心跳覆盖成 `ONLINE/OFFLINE`。
- 提交或重放提交结果时，残留草稿被清理，监考会话被标记提交。
- 历史脏数据可在应用启动迁移中自动校准。
