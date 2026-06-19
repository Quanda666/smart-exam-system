# 178. 监考处置通知已读状态回显

## 背景

第 176、177 批已经把监考处置和具体通知记录打通：教师能看到 `Notification #id`，管理员能按通知 ID 精确查询。但教师在监考处置时间线里仍然看不到学生是否已读该提醒，无法快速判断提醒是否触达。

## 本批改动

- 监考处置查询关联 `notification` 表：
  - 返回 `notificationRead`。
  - 返回 `notificationCreatedAt`。
- 处置 CSV 导出增加：
  - `Notification Read`
  - `Notification Created At`
- 教师端处置时间线展示：
  - `Notification #<id> · Read`
  - `Notification #<id> · Unread`
  - 无通知动作仍显示 `No notification`。
- 质量门增加后端 join、导出列、前端类型和已读/未读展示断言。

## 三端协同影响

- 学生端：通知已读状态仍由学生查看通知时更新，不改变考试流程。
- 教师端：可以直接判断监考提醒是否被学生读取。
- 管理员端：导出处置记录时可同时拿到通知 ID、已读状态和创建时间，再按通知审计页精确追查。

## 验收点

- 已发送通知的监考处置返回 `notificationId`、`notificationRead`、`notificationCreatedAt`。
- 学生未读时教师端显示 `Unread`。
- 学生已读后教师端刷新处置记录显示 `Read`。
- 处置导出包含通知已读状态和通知创建时间。
