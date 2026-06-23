# 179. 监考会话最近处置通知状态

## 背景

第 178 批已经在处置详情中显示通知已读状态，但教师在监考会话列表中仍只能看到最近处置动作、处置人、时间和备注。大考并发监考时，教师需要在列表层面快速判断最近一次提醒是否已发送、学生是否已读，而不是逐个打开详情抽屉。

## 本批改动

- `listExamMonitorSessions` 增加最近处置通知字段：
  - `latestActionNotificationSent`
  - `latestActionNotificationId`
  - `latestActionNotificationRead`
  - `latestActionNotificationCreatedAt`
- 监考会话导出增加：
  - `Latest Notification Sent`
  - `Latest Notification ID`
  - `Latest Notification Read`
  - `Latest Notification Created At`
- 教师端会话列表“最近处置”列显示最近通知状态：
  - `Notification #<id> · Read`
  - `Notification #<id> · Unread`
  - `Notification not sent`
  - `No notification`
- 质量门增加后端字段、会话导出列和前端列表展示断言。

## 三端协同影响

- 教师端：可以在会话列表直接扫到最近提醒是否触达。
- 学生端：通知已读仍由学生查看通知时更新，不改变考试流程。
- 管理员端：导出会话概览时可以看到最近处置通知编号和已读状态，必要时继续到通知审计页精确查询。

## 验收点

- 有最近 `WARN` 或 `FORCE_SUBMIT` 且通知发送成功的会话，列表显示通知编号和已读/未读。
- 最近处置为 `ACKNOWLEDGE` 或 `NOTE` 时，列表显示 `No notification`。
- 会话 CSV 导出包含最近通知发送状态、通知 ID、已读状态和创建时间。
