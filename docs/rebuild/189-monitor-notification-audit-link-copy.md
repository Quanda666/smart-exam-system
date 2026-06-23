# 189. 教师端复制通知审计链接

## 背景

第 188 批让管理员日志页支持 `/monitor/logs?notificationId=...` 深链预填。教师端此前只能复制通知 ID，管理员仍需要手动进入日志页并粘贴 ID。

## 本批改动

- 共享剪贴板工具新增 `buildNotificationAuditDeepLink`。
- 共享剪贴板工具新增 `copyNotificationAuditLinkToClipboard`。
- 教师端监考会话列表的最新通知增加 `Copy link`。
- 教师端处置时间线的通知处置记录增加 `Copy link`。
- 复制的链接格式为 `/monitor/logs?notificationId=<id>`，带当前站点 origin。
- 质量门禁增加深链生成、URL 编码、列表按钮和时间线按钮断言。

## 三端协同影响

- 教师端：可直接复制通知审计链接给管理员。
- 管理员端：打开链接后自动进入 `Notification Audit` 并预填通知 ID。
- 学生端：不改变通知读取、考试作答或监考事件上报流程。

## 验收点

- 教师端存在通知 ID 的最新处置记录显示 `Copy link`。
- 教师端处置时间线存在通知 ID 的记录显示 `Copy link`。
- 复制链接后打开，可进入管理员日志页并预填通知 ID。
- 既有 `Copy audit ID` 行为不变。
