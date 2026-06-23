# 第 66 批：监考强制交卷事务接口

## 背景

第 65 批已经禁止直接伪造 `FORCE_SUBMIT` 处置记录，但教师实时监考页仍是前端连续调用两个接口：

1. `POST /api/exams/attempt/{attemptId}/force-submit`
2. `POST /api/monitor/sessions/{sessionId}/actions`

如果第一步成功、第二步因为网络或权限波动失败，就会出现“答卷已被强制交卷，但监考处置记录缺失”的审计断点。

## 本批改动

- 新增 `MonitorForceSubmitRequest`，监考强制交卷接口只接收可选 `note`。
- 新增事务型接口：`POST /api/monitor/sessions/{sessionId}/force-submit`。
- `MonitorService.forceSubmitMonitorSession` 在同一个服务事务中完成：
  - 校验当前用户对监考会话有访问权。
  - 调用 `ExamService.forceSubmitAttempt` 执行真实强制交卷，继续沿用考试创建者/管理员的强制交卷权限。
  - 校验返回结果必须为 `submitType=FORCED`。
  - 写入 `FORCE_SUBMIT` 监考处置记录。
  - 如果重试时已存在 `FORCE_SUBMIT` 处置记录，返回已有记录并标记 `actionAlreadyRecorded=true`，避免重复审计。
- 教师实时监考页强制交卷按钮改为只调用新接口，不再由前端串联两个接口。
- `verify-attempt-resilience.ps1` 新增 `-CheckMonitorForceSubmitTransaction`：
  - 调用新监考强制交卷接口。
  - 校验答卷结果为 `FORCED`。
  - 校验处置记录为 `FORCE_SUBMIT`。
  - 再次调用接口，校验处置记录幂等复用。
- `run-attempt-resilience-acceptance.ps1` 透传该开关，并在该模式下不再追加普通 `-Submit`。

## 三端协同影响

- 学生端：最终答卷仍由考试服务生成，未保存内容按未答处理。
- 教师端：监考页一次点击即可完成强制交卷与处置记录，减少半成功状态。
- 管理员端：审计时可以把 `exam_attempt.submit_type=FORCED` 与 `exam_monitor_action.action_type=FORCE_SUBMIT` 对齐。

## 验收方式

该验收会强制提交 disposable attempt，应只用于临时验收数据：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorForceSubmitTransaction `
  -CleanupAfterRun
```

完整质量门：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

## 后续建议

- 为监考处置表增加 `related_operation_id` 或 `related_submit_type` 字段，进一步增强审计关联。
- 将教师端强制交卷结果展示为结构化反馈，例如是否首次强制交卷、是否复用已有处置记录。
