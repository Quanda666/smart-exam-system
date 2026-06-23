# 177. 通知审计按通知 ID 精确查询

## 背景

第 176 批让监考处置记录保存并展示 `notification_id`。但管理员通知审计页此前只能按关键字、通知类型、关联类型、关联 ID、接收人和时间范围筛选，无法直接用处置记录里的 `Notification #id` 精确定位通知。

## 本批改动

- 通知审计列表接口新增 `notificationId` 查询参数。
- 通知审计导出接口新增 `notificationId` 查询参数。
- `NotificationService.appendNotificationAuditFilters` 支持 `n.id = ?` 精确过滤。
- 管理员端 `NotificationAuditQuery` 新增 `notificationId`。
- `SystemLog` 的 `Notification Audit` 工具栏新增 `Notification ID` 输入框。
- 查询、重置、导出均带上 `notificationId`。
- 质量门增加后端参数/过滤和前端 API/UI 断言。

## 三端协同影响

- 教师端：监考处置时间线显示 `Notification #<id>`。
- 管理员端：可在通知审计页直接输入该 ID，核验通知内容、接收人、已读状态、创建时间和关联答卷。
- 学生端：通知接收和考试流程不变。

## 验收点

- `/api/notifications/audit?notificationId=<id>` 只返回对应通知。
- `/api/notifications/audit/export?notificationId=<id>` 只导出对应通知。
- 管理员通知审计页可输入 `Notification ID` 后查询和导出。
- 重置筛选会清空 `Notification ID`。
