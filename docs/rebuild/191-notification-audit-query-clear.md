# 191. 通知审计深链 ID 清理

## 背景

第 190 批让管理员日志页在已挂载状态下响应新的 `notificationId` query。仍有一个边角场景：管理员从 `/monitor/logs?notificationId=123` 跳转到 `/monitor/logs?tab=notification` 时，URL 已经不再指定通知 ID，但页面可能保留旧的 `123` 筛选条件。

## 本批改动

- `hydrateLogRouteQuery` 区分 `notificationId` query 是否存在。
- 当 URL 显式进入 `tab=notification` 但没有 `notificationId` 时，清空旧的通知 ID 筛选。
- 清空通知 ID 时重置通知审计分页到第一页。
- 保留 `notificationId=Notification%20%23123` 的规范化行为。
- 质量门禁增加 query key 判断、清空旧 ID 和分页重置断言。

## 三端协同影响

- 教师端：复制的通知审计链接行为不变。
- 管理员端：在不同通知审计链接和普通通知审计页之间切换时，不会残留旧筛选。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- 从 `/monitor/logs?notificationId=123` 跳到 `/monitor/logs?tab=notification` 后，`Notification ID` 输入框清空。
- 清空后通知审计分页回到第一页。
- 从 `/monitor/logs?notificationId=Notification%20%23123` 打开时仍预填为 `123`。
- 未显式切换 URL query 时，管理员手动输入的筛选条件不被自动清空。
