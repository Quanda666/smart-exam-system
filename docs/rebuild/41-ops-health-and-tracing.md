# 41. 运维健康检查与请求追踪

## 目标

- 让已有 Actuator 配置真正生效，补齐后端运维探活基础。
- 为每个 HTTP 请求生成或透传 `X-Request-Id`，并写入日志 MDC。
- 提供可执行脚本验证 `/api/health`、Actuator liveness/readiness 和 trace header。

## 后端变更

### Actuator

`backend/pom.xml` 新增：

- `spring-boot-starter-actuator`

`application.yml` 已暴露：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/metrics`

并补充：

- readiness group：`readinessState,db,diskSpace`
- liveness group：`livenessState,ping`
- `info.app.name`
- `info.app.version`
- Tomcat MBean registry，用于基础容器指标。

### 请求追踪

新增：

- `RequestTraceFilter`

行为：

- 优先透传请求头 `X-Request-Id`。
- 若不存在，则尝试使用 `X-Correlation-Id`。
- 若仍不存在，则生成 32 位随机 trace id。
- 所有响应都会返回 `X-Request-Id`。
- 响应会返回 `X-Response-Time-Ms`。
- 日志 MDC 写入 `traceId`，控制台日志格式包含 `traceId=%X{traceId}`。

示例日志：

```text
2026-06-17 12:00:00 [http-nio-8080-exec-1] INFO traceId=abc123 com.smartexam.config.RequestTraceFilter - request method=POST uri=/api/exams/attempt/1/save status=200 durationMs=42 remote=127.0.0.1
```

### 自定义健康接口

`/api/health` 保留，返回：

- 应用名
- 状态
- 当前时间
- 数据库连接结果

该接口用于前端轻量状态展示和旧部署脚本兼容；Actuator 用于平台级 liveness/readiness。

## 新增脚本

- `scripts/check-ops-health.ps1`

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-ops-health.ps1 `
  -BaseUrl http://127.0.0.1:8080
```

仅检查旧健康接口：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-ops-health.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -SkipActuator
```

脚本会验证：

- `/api/health` HTTP 2xx。
- `/api/health` 返回 `success=true` 和应用名。
- 响应头包含 `X-Request-Id`。
- 未跳过 Actuator 时，liveness/readiness/metrics 均可访问并返回 JSON。

## 部署建议

- 容器 livenessProbe 使用 `/actuator/health/liveness`。
- 容器 readinessProbe 使用 `/actuator/health/readiness`。
- 外部负载均衡可继续使用 `/api/health`，保持兼容。
- 日志平台按 `traceId` 建索引，排查学生作答、草稿保存、提交重放等链路时用同一个 request id 串联。

## 下一步

- 接入 Micrometer Prometheus registry 后暴露 `/actuator/prometheus`。
- 为草稿保存、提交、监考事件批量上报增加业务指标计数和耗时分布。
- 增加慢请求阈值日志，将 `durationMs` 超过配置值的请求标记为 `WARN`。
