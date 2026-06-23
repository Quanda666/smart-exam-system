# 27. 审批 SLA 配置化与提醒通知

## 目标

本批次把第 26 批中的“24 小时审批超时”从硬编码升级为系统配置，并增加管理员手动发送超时审批提醒的能力。审批 SLA 从“只展示指标”推进为“可配置、可触达”。

## 后端变更

- 新增系统配置默认值：
  - `approval.slaOverdueHours`：审批超时阈值，默认 `24` 小时。
  - `approval.reminderEnabled`：是否允许发送审批超时提醒，默认 `true`。
- `/api/overview/admin` 改为读取 `approval.slaOverdueHours`：
  - `approvalSummary.overdue` 按配置阈值计算。
  - `approvalSummary.overdueHours` 返回当前阈值，前端展示时不再写死 24 小时。
- 新增 `POST /api/exams/approvals/reminders`：
  - 仅管理员可调用。
  - 读取 `approval.reminderEnabled` 和 `approval.slaOverdueHours`。
  - 如果存在超过阈值的待审批考试，向所有启用状态的管理员发送站内信。
  - 返回 `sent`、`enabled`、`overdueHours`、`overdueExamCount`、`adminCount`。

## 前端变更

- 系统配置页新增 `APPROVAL` 分类和“审批配置”统计卡。
- 管理员首页：
  - 审批提醒面板展示配置化阈值。
  - 待审批列表按配置化阈值标记超时。
  - 新增“发送提醒”按钮。
- 审批队列页：
  - 顶部新增“发送超时提醒”按钮。
  - 调用提醒接口后，根据返回结果提示：已发送、无超时审批、或配置已关闭。

## 三端协同影响

- 管理员端可以根据学院管理要求调整审批超时阈值。
- 教师提交的考试如果长时间未处理，管理员可以通过提醒机制降低遗漏概率。
- 学生端仍只消费已发布考试；提醒机制不会改变考试可见性。

## 后续任务

- 增加自动定时提醒，避免依赖管理员手动点击。
- 增加提醒去重策略，例如同一场考试 24 小时内只提醒一次。
- 将审批超时提醒纳入系统日志和告警中心。
- 支持按学院、考试周期、课程班范围定向提醒对应管理员。
