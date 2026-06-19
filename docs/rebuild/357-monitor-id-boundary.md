# Step 357 - Monitor ID Boundary

## 背景

监考闭环连接学生端事件采集、教师端实时看板、教师处置动作、强制交卷、动作导出和管理员审计。上一阶段已经补齐了更多监考事件类型和事件风险分，但核心路径参数仍需要继续收紧。

如果 `attemptId`、`examId`、`sessionId` 非正数仍进入查询，会把参数错误混进“考试不存在”“答卷不存在”“无权限”等业务错误里。正式在线考试系统需要把这些边界放在服务层，确保页面绕过、通知深链、导出接口和内部调用都遵守同一套规则。

## 本次改动

- `CheatEventRequest.attemptId` 增加 `@Positive`，学生端批量或单条上报时必须提供正数答卷 ID。
- `MonitorService.requireAttemptAccess` 在查答卷监考事件前调用 `requireAttemptId`。
- `MonitorService.requireExamMonitorAccess` 在查考试监考会话前调用 `requireExamId`。
- `MonitorService.loadSessionForAction` 在查监考会话动作前调用 `requireSessionId`。
- 新增 `requireExamId` 和 `requireSessionId`，明确拒绝 `null`、`0`、负数。
- 质量门禁新增结构断言，要求监考 DTO 和服务层 ID 边界持续存在。

## 协同意义

- 学生端：事件上报不会用非法答卷 ID 触发监考数据写入或噪声查询。
- 教师端：会话列表、事件查看、事件导出、提醒学生、规则提醒、强制交卷和处置动作导出都围绕有效业务对象展开。
- 管理员端：监考审计和运维排查可以区分参数错误与真实业务不存在/越权，减少误判。

## 验收点

- `attemptId <= 0` 不能进入监考事件查询、导出或学生事件写入链路。
- `examId <= 0` 不能进入考试监考会话查询或导出链路。
- `sessionId <= 0` 不能进入监考动作、强制交卷、动作列表或动作导出链路。
- 合法 ID 的原有权限校验、授课范围校验、通知发送、风险分和导出行为不变。
