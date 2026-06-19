# 第 67 批：监考处置学生通知闭环

## 背景

实时监考页已经支持 `WARN`、`ACKNOWLEDGE`、`NOTE` 和事务型 `FORCE_SUBMIT`，但 `WARN` 之前只是教师端审计记录。真实考试里，“提醒学生”必须触达学生端，否则教师看到的是处置闭环，学生却没有收到任何明确提示。

## 本批改动

- `MonitorService` 注入 `NotificationService`。
- 写入 `WARN` 监考处置后，给对应学生发送站内通知：
  - `type = MONITOR_WARNING`
  - 标题为 `Exam monitor reminder`
  - 内容优先使用教师填写的处置说明
  - 跳转链接为 `/student/exams`
- 首次写入 `FORCE_SUBMIT` 处置后，给学生发送站内通知：
  - `type = MONITOR_FORCE_SUBMIT`
  - 标题为 `Exam force-submitted`
  - 内容优先使用强制交卷说明
  - 重试复用已有 `FORCE_SUBMIT` 处置时不重复发送通知
- `createMonitorAction` 返回的 action 增加 `notificationSent`，教师端和验收脚本可以知道本次处置是否触达学生。
- `forceSubmitMonitorSession` 返回 `notificationSent`，首次事务型强制交卷为 `true`，重试复用已有处置为 `false`。
- `verify-attempt-resilience.ps1` 新增 `-CheckMonitorWarnNotification`：
  - 写入一条唯一 `WARN` 处置。
  - 校验 action 返回 `notificationSent=true`。
  - 使用学生 token 查询 `/api/notifications/my`，确认能看到 `MONITOR_WARNING` 通知。
- `run-attempt-resilience-acceptance.ps1` 透传 `-CheckMonitorWarnNotification`。

## 三端协同影响

- 教师端：点击“提醒学生”不再只是留痕，而是会给学生发个人通知。
- 学生端：现有通知铃铛可看到监考提醒和强制交卷通知。
- 管理员端：监考审计记录、通知记录、答卷状态可以互相印证，减少只在单端可见的处置。

## 验收方式

非破坏性验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorWarnNotification `
  -SkipSubmit `
  -CleanupAfterRun
```

可与监考入口验收组合：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorEventDedup `
  -CheckMonitorActionForceSubmitBinding `
  -CheckMonitorWarnNotification `
  -SkipSubmit `
  -CleanupAfterRun
```

## 后续建议

- 学生作答页可增加轻量实时提醒区域，用于展示最新监考提醒，而不只依赖顶部通知铃铛。
- 通知表后续可增加 `related_type`、`related_id`，关联监考处置记录，便于管理员审计通知送达链路。
