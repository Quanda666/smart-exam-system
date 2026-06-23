# 190. 通知审计深链 Query 变化响应

## 背景

第 188 批支持首次打开 `/monitor/logs?notificationId=...` 时预填通知审计 ID。实际使用中，管理员可能已经停留在日志页，再打开或跳转到另一个通知审计链接。由于组件不会重新挂载，仅靠初始化 hydrate 不足以覆盖这个场景。

## 本批改动

- `SystemLog` 监听 `route.fullPath` 变化。
- `hydrateLogRouteQuery` 返回是否实际改变了页签或筛选条件。
- 当 query 改变但仍停留在同一页签时，主动重新加载数据。
- 当 query 导致页签切换时，复用既有 `activeTab` watcher 加载，避免重复请求。
- 新增 `extractNotificationAuditId`，让输入框规范化和 URL query 规范化复用同一规则。
- `notificationId` 深链切换时重置通知审计分页到第一页。

## 三端协同影响

- 教师端：连续发送不同通知审计链接时，管理员可以在同一日志页面内连续定位。
- 管理员端：不需要刷新页面即可响应新的 `notificationId` query。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- 已在 `/monitor/logs?notificationId=123` 时，跳转到 `/monitor/logs?notificationId=456` 会重新查询 `456`。
- 已在其它日志页签时，跳转到带 `notificationId` 的链接会切到 `Notification Audit`。
- URL 中 `Notification #123` 仍会规范化为 `123`。
- 未知 `tab` 不会破坏当前日志页。
