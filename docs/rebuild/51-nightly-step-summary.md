# 51. Nightly Step Summary

## 目标

本批次把 nightly 验收结果从“下载 artifact 再阅读”推进到 GitHub Actions 页面直接可读：

- 新增 Markdown summary 生成脚本。
- 汇总验收结果、压测结果和指标结果。
- 不在 summary 中输出学生密码。
- 文件缺失时显示明确说明，而不是让 summary 步骤失败。

## 新增脚本

```powershell
scripts\write-acceptance-summary.ps1
```

默认读取：

- `artifacts/nightly-acceptance-result.json`
- `artifacts/nightly-load-smoke-result.json`
- `artifacts/nightly-metrics-smoke-result.json`

默认写入：

- GitHub Actions 中写入 `$GITHUB_STEP_SUMMARY`
- 本地没有 `$GITHUB_STEP_SUMMARY` 时输出到控制台

也可以显式写入文件：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\write-acceptance-summary.ps1 `
  -AcceptanceResultFile artifacts\nightly-acceptance-result.json `
  -LoadResultFile artifacts\nightly-load-smoke-result.json `
  -MetricsResultFile artifacts\nightly-metrics-smoke-result.json `
  -OutputFile artifacts\nightly-summary.md
```

## Summary 内容

摘要包含：

- 总览状态表：attempt resilience、load smoke、metrics smoke。
- 一次性夹具信息：模式、attemptId、examId、学生用户名、清理策略。
- 压测摘要：并发数、迭代数、成功数、失败数、平均耗时、样例错误。
- 指标摘要：观察到的 operation 总量和 operation/outcome 总量。

压测结果缺失时不会失败，因为 `submit_replay=true` 模式下会跳过非破坏压测。

## Nightly 接入

`nightly-acceptance.yml` 新增：

```text
Write acceptance summary
```

该步骤使用 `if: always()`，即使前面的验收失败，也会尽量把已经生成的 JSON 汇总到 Step Summary 中。

## 验收标准

- Summary 脚本可以读取三份 JSON 并生成 Markdown。
- Summary 中不输出 `studentPassword`。
- Nightly 页面能直接看到关键结果，不必先下载 artifact。
- 失败场景下，缺失文件会显示为 missing/unreadable，而不是遮蔽原始失败原因。

## 后续增强

- 将 summary 同步写入 artifact，作为发布验收记录。
- 从 JSON 中计算 P95/P99 后展示。
- 将多次 nightly 的 JSON 汇总为趋势图。
