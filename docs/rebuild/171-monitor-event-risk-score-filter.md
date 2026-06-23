# 171. 监考事件风险分筛选

## 背景

第 170 批让单条监考事件风险分在明细和导出中可见。真实监考场景里，教师和管理员更常见的动作是快速定位高风险事件，而不是逐条翻看完整事件流。

本批为监考事件明细增加 `minRiskScore` 筛选，并让导出沿用同一组筛选条件。

## 本批改动

- 监考事件查询接口新增 `minRiskScore` 参数。
- 监考事件导出接口同步支持 `minRiskScore`。
- 后端过滤逻辑新增：
  - `risk_score >= ?`
  - `minRiskScore` 不能为负数。
- 前端 `MonitorEventQuery` 新增 `minRiskScore`。
- `monitorEventQueryString` 追加 `minRiskScore` 查询参数。
- 教师端事件筛选条新增风险筛选：
  - `All risk`
  - `Risk > 0`
  - `Risk >= warningThreshold`
  - `Risk >= highThreshold`
- 重置筛选时会清空风险筛选。
- 质量门固定后端参数、SQL 条件、前端 query 和 UI 控件。

## 三端协同影响

- 学生端：无变化，仍只上报事件本身。
- 教师端：查看某个学生监考事件时，可以快速筛出有风险、预警或高风险事件。
- 管理员端：导出监考事件时可以直接导出高风险子集，减少人工二次筛选。

## 验收点

- `/api/monitor/cheat-events/{attemptId}` 支持 `minRiskScore`。
- `/api/monitor/cheat-events/{attemptId}/export` 支持同样的风险筛选。
- 负数 `minRiskScore` 被拒绝。
- 前端筛选和导出使用同一个 `buildEventQuery()`。
- 重置筛选后事件类型、时间范围和风险分条件都被清空。
