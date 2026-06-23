# 173. 监考会话筛选导出

## 背景

第 172 批让教师端监考会话看板支持按状态和风险分筛选。但导出按钮仍然调用全量考试会话导出，导致“当前看到的列表”和“导出的 CSV”口径不一致。

本批让会话导出支持同样的状态/风险筛选，教师或管理员可以直接导出当前关注的在线、离线、已交卷或高风险会话集合。

## 本批改动

- `GET /api/monitor/exams/{examId}/sessions/export` 新增参数：
  - `sessionStatus`
  - `minRiskScore`
- 后端导出新增 `filterMonitorSessionsForExport`：
  - 复用 `listExamMonitorSessions` 的权限、归属和状态推导结果。
  - 按 `sessionStatus` 过滤 `ONLINE/OFFLINE/SUBMITTED`。
  - 按 `minRiskScore` 过滤风险分。
- `sessionStatus` 支持空值和 `ALL`，非法状态会被拒绝。
- `minRiskScore` 不能为负数。
- 前端 `exportExamMonitorSessions` 支持 `MonitorSessionExportQuery`。
- 教师端导出按钮调用 `buildSessionExportQuery()`，导出当前筛选条件。
- 质量门固定后端参数、服务层校验、前端 query string 和导出调用。

## 三端协同影响

- 学生端：无变化。
- 教师端：看板筛选和导出 CSV 口径一致。
- 管理员端：后续全局监控页可复用同样的导出参数设计。

## 验收点

- 当前筛选为 `Submitted` 时，导出只包含已交卷会话。
- 当前筛选为高风险阈值时，导出只包含风险分达到阈值的会话。
- 非法 `sessionStatus` 被拒绝。
- 负数 `minRiskScore` 被拒绝。
- 不传筛选参数时仍导出全量有权限会话。
