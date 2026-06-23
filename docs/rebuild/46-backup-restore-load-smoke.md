# 46. 备份恢复与考试链路压测烟测

## 目标

本批次补齐上线前必须具备的两类运维验收能力：

- 数据备份与恢复演练：MySQL 可导出、可恢复；Redis 草稿缓存可落盘复制。
- 考试链路压测入口：对开考、草稿保存、心跳和可选提交进行并发烟测。

这些脚本面向本地和测试环境的 Docker Compose 栈，生产环境可以复用流程，但应接入正式备份系统、对象存储和 CI/CD 密钥管理。

## 新增脚本

| 脚本 | 类型 | 是否破坏数据 | 说明 |
| --- | --- | --- | --- |
| `scripts/backup-compose-data.ps1` | 备份 | 否 | 导出 MySQL，按需复制 Redis `/data` |
| `scripts/restore-mysql-backup.ps1` | 恢复 | 是 | 从 SQL 文件恢复 MySQL，必须带 `-ConfirmRestore` |
| `scripts/run-exam-load-smoke.ps1` | 压测烟测 | 默认否 | 默认只测 start/save/heartbeat，`-IncludeSubmit` 才提交 |

## 环境变量

`.env.example` 新增：

```properties
BACKUP_DIR=backups
BACKUP_RETENTION_DAYS=14
LOAD_TEST_DEFAULT_CONCURRENCY=20
LOAD_TEST_DEFAULT_ITERATIONS=5
```

`scripts/check-deploy-config.ps1` 会校验：

- `BACKUP_RETENTION_DAYS` 是非负整数。
- `LOAD_TEST_DEFAULT_CONCURRENCY` 是正整数。
- `LOAD_TEST_DEFAULT_ITERATIONS` 是正整数。

## MySQL 备份

启动 Compose 栈后执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-compose-data.ps1 `
  -EnvFile .env
```

脚本行为：

- 检查 `smart-exam-mysql` 容器正在运行。
- 使用 `mysqldump --single-transaction --quick --routines --triggers --events` 导出业务库。
- 在 `BACKUP_DIR` 下创建 `smart-exam-yyyyMMdd-HHmmss` 目录。
- 写入 `manifest.json`，记录数据库名、导出文件和字节数。

输出示例：

```text
backups/
  smart-exam-20260617-101500/
    smart_exam_system.sql
    manifest.json
```

## Redis 备份

Redis 保存的是考试草稿缓存、写回恢复状态和后续会话/缓存数据。默认备份脚本不复制 Redis，避免把短期缓存误当作强一致主数据。

需要复制 Redis 时执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backup-compose-data.ps1 `
  -EnvFile .env `
  -IncludeRedis
```

脚本会先执行 `redis-cli SAVE`，再复制容器内 `/data` 到备份目录：

```text
backups/
  smart-exam-20260617-101500/
    smart_exam_system.sql
    redis-data/
    manifest.json
```

Redis 恢复不做自动化脚本，原因是它通常涉及停容器、替换卷内容、重新启动服务，属于强破坏操作。测试环境恢复 Redis 时应先停止后端写入，再离线替换 `smart-exam-redis-data` 卷内容。

## MySQL 恢复演练

恢复必须显式确认：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-mysql-backup.ps1 `
  -EnvFile .env `
  -BackupFile backups\smart-exam-20260617-101500\smart_exam_system.sql `
  -ConfirmRestore
```

需要先清空目标库时：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\restore-mysql-backup.ps1 `
  -EnvFile .env `
  -BackupFile backups\smart-exam-20260617-101500\smart_exam_system.sql `
  -DropExisting `
  -ConfirmRestore
```

恢复验收：

- 后端 `/api/health` 正常。
- 管理员、教师、学生账号可以登录。
- 题库、试卷、考试、答卷、成绩发布、申诉、监考会话关键表有数据。
- 执行一次非破坏性 `check-ops-health.ps1 -ExpectPrometheus`。

## 考试链路压测烟测

压测脚本默认调用：

- `POST /api/exams/attempt/{id}/start`
- `POST /api/exams/attempt/{id}/save`
- `POST /api/exams/attempt/{id}/heartbeat`

默认不提交试卷。

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-exam-load-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptIds 1001,1002,1003 `
  -StudentTokens token1,token2,token3 `
  -ConcurrentUsers 20 `
  -Iterations 5
```

如果没有提前准备 token，可以让 worker 登录同一测试账号：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-exam-load-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptIds 1001 `
  -Username student_fixture_001 `
  -Password 123456 `
  -ConcurrentUsers 10 `
  -Iterations 3
```

提交链路必须使用一次性夹具答卷：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-exam-load-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -AttemptIds 1001,1002,1003 `
  -StudentTokens token1,token2,token3 `
  -ConcurrentUsers 3 `
  -Iterations 1 `
  -IncludeSubmit
```

## 与现有验收脚本配合

推荐测试环境顺序：

1. `check-deploy-config.ps1 -EnvFile .env`
2. `docker compose --env-file .env up -d --build`
3. `check-ops-health.ps1 -ExpectPrometheus`
4. `prepare-attempt-resilience-fixture.ps1` 创建一次性考试夹具。
5. `verify-attempt-resilience.ps1` 验证开考、草稿、心跳、可选提交。
6. `run-exam-load-smoke.ps1` 做并发烟测。
7. `backup-compose-data.ps1 -IncludeRedis` 生成备份。
8. 在隔离环境使用 `restore-mysql-backup.ps1 -ConfirmRestore` 演练恢复。
9. `cleanup-attempt-resilience-fixtures.ps1` 清理夹具。

## 验收标准

- 部署预检通过，且弱密码只允许出现在一次性本地环境。
- MySQL 备份目录包含 SQL 文件和 manifest。
- Redis 备份目录在 `-IncludeRedis` 时包含 `/data` 副本。
- MySQL 恢复脚本不带 `-ConfirmRestore` 会拒绝执行。
- 非破坏性压测在目标并发下错误数为 0。
- 压测期间 Prometheus 能看到 `smart_exam_exam_operation_*` 指标增长。
- 慢请求日志、`X-Request-Id` 和前端 `ApiError.requestId` 能串起一次失败请求。

## 后续增强

- 接入对象存储保存备份，并加入校验和。
- 加入 `k6` 或 `JMeter` 场景，模拟 300 并发开考、保存草稿和交卷。
- CI 中固定执行配置预检、脚本语法检查和 Docker Compose config。
- 生产环境备份应做定时任务、异地复制和定期恢复演练。
