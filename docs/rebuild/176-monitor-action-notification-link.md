# 176. 监考处置通知记录关联

## 背景

第 175 批已经让监考处置记录保存 `notification_sent`，能判断提醒或强制交卷通知是否发出。但管理员后续做全局审计时，还需要从处置记录追溯到具体站内通知记录，否则只能看到布尔结果，无法核对通知内容、接收人、已读状态和创建时间。

## 本批改动

- `notification` 写入支持返回自增主键：
  - 新增 `NotificationService.sendAndReturnId(...)`。
  - 原有 `send(...)` 保持兼容，内部复用新方法。
- `exam_monitor_action` 新增 `notification_id`：
  - 新库建表包含字段和 `idx_monitor_action_notification` 索引。
  - 旧库启动迁移自动补列和补索引。
- 监考处置发送通知后回写：
  - `notification_sent = 1`
  - `notification_id = notification.id`
- 处置列表、强制交卷返回体、处置 CSV 导出都包含通知 ID。
- 教师端处置时间线显示 `Notification #<id>`，无通知动作显示 `No notification`。
- 质量门增加通知主键回传、数据库字段/索引、后端查询/导出、前端类型/展示断言。

## 三端协同影响

- 学生端：考试通知接收逻辑不变。
- 教师端：处置历史能看到通知编号，确认提醒或强制交卷通知可追溯。
- 管理员端：后续可把监考处置审计和通知审计按 `notification_id` 串联，核验通知内容、接收人和已读状态。

## 验收点

- 创建 `WARN` 处置后，返回体包含 `notificationSent = true` 和非空 `notificationId`。
- 首次强制交卷记录处置后，处置记录保存对应 `notification_id`。
- `ACKNOWLEDGE` / `NOTE` 不发送通知，`notification_id` 为空。
- 处置 CSV 导出包含 `Notification Sent` 和 `Notification ID` 两列。
- 旧库缺少 `notification_id` 字段和索引时，启动迁移能自动补齐。
