# 29. 审批超时提醒定时任务

## 目标
- 在 Batch 28 的提醒日志与冷却规则基础上，增加后台自动提醒能力。
- 避免审批队列只能依赖管理员手动点击提醒。
- 自动任务与手动提醒共用同一套 SLA、冷却、收件人和审计日志规则。

## 后端改造
- 应用入口启用 `@EnableScheduling`。
- 新增 `ApprovalReminderScheduler`：
  - 默认启动后 120 秒开始检查。
  - 默认每 300 秒唤醒一次。
  - 唤醒频率可通过应用属性调整：
    - `smart-exam.approval-reminder-check-delay-ms`
    - `smart-exam.approval-reminder-initial-delay-ms`
- 新增系统配置：
  - `approval.reminderScheduleEnabled`：是否启用自动提醒任务，默认 `true`。
  - `approval.reminderScheduleIntervalMinutes`：自动任务最小执行间隔，默认 `60` 分钟。
- `ExamService` 新增系统触发入口：
  - `sendScheduledApprovalOverdueReminders()`
  - 使用触发人 `0` 和来源 `SCHEDULE` 写入日志。
- `exam_approval_reminder_log` 新增 `trigger_source`：
  - `MANUAL`：管理员手动触发。
  - `SCHEDULE`：后台定时任务触发。
- 自动任务不会在每次唤醒时都刷日志：
  - 如果自动任务开关关闭，直接跳过。
  - 如果距离上一次自动任务日志未达到配置间隔，直接跳过。
  - 达到间隔后才按统一提醒规则写入日志。

## 前端改造
- 审批提醒记录增加“来源”列：
  - 手动
  - 自动
- 提醒结果映射补充：
  - `SKIPPED_SCHEDULE_DISABLED`
  - `SKIPPED_SCHEDULE_INTERVAL`
- 系统配置页沿用 `APPROVAL` 分类：
  - 管理员可以直接维护自动提醒开关和自动提醒最小执行间隔。

## 协同价值
- 管理员端：
  - 不需要长期盯着审批队列。
  - 可以通过提醒记录追踪自动任务是否运行、是否发送、是否被冷却拦截。
- 教师端：
  - 超时审批更容易被管理员看到，降低考试发布卡住的概率。
- 学生端：
  - 间接受益于发布审批 SLA，减少临近开考仍未发布的异常。

## 风险与边界
- 自动提醒只发送站内通知，不自动审批、不自动驳回。
- 冷却规则仍然全局生效，避免自动任务与手动按钮重复发送。
- 当前为单体内定时任务；多实例部署时仍可能多节点同时唤醒，后续高可用阶段应引入数据库锁或 Redis 分布式锁。

## 后续建议
- Batch 30 可补多实例调度锁：
  - 新增 `system_job_lock` 表或 Redis 锁。
  - 自动任务执行前抢锁，避免横向扩容后重复运行。
  - 在任务日志中记录节点标识与执行耗时。
