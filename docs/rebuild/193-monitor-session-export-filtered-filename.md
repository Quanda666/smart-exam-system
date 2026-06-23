# 193. 监考会话导出文件名携带筛选摘要

## 背景

监考会话导出已经支持按状态、风险分和最新通知状态过滤。此前导出的 CSV 文件名只包含考试名和日期，后续留档时难以判断这份文件对应的是全量会话、未读通知会话，还是高风险会话。

## 本批改动

- 前端 `exportExamMonitorSessions` 在存在筛选条件时使用可追溯文件名。
- 文件名包含筛选摘要：
  - `status_<状态>`
  - `risk_<最低风险分>`
  - `notice_<最新通知状态>`
- 新增 `monitorSessionExportFileName`。
- 新增 `monitorSessionExportFilterParts`。
- 新增 `safeFileNamePart`，清理文件名中的空白和非法字符。
- 未使用筛选条件时保留原有默认文件名，降低无关行为变化。
- 质量门禁增加文件名 helper 和三类筛选摘要断言。

## 三端协同影响

- 教师端：导出监考会话后，文件名可直接反映当前筛选条件。
- 管理员端：教师提交 CSV 留档时，可以从文件名快速判断导出范围。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- `Latest notification = UNREAD` 时导出文件名包含 `notice_UNREAD`。
- `Risk >= 8` 时导出文件名包含 `risk_8`。
- `Status = ONLINE` 时导出文件名包含 `status_ONLINE`。
- 无筛选导出仍沿用既有默认命名。
