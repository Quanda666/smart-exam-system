# 49. 考试业务指标 Smoke 验收

## 目标

本批次把“链路跑过”推进到“链路指标也必须出现”：

- 新增脚本检查 `/actuator/prometheus`。
- 验证考试关键操作的 Micrometer/Prometheus counter 已输出。
- 将指标检查接入 nightly 验收，避免作答链路退化时监控也悄悄失效。

## 新增脚本

```powershell
scripts\check-exam-metrics-smoke.ps1
```

能力：

- 请求 `GET /actuator/prometheus`。
- 校验响应是 Prometheus text exposition。
- 查找 `smart_exam_exam_operation_total` 或兼容的 `smart_exam_exam_operation_total_total`。
- 按 `operation` 聚合计数。
- 可选按 `operation:outcome` 精确校验。
- 支持 `-RequireSubmitReplayMismatchOutcomes` 一键要求 `submitExam:replay_payload_mismatch` 和 `submitExam:replay_token_mismatch`。
- 支持 `-PrometheusFile` 离线校验一份 Prometheus 文本快照。
- 支持 `-ResultFile` 输出 JSON 结果，供 CI artifact 和后续趋势分析使用；缺少必需 operation/outcome 时也会先写出失败结果，Prometheus 输入错误或响应格式错误时同样会写失败结果。

## 使用示例

只校验操作出现：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-exam-metrics-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -RequiredOperations prepareAttemptResilienceFixture,startExam,saveDraft,attemptHeartbeat
```

校验操作和 outcome：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-exam-metrics-smoke.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -RequiredOperationOutcomes startExam:started,attemptHeartbeat:ok
```

## Nightly 接入

`nightly-acceptance.yml` 新增步骤：

```text
Verify exam operation metrics
```

默认要求出现：

- `prepareAttemptResilienceFixture`
- `startExam`
- `saveDraft`
- `attemptHeartbeat`

手动开启 `submit_replay=true` 时，额外要求：

- `submitExam`
- `submitExam:replay_payload_mismatch`
- `submitExam:replay_token_mismatch`

## 验收标准

- 运行一次 nightly 非破坏链路后，Prometheus 输出中能看到考试作答相关 counter。
- 如果业务链路执行成功但指标缺失，nightly 失败。
- 指标脚本不依赖外部 Prometheus 服务，直接读取后端 `/actuator/prometheus`。
- 指标标签仍保持低基数，只检查 `operation` 和 `outcome`，不引入学生、考试、答卷或请求 ID。

## 后续增强

- 在接入真实 Prometheus 后，增加 PromQL 检查 P95、错误率和草稿保存吞吐。
- 将 `run-exam-load-smoke.ps1` 的结果和指标快照合并成一份验收报告。
