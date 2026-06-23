# 175. 监考处置通知发送审计

## 背景

监考处置动作中，`WARN` 和 `FORCE_SUBMIT` 会触发学生站内通知。此前接口只在创建动作时临时返回 `notificationSent`，但处置记录列表和导出无法追溯通知是否真的发出，导致教师端和管理员审计端只能看到“做了处置”，看不到“学生是否收到通知”。

## 本批改动

- `exam_monitor_action` 新增 `notification_sent` 字段：
  - 新库建表默认包含该字段。
  - 旧库启动迁移自动补列。
  - 历史记录默认 `0`，不反推，避免制造不可信审计结论。
- `MonitorService` 在发送学生通知后回写处置记录：
  - `WARN` 成功发送提醒后标记 `notification_sent = 1`。
  - `FORCE_SUBMIT` 成功发送强制交卷通知后标记 `notification_sent = 1`。
  - `ACKNOWLEDGE` 和 `NOTE` 不发送通知，保持 `0`。
- 处置列表、最近强制交卷处置读取、CSV 导出都返回通知发送结果。
- 教师端处置时间线显示通知状态：
  - `Notification sent`
  - `Notification not sent`
  - `No notification`
- 质量门增加数据库字段、迁移、后端回写/导出和前端展示断言。

## 三端协同影响

- 学生端：收到通知的事实可被处置记录追溯，不改变学生作答流程。
- 教师端：查看某个监考会话处置历史时，可以区分“仅记录关注”和“已向学生发送通知”。
- 管理员端：后续全局监考审计、通知审计可以把处置记录和站内通知投递结果对齐。

## 验收点

- 创建 `WARN` 动作且通知发送成功后，处置记录 `notificationSent` 为 true/1。
- 创建 `ACKNOWLEDGE` 或 `NOTE` 后，处置记录显示 `No notification`。
- 强制交卷动作若首次记录并发送通知，导出处置 CSV 包含 `Notification Sent = YES`。
- 旧库缺少 `notification_sent` 字段时，启动迁移能自动补齐。
