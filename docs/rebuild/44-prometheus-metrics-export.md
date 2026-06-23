# 44. Prometheus 指标导出

## 目标

- 将第 43 批的考试业务指标从 Actuator metrics 查询推进到 Prometheus 可抓取格式。
- 为测试环境压测和生产监控提供统一 scrape endpoint。
- 保持默认开启，并允许通过环境变量关闭。

## 后端变更

`backend/pom.xml` 新增：

- `io.micrometer:micrometer-registry-prometheus`

`application.yml` 调整：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  prometheus:
    metrics:
      export:
        enabled: ${PROMETHEUS_METRICS_ENABLED:true}
```

新增端点：

- `GET /actuator/prometheus`

该端点输出 Prometheus text exposition 格式，包含：

- JVM 指标
- Tomcat 指标
- Hikari 连接池指标
- Spring/HTTP 基础指标
- 第 43 批新增的考试业务指标

## 关键指标

考试业务指标：

```text
smart_exam_exam_operation_total
smart_exam_exam_operation_duration_seconds_count
smart_exam_exam_operation_duration_seconds_sum
smart_exam_exam_operation_duration_seconds_max
```

常用标签：

- `application="smart-exam-backend"`
- `operation="saveDraft"`
- `operation="submitExam"`
- `outcome="error"`
- `outcome="replayed"`

Prometheus 会将 Micrometer 指标名中的 `.` 转为 `_`，因此：

- `smart_exam.exam.operation.total` -> `smart_exam_exam_operation_total`
- `smart_exam.exam.operation.duration` -> `smart_exam_exam_operation_duration_seconds`

## 健康脚本

`scripts/check-ops-health.ps1` 新增：

- `-ExpectPrometheus`

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-ops-health.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -ExpectPrometheus
```

脚本会额外验证：

- `/actuator/prometheus` HTTP 2xx。
- 响应包含 `# HELP` 或 `# TYPE`。
- 响应头包含 `X-Request-Id`。

## Prometheus Scrape 示例

```yaml
scrape_configs:
  - job_name: smart-exam-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - smart-exam-backend:8080
```

## 压测查询示例

提交接口错误数：

```promql
sum(rate(smart_exam_exam_operation_total{operation="submitExam",outcome="error"}[5m]))
```

草稿保存吞吐：

```promql
sum(rate(smart_exam_exam_operation_total{operation="saveDraft"}[1m]))
```

提交耗时平均值：

```promql
sum(rate(smart_exam_exam_operation_duration_seconds_sum{operation="submitExam"}[5m]))
/
sum(rate(smart_exam_exam_operation_duration_seconds_count{operation="submitExam"}[5m]))
```

Redis 草稿刷盘失败：

```promql
sum(rate(smart_exam_exam_operation_total{operation="flushRedisDrafts",outcome=~"failed|partial|error"}[5m]))
```

## 验收点

- 应用依赖中存在 `micrometer-registry-prometheus`。
- `/actuator/prometheus` 已被 Actuator 暴露。
- `PROMETHEUS_METRICS_ENABLED=false` 时可关闭 Prometheus export。
- 执行一次考试链路后，Prometheus 输出中可看到 `smart_exam_exam_operation_*` 指标。
- 指标标签不包含学生、考试、答卷、IP、请求 ID 等高基数字段。
