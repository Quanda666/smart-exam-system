# 云端优先验证与部署方案

本文档用于在尽量不依赖本地 Docker Desktop、本地 MySQL 的前提下，完成智慧在线考试系统的后端、数据库、前端联调验证。

## 推荐优先级

| 优先级 | 方案 | 适用目标 | 推荐原因 |
|---|---|---|---|
| 1 | GitHub Actions + MySQL 服务容器 | 自动验证后端、数据库脚本、前端构建 | 最省事、免费额度够课程项目使用、不依赖本地环境 |
| 2 | Railway | 后端 + MySQL + 前端部署 | 对全栈部署友好，能快速创建 MySQL，但免费额度和政策可能变化 |
| 3 | Render + 外部 MySQL | 后端和前端稳定展示 | Web Service 部署简单，但免费 MySQL 通常需要外部服务 |
| 4 | GitHub Codespaces | 云端开发与手动验证 | 环境统一，适合课堂演示前临时验证，但免费时长有限 |
| 5 | Fly.io / Koyeb | 容器化后端部署 | 更接近生产容器部署，但数据库和网络配置成本略高 |

## 方案 A：GitHub Actions 自动化验证（最推荐）

已提供工作流文件：[`cloud-verify.yml`](../.github/workflows/cloud-verify.yml)。

### 验证内容

1. 启动 MySQL 8.4 服务容器。
2. 执行 [`schema.sql`](../database/schema.sql) 和 [`seed.sql`](../database/seed.sql)。
3. 执行后端 Maven 测试。
4. 启动后端服务并访问 `/api/health`。
5. 校验 `/api/health` 中数据库 `connected=true`。
6. 校验 `/api/ai/status` 中 AI 模块为模拟模式。
7. 执行前端 `npm ci` 和 `npm run build`。

### 操作步骤

1. 将代码推送到 GitHub `main` 分支。
2. 打开 GitHub 仓库的 Actions 页面。
3. 找到 `Cloud verification` 工作流。
4. 等待自动运行，或手动点击 `Run workflow`。
5. 通过后即可把 Actions 结果截图放入 [`docs/report-assets`](report-assets)。

### 优点

- 不需要本地 Docker Desktop 正常运行。
- 不需要本地安装 MySQL。
- 每次提交都能复现验证。
- 适合实训报告“系统测试与部署验证”章节。

### 可能踩坑

- GitHub Actions 需要仓库启用 Actions。
- 首次运行需要拉取依赖，耗时可能较长。
- MySQL 服务容器启动需要等待健康检查完成。

## 方案 B：Railway 全栈部署

Railway 适合快速创建后端服务和 MySQL 数据库。

### 后端部署

1. 新建 Railway Project。
2. 连接 GitHub 仓库。
3. 选择 `backend` 目录作为服务根目录。
4. 设置构建命令：

```bash
mvn -B clean package -DskipTests
```

5. 设置启动命令：

```bash
java -jar target/*.jar
```

6. 设置环境变量：

```text
MYSQL_URL=jdbc:mysql://<Railway MySQL Host>:<Port>/<Database>?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=<Railway MySQL User>
MYSQL_PASSWORD=<Railway MySQL Password>
AI_MOCK_ENABLED=true
OPENAI_MODEL=gpt-4o-mini
```

### 数据库初始化

Railway 创建 MySQL 后，可在 Railway 控制台或本地使用数据库连接工具执行：

```bash
mysql -h <host> -P <port> -u <user> -p < database/schema.sql
mysql -h <host> -P <port> -u <user> -p < database/seed.sql
```

### 前端部署

1. 新建 Railway Static 或 Web 服务。
2. 根目录选择 `frontend`。
3. 构建命令：

```bash
npm ci && npm run build
```

4. 输出目录：

```text
dist
```

如前端单独部署，需要配置前端调用后端公网域名；后续可把 API Base URL 抽成环境变量。

### 优缺点

- 优点：最接近真实部署，数据库创建方便。
- 缺点：免费额度和休眠策略会变化，部分账户可能需要绑定支付方式。

## 方案 C：Render + 外部 MySQL

Render 适合部署后端 Web Service 和前端 Static Site，但免费 MySQL 资源通常需要外部服务补充。

### 后端 Render Web Service

- Root Directory：`backend`
- Build Command：

```bash
mvn -B clean package -DskipTests
```

- Start Command：

```bash
java -jar target/*.jar
```

- Health Check Path：

```text
/api/health
```

- 环境变量同 Railway。

### 前端 Render Static Site

- Root Directory：`frontend`
- Build Command：

```bash
npm ci && npm run build
```

- Publish Directory：

```text
dist
```

### 数据库选择

可选择 Railway MySQL、Aiven MySQL、PlanetScale 兼容方案或其他云 MySQL。若没有长期免费 MySQL，则使用 GitHub Actions 完成自动化验证即可。

## 方案 D：GitHub Codespaces

Codespaces 适合统一开发环境，后续可新增 [`devcontainer.json`](../.devcontainer/devcontainer.json)。当前阶段建议先用 GitHub Actions 验证，不急于引入 devcontainer。

### 适合场景

- 组员电脑环境不一致。
- 课堂演示前需要快速打开云端 VS Code。
- 本地 Docker Desktop 不稳定。

### 注意事项

- 免费时长有限。
- 如果在 Codespaces 内启动 MySQL 容器，仍需要容器运行资源。

## 方案 E：docker-compose 参考方案

已提供 [`docker-compose.yml`](../docker-compose.yml)，用于 Docker 环境可用时一键启动 MySQL、后端和前端。

```bash
docker compose up --build
```

启动后访问：

```text
前端：http://127.0.0.1:3000
后端：http://127.0.0.1:8080/api/health
AI状态：http://127.0.0.1:8080/api/ai/status
```

当前本机 Docker Desktop Engine 卡在 `starting` 时，不建议强行执行 Compose；应优先使用 GitHub Actions。

## 推荐最终执行路线

1. 使用 GitHub Actions 完成数据库脚本、后端、前端的自动化验证。
2. 若需要公开演示，再选择 Railway 部署后端和 MySQL。
3. 前端可部署到 Railway、Render Static Site、Vercel 或 Netlify。
4. 实训报告中把 GitHub Actions 截图作为“云端自动化测试验证”证据，把 Railway/Render 截图作为“部署展示”证据。

## 当前项目已具备的配置

| 文件 | 用途 |
|---|---|
| [`backend/Dockerfile`](../backend/Dockerfile) | 后端容器镜像构建 |
| [`frontend/Dockerfile`](../frontend/Dockerfile) | 前端 Nginx 静态服务镜像构建 |
| [`frontend/nginx.conf`](../frontend/nginx.conf) | 前端容器代理 `/api` 到后端容器 |
| [`docker-compose.yml`](../docker-compose.yml) | MySQL、后端、前端三服务编排 |
| [`.github/workflows/cloud-verify.yml`](../.github/workflows/cloud-verify.yml) | GitHub Actions 云端自动化验证 |
| [`database/schema.sql`](../database/schema.sql) | 数据库结构初始化 |
| [`database/seed.sql`](../database/seed.sql) | 演示数据初始化 |
