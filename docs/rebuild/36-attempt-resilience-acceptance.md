# 36. 作答可靠性验收脚本

## 目标

- 将草稿保存、心跳恢复、提交前刷盘、重复提交响应回放从“代码能力”推进到“可执行验收”。
- 提供一个无需新增 npm/Maven 测试依赖的 API 验收脚本。
- 明确 destructive 验收边界，避免误提交正式考试答卷。

## 新增脚本

- `scripts/verify-attempt-resilience.ps1`

脚本支持两种运行模式：

- 非破坏模式：只验证开考、草稿保存、心跳恢复、可选管理员草稿缓存状态。
- 破坏模式：增加首次提交和同 token 重复提交响应回放校验，会真正提交 attempt。
- 若未传 `-AttemptId`，并提供管理员 token 或管理员账号密码，脚本会调用候选 attempt 发现接口自动选择一条 open attempt。

## 示例

非破坏模式：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptId 1001 `
  -Username student001 `
  -Password student123
```

完整提交回放模式：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptId 1001 `
  -Username student001 `
  -Password student123 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -Submit
```

Redis 写回模式专项验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptId 1001 `
  -StudentToken <student-token> `
  -AdminToken <admin-token> `
  -Submit `
  -ExpectWriteBack
```

## 验收点

- `POST /api/exams/attempt/{attemptId}/start`
  - 返回题目列表。
  - 支持未开始 attempt 自动进入作答。
- `POST /api/exams/attempt/{attemptId}/save`
  - 返回 `saved=true`。
  - 返回的 `serverRevision` 不小于客户端 revision。
  - 保留 `clientDraftId`。
  - `-ExpectWriteBack` 时必须返回 `writeBack=true` 和 `draftSource=REDIS`。
- `POST /api/exams/attempt/{attemptId}/heartbeat`
  - 返回 `remainingSeconds`。
  - 返回 `draftRevision`，用于恢复检查。
- `GET /api/exams/draft-cache/status`
  - 管理员 token 存在时验证 `alertLevel`、`lastFlushAtEpochMillis`、`dirtyHighThreshold`。
- `POST /api/exams/attempt/{attemptId}/submit`
  - 首次提交返回 `success=true`、`alreadySubmitted=false`、`submitPayloadHash`。
  - 同 token 重复提交返回 `alreadySubmitted=true`、`responseReplayed=true`。
  - 重放响应的 `submitToken`、`submitPayloadHash`、`status` 与首次提交一致。

## 使用约束

- `-Submit` 会真正提交答卷，只能用于可消耗的测试 attempt。
- 若只传学生 token，不传管理员 token，脚本会跳过草稿缓存状态接口。
- 若未传 `-AnswersJson`，脚本会根据题目自动生成一组可提交答案；主观题使用固定测试文本，客观题默认选第一个选项。

## 下一批建议

- 在 CI 中加入非破坏模式 smoke test，并在测试环境 nightly job 中运行 `-Submit` 模式。
