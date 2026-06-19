# 37. 作答验收候选 Attempt 自动发现

## 目标

- 降低第 36 批验收脚本的人工准备成本。
- 管理员可以只读查询当前适合作答可靠性验收的 attempt。
- 脚本在未传 `-AttemptId` 时，可以自动发现候选 attempt。

## 后端接口

新增管理员接口：

- `GET /api/exams/attempt-resilience/candidates`

参数：

- `keyword`：可选，按考试名、试卷名、学生账号、学生姓名、学号过滤。
- `openOnly`：默认 `true`，只返回当前考试窗口内且未提交的 attempt。
- `page` / `size`：分页参数。

返回字段包含：

- `attemptId`
- `attemptStatus`
- `attemptNo`
- `examId`
- `examName`
- `examStatus`
- `startTime`
- `endTime`
- `durationMinutes`
- `studentUserId`
- `studentUsername`
- `studentName`
- `studentNo`
- `className`
- `openForVerification`
- `snapshotQuestionCount`
- `paperQuestionCount`
- `verificationFlags`

## 查询边界

- 仅 `ADMIN` 可访问。
- 只读，不创建 attempt，不启动考试，不保存草稿，不提交答卷。
- 默认只返回 `exam.status=PUBLISHED`、考试窗口内、attempt 状态为 `0/1` 的记录。
- `verificationFlags` 用于辅助判断：
  - `NOT_STARTED`
  - `IN_PROGRESS`
  - `EXAM_NOT_PUBLISHED`
  - `BEFORE_START`
  - `AFTER_END`
  - `DURATION_EXPIRED`

## 脚本增强

`scripts/verify-attempt-resilience.ps1` 新增自动发现能力：

- 未传 `-AttemptId` 时，脚本会调用 `/api/exams/attempt-resilience/candidates`。
- 若传入 `-Username`，会用该用户名作为默认 `keyword`，尽量发现该学生自己的 attempt。
- 可使用 `-DiscoveryKeyword` 覆盖自动过滤关键词。

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -Username student001 `
  -Password student123
```

提交回放模式：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -Username student001 `
  -Password student123 `
  -Submit
```

## 注意事项

- 自动发现只解决“找 attempt”的问题，学生侧接口仍需要该学生的 token 或账号密码。
- 如果自动发现到的 `studentUsername` 与传入的 `-Username` 不一致，脚本会给出 warning，后续学生开考接口可能因身份不匹配失败。
- `-Submit` 仍会真正交卷，必须使用可消耗测试 attempt。

## 下一批建议

- 增加清理测试夹具接口或脚本，避免测试环境长期积累历史 fixture 数据。
- 将 fixture 准备脚本和 `verify-attempt-resilience.ps1` 纳入测试环境 nightly job，非生产环境定时跑完整提交回放。
