# 第 69 批：通知关联答卷与考试内精确过滤

## 背景

第 68 批让学生作答页能轮询并显示监考提醒，但过滤依据主要是“进入考试后的时间窗口”。这能避免大多数历史通知干扰，却不能严格证明通知属于当前答卷。真实考试系统需要把通知和业务对象关联起来，才能让三端审计链更可信。

## 本批改动

- `notification` 表新增：
  - `related_type VARCHAR(64)`：关联业务类型。
  - `related_id BIGINT`：关联业务 ID。
  - `idx_notification_related (related_type, related_id)`。
- 旧库自愈迁移新增 `ensureNotificationRelationColumns`，启动时自动补列和索引。
- `NotificationService.send` 保持原有签名兼容，同时新增支持 `relatedType`、`relatedId` 的重载。
- `NotificationService.myNotifications` 返回 `relatedType`、`relatedId`。
- 监考 `WARN` 与首次 `FORCE_SUBMIT` 通知写入：
  - `relatedType = EXAM_ATTEMPT`
  - `relatedId = attemptId`
- 学生作答页 `ExamTaking.vue` 过滤监考通知时：
  - 优先要求 `relatedType=EXAM_ATTEMPT` 且 `relatedId` 等于当前 `attemptId`。
  - 对旧通知或旧库数据保留进入考试后的时间窗口兜底。
- `verify-attempt-resilience.ps1 -CheckMonitorWarnNotification` 增强：
  - 不仅校验学生能看到 `MONITOR_WARNING`。
  - 还校验通知关联字段准确指向当前 `attemptId`。

## 三端协同影响

- 教师端：监考处置产生的通知能和具体答卷建立数据关联。
- 学生端：作答页只展示当前答卷相关的监考提醒，避免误显示其他考试通知。
- 管理员端：后续可以按 `related_type/related_id` 查询通知送达链路，和监考处置、答卷状态进行审计对齐。

## 验收方式

后端通知关联验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorWarnNotification `
  -SkipSubmit `
  -CleanupAfterRun
```

完整质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

## 后续建议

- 为通知中心增加按通知类型和关联对象筛选。
- 后续如果引入 WebSocket/SSE，可以直接推送 `relatedType/relatedId`，作答页无需轮询通知列表。
