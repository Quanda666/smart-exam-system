# 301 通用操作日志精确定位与证据链接

## Summary

前几批已经把多个专项审计页签做成可按 ID 定位、可复制链接的证据链。通用操作日志仍只能按关键词、动作、对象和时间搜索，管理员在追踪用户管理、基础数据、题库、试卷等通用操作时，缺少“拿到一个日志 ID 直接打开”的能力。

本批为通用操作日志补齐精确定位和复制证据链接。

## Backend

- `GET /api/monitor/logs` 新增 `logId` 查询参数。
- `GET /api/monitor/logs/export` 新增 `logId` 查询参数。
- `MonitorService.appendOperationLogFilters(...)` 增加 `Long logId` 参数。
- 列表与 CSV 导出共用同一过滤口径：
  - `AND l.id = ?`

## Frontend

- `OperationLogQuery` 新增 `logId`。
- `SystemLog.vue` 的操作日志页签新增：
  - `Operation log ID` 输入框
  - `Log ID` 表格列
  - `Copy operation log ID`
  - `Copy operation log link`
- 新增深链：
  - `/monitor/logs?operationLogId=<id>`
  - `/monitor/logs?tab=operation&logId=<id>`

## Three-End Collaboration

- 管理员端：可以把任意通用操作日志作为可复核证据分享。
- 教师端/学生端：无直接 UI 变化，但用户、题库、试卷、考试等后台操作的责任追踪更完整。
- 审计协同：专项审计和通用操作日志拥有一致的 ID 定位和证据链接体验。

## Quality Gates

- 后端门禁检查 `logId` 参数、`AND l.id = ?` 过滤和导出口径。
- 前端门禁检查 API 查询参数、操作日志 ID 输入、ID 列、深链和剪贴板工具。

## Acceptance

- 管理员打开 `/monitor/logs?operationLogId=123` 会进入操作日志页签并按 ID 过滤。
- 管理员打开 `/monitor/logs?tab=operation&logId=123` 也会定位同一条日志。
- 操作日志 CSV 导出使用相同过滤条件。
- 操作日志行可复制原始日志 ID 和统一审计链接。
