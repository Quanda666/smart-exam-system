# 28. 审批超时提醒审计与去重

## 目标
- 将审批超时提醒从“按钮触发通知”补强为可审计、可解释、可去重的运营闭环。
- 避免管理员连续点击造成重复站内信。
- 为后续接入定时提醒任务预留同一套日志与冷却规则。

## 后端改造
- 新增 `exam_approval_reminder_log`：
  - `triggered_by`：触发提醒的管理员。
  - `overdue_hours`：本次使用的 SLA 超时阈值。
  - `cooldown_hours`：本次使用的提醒冷却间隔。
  - `overdue_exam_count`：本次命中的超时审批数量。
  - `recipient_count`：本次目标管理员数量。
  - `status`：`SENT`、`SKIPPED_DISABLED`、`SKIPPED_EMPTY`、`SKIPPED_NO_RECIPIENT`、`SKIPPED_COOLDOWN`。
  - `message`：本次处理说明。
- 新增系统配置：
  - `approval.reminderCooldownHours`，默认 `6` 小时。
- 调整 `POST /api/exams/approvals/reminders`：
  - 读取 `approval.reminderEnabled`、`approval.slaOverdueHours`、`approval.reminderCooldownHours`。
  - 按关闭、无超时、无收件人、冷却中、已发送五种结果处理。
  - 每次触发均写入提醒日志。
  - 返回 `cooldownActive`、`cooldownHours`、`status`、`message`、`lastReminderAt`。
- 新增 `GET /api/exams/approvals/reminders`：
  - 管理员分页查看审批提醒日志。

## 前端改造
- 审批队列页新增“提醒记录”入口：
  - 展示触发结果、触发人、超时数量、收件人数、阈值、冷却时间、触发时间和说明。
  - 发送提醒后，如果记录抽屉已打开，会自动刷新。
- 管理员仪表盘与审批队列的发送反馈细化：
  - 区分系统关闭、冷却中、无超时审批、成功发送。
- 系统配置页无需额外改动：
  - 新增配置属于 `APPROVAL` 分类，会自动出现在审批配置中。

## 协同价值
- 管理员端：可追踪谁触发了审批提醒，以及为什么没有真正发送。
- 教师端：减少重复提醒噪音，但仍能通过审批队列推进超时申请。
- 学生端：间接受益于审批 SLA 管理，减少考试发布卡在审批阶段导致的开考异常。

## 后续建议
- Batch 29 可接入定时任务：
  - 使用同一套 `approval.reminderCooldownHours` 和 `exam_approval_reminder_log`。
  - 定时扫描超时审批并发送一次提醒。
  - 增加系统配置 `approval.reminderScheduleEnabled` 与 cron/interval 配置。
