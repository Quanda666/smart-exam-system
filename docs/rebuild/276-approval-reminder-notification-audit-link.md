# 276. 审批提醒记录跳转通知审计

## 背景

第 275 步已经让审批超时提醒通知绑定 `APPROVAL_REMINDER / reminderLogId`。但管理员在审批提醒记录抽屉中仍不能直接查看这次提醒对应的通知投递结果，需要手动进入系统日志并填写关联类型和 ID。

## 本步改动

- 审批提醒记录中，已发送记录新增“通知审计”入口。
- 点击后跳转到 `/monitor/logs?tab=notification&relatedType=APPROVAL_REMINDER&relatedId=<reminderLogId>`。
- 系统日志的 Notification Audit 页支持从路由查询参数自动填充 `relatedType` 和 `relatedId`。
- 质量门检查跳转入口和通知审计路由水合逻辑。

## 三端协同价值

- 管理员端可以从审批提醒记录一键进入通知投递审计，核对每位管理员是否收到提醒。
- 教师端审批申请不受影响，但审批超时处理链路更容易追踪和复盘。
- 学生端考试通知仍按 `EXAM_ATTEMPT` 跟踪，审批提醒通知按 `APPROVAL_REMINDER` 跟踪，审计入口统一落在通知日志。

## 验收点

- 已发送的审批提醒记录显示通知审计入口。
- 跳转后系统日志自动打开 Notification Audit 标签。
- 通知审计自动填入 `relatedType = APPROVAL_REMINDER` 和对应提醒日志 ID。
- 查询结果可定位该次提醒产生的通知投递记录。
