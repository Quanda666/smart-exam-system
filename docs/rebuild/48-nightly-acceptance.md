# 48. Nightly 考试韧性验收与非破坏压测

## 目标

本批次把考试作答韧性链路接入定时自动验收：

- 每晚自动启动 MySQL、Redis 和后端。
- 临时开启测试夹具开关，创建一次性考试、试卷、题目、学生和答卷。
- 默认执行非破坏性开考、草稿保存、心跳、Redis 写回状态校验。
- 基于同一一次性答卷执行非破坏性并发压测烟测。
- 最后清理本次 disposable fixture。

## 新增 Workflow

新增：

```text
.github/workflows/nightly-acceptance.yml
```

触发方式：

- `schedule`：每天 UTC 18:30，约北京时间 02:30。
- `workflow_dispatch`：手动触发，可调参数。

服务依赖：

- MySQL 8.4
- Redis 7.2

后端运行配置：

- `DB_INIT_MODE=always`
- `REDIS_HEALTH_ENABLED=true`
- `EXAM_DRAFT_REDIS_ENABLED=true`
- `AI_MOCK_ENABLED=true`
- `PROMETHEUS_METRICS_ENABLED=true`

## 执行链路

Nightly job 的步骤：

1. 校验关键 PowerShell 脚本语法。
2. 启动后端并等待 `/api/health`。
3. 执行 `check-ops-health.ps1 -ExpectPrometheus`。
4. 执行 `run-attempt-resilience-acceptance.ps1`。
5. 写入 `artifacts/nightly-acceptance-result.json`。
6. 从结果文件读取 `attemptId`、学生用户名和密码。
7. 在非提交模式下执行 `run-exam-load-smoke.ps1`，默认只测 `start/save/heartbeat`。
8. `always()` 清理 `verify_student_nightly_<run_id>` 夹具。
9. 上传后端日志和验收结果文件。

## 安全边界

默认 nightly 不提交答卷：

```powershell
run-attempt-resilience-acceptance.ps1 -SkipSubmit
```

提交回放只允许手动触发时打开：

```text
submit_replay=true
```

即使打开提交回放，也只作用于 workflow 当前创建的一次性 fixture attempt。

当 `submit_replay=true` 时，后续非破坏压测步骤会跳过，因为该 attempt 已经提交，不再适合作为草稿保存和心跳压测对象。

夹具开关边界：

- `system.testFixtureEnabled` 默认保持 `false`。
- 验收脚本会读取原值。
- 如果原值不是 `true`，脚本临时设置为 `true`。
- 执行结束后恢复原值。
- 清理脚本也遵循同样的临时开启和恢复规则。

## 结构化结果输出

`run-attempt-resilience-acceptance.ps1` 新增：

```powershell
-ResultFile artifacts/nightly-acceptance-result.json
```

结果内容包含：

- `success`
- `mode`
- `baseUrl`
- `studentUsername`
- `studentPassword`
- `examId`
- `attemptId`
- `cleanupAfterRun`

这个文件用于后续压测步骤读取 attempt 和登录信息，也会作为 workflow artifact 上传，方便失败后排查。

## 手动触发参数

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `submit_replay` | `false` | 是否执行提交回放验证 |
| `load_concurrency` | `5` | 非破坏压测并发 worker 数 |
| `load_iterations` | `2` | 每个 worker 的 save/heartbeat 循环次数 |

## 本地复现

启动后端后可本地复现 nightly 主流程：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -StudentUsername verify_student_nightly_local `
  -StudentPassword student123 `
  -ExpectWriteBack `
  -SkipSubmit `
  -ResultFile artifacts\nightly-acceptance-result.json
```

然后执行非破坏压测：

```powershell
$result = Get-Content artifacts\nightly-acceptance-result.json -Raw | ConvertFrom-Json
powershell -ExecutionPolicy Bypass -File scripts\run-exam-load-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptIds $result.attemptId `
  -Username $result.studentUsername `
  -Password $result.studentPassword `
  -ConcurrentUsers 5 `
  -Iterations 2
```

清理：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\cleanup-attempt-resilience-fixtures.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -StudentPrefix verify_student_nightly_local `
  -OlderThanHours 0 `
  -Execute
```

## 验收标准

- Nightly workflow 默认不会执行提交。
- 手动开启 `submit_replay=true` 时，提交只作用于一次性 fixture。
- 手动开启 `submit_replay=true` 时，非破坏压测步骤会跳过。
- 验收结果 JSON 能被后续压测步骤读取。
- 压测默认只调用开考、草稿保存和心跳。
- 清理步骤使用 `always()`，失败时也尽量回收夹具。
- 工作流 artifact 包含后端日志和验收结果。

## 后续增强

- 引入 Playwright 跑三端完整链路。
- 在 nightly 中增加 `run-exam-load-smoke.ps1` 的 50/100/300 并发分层场景。
- 接入 Prometheus 查询，自动判断 P95、错误率和草稿保存吞吐。
