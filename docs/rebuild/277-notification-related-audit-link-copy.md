# 277. 通知审计业务关联筛选链接复制

## 背景

通知审计已经支持按 `relatedType / relatedId` 筛选，也可以从审批提醒记录跳转到对应的通知审计。但在 Notification Audit 表格中，业务关联仍只是静态文本，管理员无法快速把“这次提醒、这场考试、这份答卷对应的通知投递记录”分享给其他人复核。

## 本步改动

- 通知审计的 Related 列增加复制按钮。
- 新增 `buildNotificationRelatedAuditDeepLink`，生成 `tab=notification&relatedType=...&relatedId=...` 的审计筛选链接。
- 新增 `copyNotificationRelatedAuditLinkToClipboard`，一键复制业务关联审计链接。
- 质量门检查剪贴板工具和 SystemLog 的 Related 复制入口。

## 三端协同价值

- 管理员端可快速分享某个业务对象的通知投递审计条件。
- 教师审批结果通知、审批提醒通知、学生考试通知都能按各自关联对象复现查询。
- 学生端和教师端不用增加额外页面，管理员审计侧统一承担追踪和复核入口。

## 验收点

- 有业务关联的通知审计行显示复制按钮。
- 复制出的链接能打开 Notification Audit 并自动填充关联筛选条件。
- 无业务关联的通知行不显示该按钮。
- 原有按通知 ID 复制和深链能力保持可用。
