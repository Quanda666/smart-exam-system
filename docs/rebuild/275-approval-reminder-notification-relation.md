# 275. 审批超时提醒通知绑定提醒日志

## 背景

审批超时提醒已经有独立的 `exam_approval_reminder_log`，但真正发给管理员的站内通知仍是无关联的普通 `EXAM_APPROVAL` 通知。管理员在通知审计中只能看到一批提醒通知，无法反查这些通知来自哪一次提醒任务。

## 本步改动

- `NotificationService.sendBatch` 增加可选业务关联参数。
- 审批超时提醒先记录 `exam_approval_reminder_log` 并取得 `reminderLogId`。
- 实际发送给管理员的提醒通知绑定 `related_type = APPROVAL_REMINDER`、`related_id = reminderLogId`。
- `sendApprovalOverdueReminders` 响应增加 `reminderLogId`。
- 管理员手动发送提醒后的成功提示展示提醒记录编号。
- 质量门增加后端关联发送、前端类型和成功提示检查。

## 三端协同价值

- 管理员端可以从提醒记录追踪到对应通知，也可以在通知审计中按 `APPROVAL_REMINDER / reminderLogId` 筛选投递结果。
- 教师端考试发布申请仍通过审批队列处理，提醒通知只面向管理员，不干扰教师和学生。
- 学生端考试通知继续按 `EXAM_ATTEMPT` 关联答卷，审批提醒通知按 `APPROVAL_REMINDER` 关联运维审计事件，通知类型边界更清晰。

## 验收点

- 发送审批超时提醒时，先生成提醒日志，再发送通知。
- 已发送的提醒通知包含 `related_type = APPROVAL_REMINDER` 和提醒日志 ID。
- 手动提醒成功提示包含提醒记录编号。
- 原有无关联批量通知调用仍可继续使用。
