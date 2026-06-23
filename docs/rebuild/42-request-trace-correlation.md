# 42. 前后端请求追踪协同

## 目标

- 将第 41 批的后端 `X-Request-Id` 扩展到前端请求封装。
- 前端每次 API 请求主动生成 request id，后端继续透传或生成兜底值。
- API 失败时，错误对象携带 request id，页面现有错误提示会显示该 ID，便于排查。
- 后端增加慢请求 WARN 日志，为压测和真实考试故障定位提供第一层信号。

## 前端变更

`frontend/src/api/request.ts`：

- `ApiResponse<T>` 新增：
  - `requestId`
  - `responseTimeMs`
- `ApiError` 新增：
  - `requestId`
  - `responseTimeMs`
  - `rawMessage`
- `getJson` / `postJson` / `putJson` / `deleteJson` 统一发送 `X-Request-Id`。
- `postForm` 和 `downloadFile` 同样发送 `X-Request-Id`。
- 成功响应会从响应头回填 `requestId` 和 `responseTimeMs`。
- 失败响应会把 request id 拼进 `Error.message`，现有 `ElMessage.error(error.message)` 可直接展示。
- CORS 暴露 `X-Request-Id`、`X-Response-Time-Ms` 和 `Content-Disposition`，跨域部署时前端也能读取追踪头和下载文件名。

示例错误：

```text
草稿保存失败（请求ID：web-9f1c2a...）
```

## 后端变更

`RequestTraceFilter`：

- 保持 `X-Request-Id` / `X-Correlation-Id` 透传。
- 响应继续返回：
  - `X-Request-Id`
  - `X-Response-Time-Ms`
- 日志 MDC 继续写入 `traceId`。
- 新增慢请求阈值：

```yaml
smart-exam:
  request-tracing:
    slow-warning-ms: ${REQUEST_SLOW_WARNING_MS:1000}
```

当请求耗时大于等于阈值时，日志级别从 `INFO` 提升为 `WARN`：

```text
slow request method=POST uri=/api/exams/attempt/1/save status=200 durationMs=1530 thresholdMs=1000 remote=127.0.0.1
```

## 运维排查流程

1. 学生或教师截图/反馈页面错误提示中的请求 ID。
2. 日志平台按 `traceId=<请求ID>` 搜索。
3. 定位该请求的接口、耗时、HTTP 状态、远端地址。
4. 若涉及作答保存或提交，再结合 attemptId、submitToken、draftRevision 查询业务状态。

## 验收点

- 浏览器发出的 API 请求包含 `X-Request-Id`。
- 后端响应包含同一个 `X-Request-Id`。
- 跨域请求下，浏览器 JS 可以读取 `X-Request-Id` 和 `X-Response-Time-Ms`。
- 前端 `ApiError` 包含 `requestId` 和 `responseTimeMs`。
- 页面现有错误提示能展示请求 ID。
- 超过 `REQUEST_SLOW_WARNING_MS` 的请求在后端日志中为 `WARN`。
