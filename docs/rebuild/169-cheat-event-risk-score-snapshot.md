# 169. 监考事件风险分快照

## 背景

第 168 批已经把 `exam_monitor_session` 的聚合字段改成可从 `cheat_event` 重建。但风险权重仍然同时存在于运行时 Java 和迁移 SQL 中，长期看容易出现口径漂移。

本批把单条监考事件的风险分持久化到 `cheat_event.risk_score`，让原始事件本身具备完整审计语义。会话看板不再重新根据事件类型推算风险，而是直接汇总事件风险分快照。

## 本批改动

- `cheat_event` 新增 `risk_score INT NOT NULL DEFAULT 0`。
- 新增 `idx_cheat_exam_risk_time (exam_id, risk_score, event_time)`，支持按考试筛选高风险事件。
- 启动迁移新增 `ensureCheatEventRiskScoreConsistency`：
  - 旧事件按事件类型回填风险分。
  - 风险分与当前事件类型权重不一致时自动校准。
- 学生端上报事件时，后端计算 `riskScore` 并写入 `cheat_event`。
- 监考事件查询返回 `riskScore`。
- 前端 `MonitorEvent` 类型补充 `examId/userId/riskScore`。
- 会话聚合迁移改为 `SUM(ce.risk_score)`，不再重复硬算事件类型权重。
- 质量门固定以上约束。

## 风险分口径

- `PASTE`: 8
- `COPY`: 6
- `FULLSCREEN_EXIT`: 5
- `NETWORK_OFFLINE`: 4
- `VISIBILITY_HIDDEN`: 3
- `WINDOW_BLUR`: 3
- `HEARTBEAT_FAILED`: 2
- `NETWORK_ONLINE`: 1

风险分只代表过程风险记录，不自动判定作弊或违规。

## 三端协同影响

- 学生端：上报协议不变，不需要信任客户端提供风险分。
- 教师端：查看监考事件时可以看到后端计算的单条风险分，会话风险分来自事件分值汇总。
- 管理员端：后续高风险事件检索、考试级风险统计、审计导出都有稳定的事件级分值依据。

## 验收点

- 新写入的 `cheat_event` 包含 `risk_score`。
- 历史 `cheat_event` 可按事件类型回填风险分。
- `exam_monitor_session.risk_score` 可由 `SUM(cheat_event.risk_score)` 重建。
- 监考事件 API 类型包含 `examId/userId/riskScore`。
