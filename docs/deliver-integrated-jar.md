# 一体化 Jar 打包交付说明

> 本文档说明如何将项目交付给老师验收。**推荐 Docker Compose 一键启动方式（老师只需装 Docker Desktop，双击 bat 即可），** 备选纯 Jar 方式适合老师已有 JDK + MySQL 的场景。
>
> 最近更新：2026-06-19

---

## 推荐方式：Docker Compose 一键启动（真正双击即用）

老师只需安装 **Docker Desktop**，双击 [`交付给老师.bat`](../交付给老师.bat)，等待几分钟，浏览器自动打开系统。

### 交付包结构

打包以下文件放进一个文件夹（如 `smart-exam-交付包/`）发给老师：

```
smart-exam-交付包/
├── 交付给老师.bat                  ← 双击启动
├── docker-compose.teacher.yml      ← 简化编排（只用一体化 Dockerfile）
├── Dockerfile                      ← 一体化多阶段构建
├── backend/
│   ├── pom.xml
│   └── src/main/resources/db/
│       ├── schema.sql
│       └── data.sql
└── frontend/
    ├── package.json
    ├── package-lock.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── tsconfig.node.json
    ├── index.html
    └── src/...
```

> 老师**不需要**安装 Node.js、Maven、JDK、MySQL 或任何其他依赖。Docker 自动完成前端 npm build、后端 mvn package、MySQL 初始化。

### 老师操作步骤

| 步骤 | 操作 | 说明 |
|---|---|---|
| 1 | 安装 Docker Desktop | [docker.com 下载](https://www.docker.com/products/docker-desktop/)，安装后启动，等右下角鲸鱼变绿 |
| 2 | 双击 `交付给老师.bat` | 自动检测 Docker → 构建镜像 → 拉起 MySQL/Redis/应用 → 打开浏览器 |
| 3 | 等待 2-5 分钟 | 首次需编译前端+后端，后续启动只需数秒 |
| 4 | 浏览器自动打开 `http://localhost:8080` | `admin / admin123` 登录 |

### 启动后效果

| 服务 | 地址 | 说明 |
|---|---|---|
| 系统主页 | `http://localhost:8080` | Vue SPA + Spring Boot API，同一端口 |
| 健康检查 | `http://localhost:8080/api/health` | 返回 `{"status":"UP"}` |
| MySQL | `localhost:3306` | root / root，数据库 `smart_exam_system` |
| Redis | `localhost:6379` | 草稿缓存、心跳恢复 |

### 老师如何停止 / 重置

```bash
# 停止（数据保留，下次启动秒开）
docker-compose -f docker-compose.teacher.yml down

# 彻底清空（恢复出厂状态）
docker-compose -f docker-compose.teacher.yml down -v

# 重新启动
双击 交付给老师.bat
```

### 可选：配置 AI

如果老师有 OpenAI Key，编辑 `docker-compose.teacher.yml` 中 `app.environment` 段：

```yaml
AI_MOCK_ENABLED: "false"
OPENAI_API_KEY: "sk-xxx"
OPENAI_MODEL: "gpt-4o-mini"
```

不改则使用模拟输出。

---

## 备选方式：纯 Jar 交付（适合老师已有 JDK + MySQL）

如果老师不愿装 Docker，且已有 JDK 17 + MySQL 8 环境。

### 你如何构建一体化 Jar

你已安装 Node.js + Maven + JDK，四步完成：

```powershell
# 1. 构建前端
cd frontend
npm install
npm run build

# 2. 将前端产物复制到后端 static 目录（含前端路由 fallback）
Remove-Item -Recurse -Force ../backend/src/main/resources/static -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force ../backend/src/main/resources/static | Out-Null
Copy-Item -Recurse dist/* ../backend/src/main/resources/static/

# 3. 确保 SPA 路由 fallback：所有非 API 请求返回 index.html
# 在 backend/src/main/resources/static/ 下创建 index.html 已包含 Vue Router

# 4. 打包
cd ../backend
mvn clean package -DskipTests

# 产出物: backend/target/smart-exam-backend-0.1.0-SNAPSHOT.jar
```

### 构建产物

| 属性 | 值 |
|---|---|
| 文件名 | `smart-exam-backend-0.1.0-SNAPSHOT.jar` |
| 端口 | `8080`（`--server.port=9090` 覆盖） |
| 内嵌前端 | `/` → `index.html` → Vue SPA |
| API | `/api/*` |

### 老师操作（纯 Jar）

```bash
# 1. 建库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS smart_exam_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 启动（默认 root/root@localhost:3306）
java -jar smart-exam-backend-0.1.0-SNAPSHOT.jar

# 非默认 MySQL 账号：
java -jar smart-exam-backend-0.1.0-SNAPSHOT.jar --MYSQL_USERNAME=myuser --MYSQL_PASSWORD=mypassword

# 3. 打开浏览器 http://localhost:8080，admin / admin123 登录
```

---

## Docker Compose vs 纯 Jar 对比

| 维度 | Docker Compose | 纯 Jar |
|---|---|---|
| 老师需装 | **Docker Desktop（1 个安装包）** | JDK 17 + MySQL 8 + 手动建库 |
| 启动方式 | **双击 bat** | 建库 → 命令行 → 开浏览器 |
| MySQL | 容器自动拉起 + schema/data 自动导入 | 老师自己装、配密码、建库 |
| Redis | **自动拉起** | 默认不用 |
| 清理 | `docker-compose down -v` 一键 | 停 Java 进程 + 手动 DROP DATABASE |

---

## 交付前自检

- [ ] Docker Desktop 已装且能 `docker info`
- [ ] 在干净环境测试：双击 `交付给老师.bat` → 浏览器自动打开 `http://localhost:8080`
- [ ] `admin / admin123` 登录成功
- [ ] 三端菜单无 404
- [ ] 全流程：题库 → 组卷 → 考试 → 作答 → 交卷 → 阅卷 → 成绩 → 错题

---

## 相关文档

| 文档 | 说明 |
|---|---|
| [deploy-local.md](deploy-local.md) | 本地验收部署指南 |
| [test-records.md](test-records.md) | 测试与人工验收路径 |
| [rebuild/08-test-and-acceptance.md](rebuild/08-test-and-acceptance.md) | 测试与验收标准 |
