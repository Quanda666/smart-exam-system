# 47. CI 质量门禁与一键本地验收

## 目标

本批次把前面已经沉淀的构建、部署预检和运维检查串成可重复执行的门禁：

- 本地用一个 PowerShell 脚本跑基础质量检查。
- GitHub Actions 使用 MySQL、Redis 服务执行后端测试和运行时健康 smoke。
- CI 检查 Prometheus 指标端点，确保可观测能力没有退化。
- 修复旧 workflow 中的编码污染和不存在的初始化脚本引用。

## 本地质量门禁

新增：

```powershell
scripts\run-quality-gates.ps1
```

默认执行：

- PowerShell 脚本语法检查。
- `check-deploy-config.ps1 -UseExample`。
- `frontend` 的 `npm run build`。
- 后端编译检查：有 Maven 时执行 `mvn -B -DskipTests compile`，没有 Maven 时执行现有 `javac` 粗筛。
- `git diff --check`。

常用示例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

本机没有 Docker 时：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1 -SkipDockerConfig
```

快速检查脚本和配置：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1 `
  -SkipFrontendBuild `
  -SkipBackendCompile
```

## GitHub Actions

重写：

```text
.github/workflows/cloud-verify.yml
```

CI 服务：

- MySQL 8.4
- Redis 7.2

CI 步骤：

1. Checkout。
2. 安装 JDK 17。
3. 安装 Node.js 20。
4. `npm ci`。
5. 校验 PowerShell 脚本语法。
6. 执行 `check-deploy-config.ps1 -UseExample`。
7. 执行 `mvn -B test`。
8. 执行 `npm run build`。
9. 启动后端并检查：
   - `/api/health`
   - `/actuator/health/liveness`
   - `/actuator/health/readiness`
   - `/actuator/prometheus`
10. 执行 `check-ops-health.ps1 -ExpectPrometheus`。

## 边界说明

- CI 使用 `DB_INIT_MODE=always`，由 Spring Boot 启动时加载 `schema.sql` 和 `data.sql`。
- CI 开启 `AI_MOCK_ENABLED=true`，不依赖外部 AI 服务。
- CI 开启 Redis draft cache 和 Redis health，用于尽早发现 Redis 配置回归。
- CI 不执行破坏性提交压测和数据库恢复。
- 备份目录 `backups/` 已加入 `.gitignore`。

## 验收标准

- 本地 `run-quality-gates.ps1` 可以完成所有可用检查。
- GitHub Actions 不再引用不存在的 `docs/init.sql`。
- workflow 文件没有编码污染导致的 shell 引号损坏。
- 后端测试、前端构建、部署配置、脚本语法和 Prometheus smoke 被同一个 CI 串联。

## 后续增强

- 加入 Testcontainers MySQL/Redis，减少 CI 对 service 初始化方式的依赖。
- 增加 Playwright E2E，覆盖管理员建数据、教师组卷发布、学生作答、阅卷发布、学生查成绩。
- 加入 nightly job，使用一次性夹具执行 `run-attempt-resilience-acceptance.ps1` 和非破坏性 `run-exam-load-smoke.ps1`。
