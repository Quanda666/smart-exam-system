# 45. Docker Compose 部署与可观测预检

## 目标

本批次把本地/测试环境的部署底座补成可自查的组合：

- MySQL 作为主业务库。
- Redis 承接考试草稿缓存、写回恢复和后续会话/缓存能力。
- Spring Boot 后端暴露健康检查、追踪头、业务指标和 Prometheus 指标。
- Vue 前端由 Nginx 容器提供静态访问。
- Prometheus 抓取 `/actuator/prometheus`，用于压测和运维看板。
- 提供部署前预检脚本，尽早发现密码、端口、文件和 Compose 配置问题。

## Compose 服务

`docker-compose.yml` 当前包含：

| 服务 | 容器 | 默认端口 | 说明 |
| --- | --- | --- | --- |
| mysql | `smart-exam-mysql` | `3306` | 初始化 `schema.sql` 和 `data.sql` |
| redis | `smart-exam-redis` | `6379` | AOF 持久化，支持可选密码 |
| backend | `smart-exam-backend` | `8080` | Spring Boot API、Actuator、Prometheus metrics |
| frontend | `smart-exam-frontend` | `3000` | Nginx 托管 Vue 构建产物 |
| prometheus | `smart-exam-prometheus` | `9090` | 抓取后端指标 |

后端在 Compose 中会等待 MySQL 和 Redis 健康后启动，前端和 Prometheus 会等待后端健康后启动。

## 环境变量边界

数据库密码拆分为两类：

- `MYSQL_PASSWORD`：应用数据库用户密码，供 MySQL 初始化应用用户和后端连接使用。
- `MYSQL_ROOT_PASSWORD`：MySQL root 账号密码，仅用于容器内部 root 账号。

本地示例值可以用于一次性开发环境，但测试/生产环境必须替换：

- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `REDIS_PASSWORD`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `OPENAI_API_KEY`
- `RESEND_API_KEY`

生产环境建议把 `DB_INIT_MODE` 调整为 `never`，由受控迁移脚本管理表结构，避免应用启动时重复执行初始化 SQL。

## 启动流程

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File scripts\check-deploy-config.ps1 -EnvFile .env
docker compose --env-file .env up -d --build
```

启动后检查：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-ops-health.ps1 `
  -BaseUrl http://127.0.0.1:8080 `
  -ExpectPrometheus
```

常用入口：

- 前端：`http://127.0.0.1:3000`
- 后端健康：`http://127.0.0.1:8080/api/health`
- Actuator 健康：`http://127.0.0.1:8080/actuator/health`
- Prometheus 指标：`http://127.0.0.1:8080/actuator/prometheus`
- Prometheus UI：`http://127.0.0.1:9090`

## 部署预检脚本

新增脚本：

```powershell
scripts\check-deploy-config.ps1
```

能力：

- 校验 `.env` 或 `.env.example` 中的关键变量。
- 校验端口值范围，并提示端口冲突。
- 提示弱密码和 root/应用用户共用密码。
- 校验 `docker-compose.yml` 中 Redis、Prometheus、MySQL root 密码变量是否存在。
- 校验 `deploy/prometheus.yml` 抓取 `backend:8080/actuator/prometheus`。
- Docker CLI 可用时执行 `docker compose --env-file <env> config`。

示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-deploy-config.ps1 -UseExample -SkipDockerConfig
powershell -ExecutionPolicy Bypass -File scripts\check-deploy-config.ps1 -EnvFile .env
powershell -ExecutionPolicy Bypass -File scripts\check-deploy-config.ps1 -EnvFile .env -Strict
```

`-Strict` 会把弱密码、端口冲突、数据库 root 和应用用户密码相同等问题从警告升级为失败，适合 CI 或测试环境。

## 可观测验收

Prometheus 抓取目标：

```yaml
scrape_configs:
  - job_name: smart-exam-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - backend:8080
```

验收点：

- `/api/health` 返回成功，并带 `X-Request-Id`。
- `/actuator/health/liveness` 和 `/actuator/health/readiness` 可访问。
- `/actuator/prometheus` 返回 `# HELP` 或 `# TYPE`。
- 执行一次考试作答、保存草稿、提交、监考事件上报后，Prometheus 中能看到 `smart_exam_exam_operation_*` 指标。
- 后端慢请求日志带 `traceId`，前端错误对象保留 `requestId`。

## 生产注意事项

- 不要在生产环境使用 `.env.example` 的密码。
- `CORS_ALLOWED_ORIGIN_PATTERNS` 必须收敛到正式域名。
- `PROMETHEUS_METRICS_ENABLED` 默认开启，生产环境应通过网络策略限制访问。
- `REDIS_PASSWORD` 建议开启，并确保后端和 Redis 健康检查使用同一密码。
- 文件上传后续应迁移到对象存储，本地容器磁盘不能作为长期业务文件存储。
- 夹具接口必须保持 `system.testFixtureEnabled=false`，只在验收环境临时开启。
