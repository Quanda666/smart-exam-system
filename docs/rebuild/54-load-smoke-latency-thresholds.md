# 54. Load Smoke Latency Thresholds

## 目标

本批次把 load smoke 从“展示延迟”推进到“延迟退化自动失败”：

- 支持全局平均、P95、P99 请求耗时阈值。
- 支持按 operation 配置 P95、P99、最大耗时阈值。
- 违规信息写入 JSON 和 nightly summary。
- 先写结果文件，再让脚本失败，确保失败场景也能下载证据。

## 新增参数

`scripts\run-exam-load-smoke.ps1` 新增：

```powershell
-MaxAverageRequestLatencyMs 0
-MaxP95RequestLatencyMs 5000
-MaxP99RequestLatencyMs 8000
-MaxOperationP95Ms "startExam=5000,saveDraft=5000,attemptHeartbeat=5000"
-MaxOperationP99Ms "startExam=8000,saveDraft=8000,attemptHeartbeat=8000"
-MaxOperationMaxMs "saveDraft=10000"
```

阈值为 `0` 表示关闭对应校验。operation 阈值使用逗号或分号分隔的 `operation=milliseconds` 格式。

## JSON 新增字段

load smoke 结果新增：

```json
{
  "thresholds": {
    "maxAverageRequestLatencyMs": 0,
    "maxP95RequestLatencyMs": 5000,
    "maxP99RequestLatencyMs": 8000,
    "maxOperationP95Ms": "saveDraft=5000",
    "maxOperationP99Ms": "saveDraft=8000",
    "maxOperationMaxMs": ""
  },
  "thresholdViolations": [
    {
      "scope": "operation",
      "operation": "saveDraft",
      "metric": "p95Ms",
      "actualMs": 6200,
      "thresholdMs": 5000
    }
  ]
}
```

`success` 现在必须同时满足：

- `totalFailed == 0`
- `thresholdViolations` 为空

## Nightly 策略

`.github/workflows/nightly-acceptance.yml` 默认启用：

- 全局 P95 <= `5000ms`
- 全局 P99 <= `8000ms`
- `startExam`、`saveDraft`、`attemptHeartbeat` 的 P95/P99 同步使用上述阈值

`workflow_dispatch` 可临时调整：

- `max_p95_request_latency_ms`
- `max_p99_request_latency_ms`

这组阈值偏宽松，目的不是替代正式压测，而是让 nightly 能捕捉明显退化。后续应基于多次运行数据逐步收紧。

## Summary 展示

`scripts\write-acceptance-summary.ps1` 在 Load Smoke 区块新增阈值违规表：

```text
Threshold Scope | Operation | Metric | Actual Ms | Threshold Ms
```

当 nightly 失败时，先看这张表判断是全局退化还是具体接口退化，再结合 operation latency breakdown 和 backend log 追踪。

## 验收标准

- 延迟超过阈值时脚本退出失败。
- 失败前必须写入 load smoke JSON。
- summary 必须展示阈值违规明细。
- 没有配置阈值时保持兼容，不因为缺省阈值失败。

## 后续增强

- 为 `submitExam` 单独配置更宽阈值，服务破坏性提交回放。
- 将阈值按环境拆分为 local、nightly、staging、production smoke。
- 把 thresholdViolations 接入告警通知，形成性能退化提醒闭环。
