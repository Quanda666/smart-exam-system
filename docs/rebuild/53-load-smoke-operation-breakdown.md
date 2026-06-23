# 53. Load Smoke Operation Breakdown

## 目标

本批次把 load smoke 从全局延迟统计推进到接口级诊断：

- 每次请求记录 operation、路径、耗时和成功状态。
- JSON 汇总输出 `operationMetrics`。
- nightly summary 展示每个 operation 的请求数、失败数、平均、P95、P99、最大耗时。
- 修正 `success=false` 被重复计入失败和耗时的问题。

## Operation 映射

`scripts\run-exam-load-smoke.ps1` 当前识别：

- `/api/auth/login` -> `login`
- `/api/exams/attempt/{id}/start` -> `startExam`
- `/api/exams/attempt/{id}/save` -> `saveDraft`
- `/api/exams/attempt/{id}/heartbeat` -> `attemptHeartbeat`
- `/api/exams/attempt/{id}/submit` -> `submitExam`

未知路径保留原始 path，方便后续扩展时先暴露数据。

## JSON 新增字段

load smoke 结果新增：

```json
{
  "operationMetrics": [
    {
      "operation": "saveDraft",
      "total": 10,
      "success": 10,
      "failed": 0,
      "averageMs": 82.4,
      "p95Ms": 130,
      "p99Ms": 155,
      "maxMs": 155
    }
  ],
  "workers": [
    {
      "requests": [
        {
          "operation": "saveDraft",
          "method": "POST",
          "path": "/api/exams/attempt/1/save",
          "elapsedMs": 80,
          "success": true
        }
      ]
    }
  ]
}
```

全局字段 `averageRequestLatencyMs`、`p95RequestLatencyMs`、`p99RequestLatencyMs` 仍保留，用于快速判断整体是否退化；`operationMetrics` 用于定位具体慢接口。

## Summary 展示

`scripts\write-acceptance-summary.ps1` 在 Load Smoke 区块新增 operation 表：

```text
Operation | Total | Failed | Avg Ms | P95 Ms | P99 Ms | Max Ms
```

nightly 查看顺序建议：

1. 先看总览 PASS/FAIL。
2. 再看全局 P95/P99。
3. 如果全局变慢，看 operation 表定位是 `startExam`、`saveDraft`、`attemptHeartbeat` 还是 `submitExam`。
4. 如果某个 operation 失败数大于 0，再结合 sample errors 和 backend log 排查。

## 验收标准

- load smoke 成功或失败时都尽量输出 `operationMetrics`。
- `success=false` 只计为一次失败请求。
- summary 中能看到 operation 延迟表。
- 密码仍只允许出现在 GitHub 输出变量内部，不允许出现在 summary 文本。

## 后续增强

- 为不同 operation 设置独立阈值，例如 `saveDraft` P95 不超过 800ms。
- 将 operationMetrics 转换为 Prometheus push 或长期趋势 artifact。
- 在真实高并发压测中按 operation 生成慢请求样本，保留 request id 方便关联后端结构化日志。
