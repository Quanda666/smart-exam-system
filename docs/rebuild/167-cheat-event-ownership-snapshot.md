# 167. 监考事件归属快照

## 背景

监考事件是考试过程审计的原始证据。此前 `cheat_event` 只保存 `attempt_id`，考试和学生归属需要每次通过 `exam_attempt` 临时推导。这样在列表导出阶段还能工作，但对真实系统有几个问题：

- 后续按考试、学生聚合风险时必须频繁联表。
- 审计表自身缺少归属快照，不利于定位历史脏数据。
- 如果未来做异步告警、离线报表或归档，原始事件无法直接按考试/学生分区查询。

本批把监考事件也纳入“审计记录持有归属快照，启动迁移可校准”的规则。

## 本批改动

- `cheat_event` 新增 `exam_id`、`user_id`。
- 新库 schema 增加：
  - `idx_cheat_exam_time (exam_id, event_time)`
  - `idx_cheat_user_time (user_id, event_time)`
- 启动迁移新增 `ensureCheatEventOwnershipConsistency`：
  - 旧库自动补 `exam_id/user_id` 列。
  - 从 `exam_attempt` 回填并修正事件归属。
  - 补齐按考试和学生查询的时间索引。
- 新监考事件写入时同步保存 `attempt_id/exam_id/user_id`。
- 监考事件查询返回 `examId/userId`，方便前端审计视图和后续导出复用。
- 质量门新增约束，防止后续改动让监考事件重新退回到只有 `attempt_id` 的弱归属状态。

## 三端协同影响

- 学生端：上报协议不变，仍只需要提交答卷 ID 和事件信息。
- 教师端：按考试查看监考事件和风险聚合时，后端可直接使用事件归属快照。
- 管理员端：监考审计、异常追踪、离线统计具备更稳定的数据基础。

## 验收点

- 新写入的监考事件包含 `attempt_id/exam_id/user_id`。
- 旧库启动后可以从 `exam_attempt` 回填监考事件归属。
- 监考事件表具备按考试、按学生的时间索引。
- 查询监考事件时返回 `examId/userId`，不破坏原有事件字段。
