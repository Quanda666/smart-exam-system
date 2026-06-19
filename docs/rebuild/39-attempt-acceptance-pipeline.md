# 39. 作答可靠性一键验收管线

## 目标

- 将“准备测试夹具”和“执行作答可靠性验收”串成一个可重复运行的测试环境脚本。
- 默认创建一次性学生、考试和 attempt，避免提交型验收污染真实考试或复用旧答卷。
- 自动临时开启 `system.testFixtureEnabled`，执行完成后恢复原值，降低测试造数入口误开的风险。

## 新增脚本

- `scripts/run-attempt-resilience-acceptance.ps1`

默认流程：

1. 使用管理员 token，或管理员账号密码登录。
2. 读取 `system.testFixtureEnabled` 当前值。
3. 若开关未开启，则临时设置为 `true`。
4. 调用 `POST /api/exams/attempt-resilience/fixture` 创建 disposable 测试数据。
5. 调用 `scripts/verify-attempt-resilience.ps1` 执行开考、草稿保存、心跳恢复、草稿缓存状态校验。
6. 默认继续执行首次提交和同 token 重复提交响应重放校验。
7. 若脚本临时开启过测试夹具开关，结束时恢复为原值。

## 使用方式

完整提交重放验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123
```

仅非破坏性冒烟：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -SkipSubmit
```

Redis 写回模式专项验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -ExpectWriteBack
```

完整验收后自动清理本次 disposable 数据：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AdminUsername admin `
  -AdminPassword admin123 `
  -CleanupAfterRun
```

## 参数说明

- `-AdminToken`：已有管理员 token，可跳过登录。
- `-AdminUsername` / `-AdminPassword`：无 token 时用于登录。
- `-StudentUsername`：可指定测试学生；默认自动生成 `verify_student_时间戳_随机后缀`。
- `-StudentPassword`：测试学生密码，默认 `student123`。
- `-DurationMinutes`：测试考试时长，默认 120 分钟。
- `-SkipSubmit`：只跑开考、草稿、心跳和缓存状态，不提交答卷。
- `-ExpectWriteBack`：要求草稿保存和提交前刷盘路径命中 Redis 写回模式。
- `-CleanupAfterRun`：验收成功后调用清理接口归档本次 disposable 数据。
- `-CleanupOlderThanHours`：自动清理时的最小年龄，默认 0 小时，仅匹配本次测试学生前缀。
- `-KeepFixtureConfigEnabled`：脚本临时开启夹具配置后不恢复，通常只用于手工调试。

## 验收边界

- 默认模式会真实提交测试 attempt，只能用于脚本创建的 disposable 数据。
- 生产环境应保持 `system.testFixtureEnabled=false`，不应运行该脚本。
- 若验证失败，脚本仍会尝试恢复 `system.testFixtureEnabled` 原值。
- 默认不清理测试数据，方便失败后排查；CI/nightly 可显式添加 `-CleanupAfterRun`。

## 接入建议

- 测试环境 smoke job：每天或每次部署后运行 `-SkipSubmit`。
- nightly job：运行默认完整模式，覆盖提交响应重放和提交前草稿刷盘。
- Redis 写回环境：额外运行 `-ExpectWriteBack`，验证 Redis dirty draft 刷盘链路。
