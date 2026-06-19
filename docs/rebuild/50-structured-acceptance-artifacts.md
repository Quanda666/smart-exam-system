# 50. 结构化验收 Artifact

## 目标

本批次把 nightly 验收从“只能看控制台日志”推进到“脚本结果可被机器读取”：

- 压测烟测输出 JSON。
- 指标 smoke 输出 JSON。
- Nightly workflow 上传验收结果、压测结果、指标结果和后端日志。

这些 JSON 后续可以直接用于生成趋势报告、阈值判断和发布验收摘要。

## 压测结果

`scripts/run-exam-load-smoke.ps1` 新增：

```powershell
-ResultFile artifacts/nightly-load-smoke-result.json
```

结果字段：

- `success`
- `generatedAt`
- `baseUrl`
- `attemptIds`
- `concurrentUsers`
- `iterations`
- `thinkTimeMs`
- `includeSubmit`
- `totalSuccess`
- `totalFailed`
- `averageWorkerLatencyMs`
- `sampleErrors`
- `workers`

如果压测中有失败请求，脚本会先写结果文件，再失败退出，便于 CI artifact 保留失败原因。

## 指标结果

`scripts/check-exam-metrics-smoke.ps1` 新增：

```powershell
-ResultFile artifacts/nightly-metrics-smoke-result.json
```

结果字段：

- `success`
- `generatedAt`
- `baseUrl`
- `prometheusFile`
- `requiredOperations`
- `requiredOperationOutcomes`
- `missingOperations`
- `missingOperationOutcomes`
- `invalidRequiredOperationOutcomes`
- `failureReason`
- `observedOperations`
- `observedOperationOutcomes`

如果指标 smoke 缺少必需 operation/outcome，脚本会先写 `success=false` 的结果文件，再失败退出，便于 nightly summary 和 artifact 直接展示缺失项。

## Nightly Artifact

`nightly-acceptance.yml` 上传：

```text
backend/backend.log
artifacts/nightly-acceptance-result.json
artifacts/nightly-load-smoke-result.json
artifacts/nightly-metrics-smoke-result.json
```

说明：

- 默认非提交模式会生成压测结果。
- `submit_replay=true` 时压测步骤会跳过，因此可能没有 `nightly-load-smoke-result.json`。
- 指标结果在默认和提交回放模式下都会生成。

## 验收标准

- 压测脚本在成功和失败场景下都能输出结构化摘要。
- 指标脚本能输出已观察到的 operation/outcome 列表。
- Nightly artifact 能支持失败排查，不需要只依赖长日志搜索。

## 后续增强

- 将 JSON 结果转换成 Markdown summary 写入 GitHub Step Summary。
- 增加 P95、P99、错误率和吞吐量阈值。
- 将 nightly 历史 JSON 归档到对象存储或制品库，用于容量趋势分析。
