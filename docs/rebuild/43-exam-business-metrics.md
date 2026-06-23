# 43. 考试关键链路业务指标

## 目标

- 在 Actuator/Micrometer 底座上补充考试业务指标。
- 覆盖学生作答、草稿、提交、心跳、监考事件和验收夹具链路。
- 指标标签保持低基数，避免把学生 ID、attemptId、examId 写入指标系统。

## 新增切面

- `ExamOperationMetricsAspect`

拦截以下方法：

- `ExamService.startExam`
- `ExamService.saveDraft`
- `ExamService.submitExam`
- `ExamService.attemptHeartbeat`
- `ExamService.forceSubmitAttempt`
- `ExamService.flushRedisDrafts`
- `ExamService.prepareAttemptResilienceFixture`
- `ExamService.cleanupAttemptResilienceFixtures`
- `MonitorService.recordCheatEvent`
- `MonitorService.recordCheatEvents`

## 指标名称

计数器：

- `smart_exam.exam.operation.total`

耗时：

- `smart_exam.exam.operation.duration`

公共 tag：

- `application=smart-exam-backend`

业务 tag：

- `operation`
- `outcome`

## Outcome 约定

`startExam`：

- `started`
- `auto_submitted`
- `error`

`saveDraft`：

- `saved_redis_writeback`
- `saved_redis`
- `saved_db`
- `stale`
- `rejected`
- `error`

`submitExam`：

- `submitted_manual`
- `submitted_timeout`
- `replay_payload_mismatch`
- `replay_token_mismatch`
- `already_submitted`
- `replayed`
- `error`

`attemptHeartbeat`：

- `ok`
- `submitted`
- `auto_submitted`
- `error`

`forceSubmitAttempt`：

- `forced`
- `error`

`flushRedisDrafts`：

- `flushed`
- `empty`
- `partial`
- `failed`
- `error`

`prepareAttemptResilienceFixture`：

- `prepared`
- `error`

`cleanupAttemptResilienceFixtures`：

- `dry_run`
- `cleaned`
- `error`

`recordCheatEvent`：

- `accepted`
- `error`

`recordCheatEvents`：

- `accepted`
- `accepted_with_duplicates`
- `duplicates`
- `empty`
- `error`

## Actuator 查看方式

指标列表：

```http
GET /actuator/metrics
```

查看总量：

```http
GET /actuator/metrics/smart_exam.exam.operation.total
```

按 tag 过滤：

```http
GET /actuator/metrics/smart_exam.exam.operation.total?tag=operation:saveDraft
GET /actuator/metrics/smart_exam.exam.operation.duration?tag=operation:submitExam
```

## 压测关注点

- `saveDraft` 的 `rejected`、`stale` 比例是否异常升高。
- `submitExam` 是否出现大量 `error` 或 `already_submitted`。
- `submitExam` 耗时 P95/P99 是否高于验收阈值。
- `attemptHeartbeat` 是否在考试高峰出现大量 `error`。
- `recordCheatEvents` 的 `duplicates` 是否说明前端重试过于频繁。
- `flushRedisDrafts` 是否长期 `partial` 或 `failed`。

## 设计约束

- 不在指标 tag 中写入用户、考试、答卷、IP、请求 ID。
- requestId 归日志系统，指标只做聚合趋势。
- 业务失败要反映为 `outcome=error`，不能只依赖 HTTP 5xx。

## 下一步

- 接入 Prometheus registry，暴露 `/actuator/prometheus`。
- 为提交评分、成绩发布、AI 阅卷建议增加独立指标。
- 将压测脚本输出和 Actuator 指标快照合并成验收报告。
