# 第 65 批：强制交卷处置绑定真实答卷状态

## 背景

实时监考页已经支持强制交卷和监考处置记录，但 `FORCE_SUBMIT` 处置记录本身仍可能被直接调用接口写入。如果审计记录可以脱离真实强制交卷结果存在，后续追责、申诉和监考复盘都会失真。

## 本批改动

- `MonitorActionRequest.actionType` 增加最大长度校验，避免处置类型字段无限长输入。
- `MonitorService.createMonitorAction` 在写入处置前校验动作与答卷状态：
  - 普通 `WARN`、`ACKNOWLEDGE`、`NOTE` 保持原有行为。
  - `FORCE_SUBMIT` 必须满足答卷已提交且 `exam_attempt.submit_type = FORCED`。
  - 仍在作答、手动交卷、超时交卷的答卷不能补写 `FORCE_SUBMIT` 处置。
- `loadSessionForAction` 读取监考会话时同时绑定 `exam_attempt.status` 和 `submit_type`，处置动作不再只依赖会话表自身。
- 教师实时监考页移除通用处置弹窗中的“强制交卷”选项，真实强制交卷仍通过表格上的“强制交卷”按钮执行：
  - 先调用 `POST /api/exams/attempt/{attemptId}/force-submit`。
  - 成功后再写入 `FORCE_SUBMIT` 处置记录。
- `verify-attempt-resilience.ps1` 新增 `-CheckMonitorActionForceSubmitBinding`：
  - 学生开考并建立监考会话后，直接调用 `POST /api/monitor/sessions/{sessionId}/actions` 写 `FORCE_SUBMIT`。
  - 服务端必须拒绝，并返回需要真实强制交卷结果的错误。
- `run-attempt-resilience-acceptance.ps1` 透传该验收开关。

## 三端协同影响

- 学生端：答卷状态仍以考试服务为准，监考处置不能改变学生答卷结果。
- 教师端：强制交卷必须走真实作答服务，处置记录只作为动作审计，不再能单独伪造。
- 管理员端：监考复盘看到的 `FORCE_SUBMIT` 记录可以和答卷 `submit_type=FORCED` 对上，便于追踪责任链。

## 验收方式

非破坏性验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorActionForceSubmitBinding `
  -SkipSubmit `
  -CleanupAfterRun
```

可与上一批监考事件入口验收组合：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorEventDedup `
  -CheckMonitorActionForceSubmitBinding `
  -SkipSubmit `
  -CleanupAfterRun
```

## 后续建议

- 将强制交卷接口和处置记录合并为一个后端事务型监考动作接口，避免前端连续调用两个接口时出现部分成功。
- 在监考处置记录中补充 `related_operation_id` 或 `related_submit_type`，便于和操作日志、答卷结果做审计关联。
