# 52. Acceptance Summary Artifact And Percentiles

## 目标

本批次把 nightly 验收从“页面可读”继续推进到“结果可归档、压测信号更可判断”：

- Markdown summary 同时写入 GitHub Step Summary 和 artifact 文件。
- load smoke JSON 新增请求级延迟指标。
- summary 展示平均、P95、P99、最大请求耗时。
- 保留旧字段 `averageWorkerLatencyMs`，避免破坏既有消费方。

## Load Smoke 结果字段

`scripts\run-exam-load-smoke.ps1` 新增：

- `averageRequestLatencyMs`
- `p95RequestLatencyMs`
- `p99RequestLatencyMs`
- `maxRequestLatencyMs`
- `workers[].latenciesMs`

这些字段来自每次 API 请求的实际耗时，而不是 worker 平均值。后续压测验收应优先看 P95/P99，因为并发考试场景最怕少量慢请求拖垮学生作答体验。

## Summary 归档

`scripts\write-acceptance-summary.ps1` 新增参数：

```powershell
-AlsoWriteStepSummary
```

当传入 `-OutputFile artifacts\nightly-summary.md -AlsoWriteStepSummary` 时：

- `artifacts\nightly-summary.md` 使用覆盖写入，作为稳定 artifact。
- `$GITHUB_STEP_SUMMARY` 使用追加写入，作为 GitHub Actions 页面摘要。

本地没有 `$GITHUB_STEP_SUMMARY` 时，仍可只写文件或输出到控制台。

## Nightly 接入

`.github/workflows/nightly-acceptance.yml` 的 `Write acceptance summary` 步骤现在会生成：

```text
artifacts/nightly-summary.md
```

上传 artifact 列表包含：

- `backend/backend.log`
- `artifacts/nightly-acceptance-result.json`
- `artifacts/nightly-load-smoke-result.json`
- `artifacts/nightly-metrics-smoke-result.json`
- `artifacts/nightly-summary.md`

## 验收标准

- load smoke 成功或失败时都应尽量写入 JSON 结果。
- JSON 中应包含请求级平均、P95、P99、最大耗时。
- summary 中不输出学生密码。
- summary 文件可作为 nightly 运行记录下载留存。
- Step Summary 和 artifact summary 内容来自同一份脚本，避免人工维护两套报告。

## 后续增强

- 为 P95/P99 增加阈值参数，使 nightly 可在延迟退化时直接失败。
- 在 summary 中增加最近多次 nightly 趋势链接。
- 将 load smoke 的请求级耗时按接口分组，区分 `start`、`save`、`heartbeat`、`submit` 的性能表现。
