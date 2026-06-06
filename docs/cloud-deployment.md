# 云端部署方案（Railway 一键自动部署）

本项目已经内置了根目录的 [`Dockerfile`](../Dockerfile)，支持 **Railway 连接 GitHub 仓库后自动识别、自动构建、一键部署**。

我们采用 **单服务部署架构（前端打包进后端，由后端容器统一托管提供静态资源与 API 服务）**。这意味着：
- **更低的资源消耗**：在 Railway 上你只需要部署 **1 个 Web 服务** + **1 个 MySQL 数据库**，完全在 Railway 的免费额度内。
- **更简单的部署配置**：不需要手动拆分 3 个服务，也不需要配置复杂的内网 Nginx 代理和 IPv6 域名，推送到仓库即自动上线。
- **完美的 SPA 体验**：后端已集成单页路由重定向，浏览器直接刷新、前进、后退均能完美渲染 Vue 页面。

---

## 一、部署配置清单

| 路径/文件 | 角色/用途 |
|---|---|
| [`Dockerfile`](../Dockerfile) | **全局单服务构建描述**：Stage 1 构建前端 Vue (npm) → Stage 2 将产物打包进 Spring Boot 并完成 jar 构建 → Stage 3 极简 JRE 镜像部署运行。 |
| [`schema.sql`](../backend/src/main/resources/db/schema.sql)、[`data.sql`](../backend/src/main/resources/db/data.sql) | 后端启动时**自动**检测并执行，初始化数据库结构和系统初始数据，无需手动导入脚本。 |
| [`application.yml`](../backend/src/main/resources/application.yml) | 已配置 `spring.sql.init.mode=always`，配合连接池在服务初次启动时，自动导入库表。 |

---

## 二、Railway 一键部署步骤

### 1. 创建 MySQL 数据库

1. 注册并登录 [Railway](https://railway.app)。
2. 点击 **New Project** → 选择 **Provision MySQL**，一键拉起一个托管 MySQL 数据库。
3. **关键：初始化空库**
   默认建出来的连接可能只具有全局权限，而项目中写死使用了 `smart_exam_system` 作为库名。请在 Railway 中打开刚才创建的 **MySQL 服务面板**，切换到 **Query** 标签页，执行以下 SQL 命令：
   ```sql
   CREATE DATABASE IF NOT EXISTS smart_exam_system
     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

### 2. 部署 Web 主服务

1. 在当前 Project 页面，点击 **New** → **GitHub Repo**。
2. 选择本项目的 GitHub 仓库（无需填写 Root Directory、Build Command、Start Command，Railway 会自动读取根目录下的 `Dockerfile` 启动多阶段构建）。
3. **配置环境变量 (Variables)**
   在 Web 服务的 **Variables** 选项卡中，点击 **Add Raw** 并粘贴以下变量（其中 `${{MySQL.xxx}}` 为 Railway 内部对 MySQL 服务的动态引用）：

   ```properties
   MYSQL_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/smart_exam_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
   MYSQL_USERNAME=${{MySQL.MYSQLUSER}}
   MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   PORT=8080
   AI_MOCK_ENABLED=true
   OPENAI_MODEL=gpt-4o-mini
   ```

4. **绑定公网域名**
   在 Web 服务的 **Settings** 页面，找到 **Networking** 栏目，点击 **Generate Domain** 生成公网访问域名（格式如 `https://xxx.up.railway.app`）。

### 3. 等待并验证

1. 每次你向 GitHub 推送代码时，Railway 都会自动拉取最新提交，并行构建前端 Vue 与后端 Spring Boot，最终生成一体化容器运行。
2. 部署成功后，直接用浏览器访问生成的公网域名，即可看到系统登录页。
3. **初始管理员账号**：`admin` / `admin123`（在第一次部署完成后，请尽快在后台修改默认密码）。
4. 访问 `https://<你的域名>/api/health`，若返回 `{"success":true,"data":{"status":"UP"}}` 说明系统健康且数据库已成功连接并导入。

---

## 三、常见排查与问答

| 遇到的现象 | 原因分析与解决方法 |
|---|---|
| **部署卡在构建阶段 / Node、Maven 构建慢** | 这是 Railway 平台公共构建机的调度速度问题，多阶段构建正常需要 3-5 分钟，请耐心等待构建流程。 |
| **启动日志提示：Connection refused / 数据库连接失败** | 请检查 MySQL 服务是否正常启动，以及环境变量 `MYSQL_URL` 中是否包含了完整的库名 `smart_exam_system`，且该库已按 1.3 节所述语句成功创建。 |
| **在前端页面里，刷新浏览器时出现 404** | 项目已在后端引入 [`SpaController.java`](../backend/src/main/java/com/smartexam/controller/SpaController.java)。该控制器将所有非静态资源、非 `/api` 的通用页面请求都重定向转发到 `index.html`。如果遇到 404，请确认该控制器代码已推送到仓库。 |
| **本地仍然可以用前后端分离开发吗？** | **完全可以**。本地开发不受单容器部署影响：前端执行 `npm run dev`（监听 `3000`），后端在 IDE 启动 `SmartExamApplication`（监听 `8080`），前端的 Vite 代理仍可完美解决跨域。 |
| **是否还需要配置 CORS 跨域？** | **不需要**。因为线上部署后，浏览器是在同域名、同端口下请求前端页面和 `/api` 接口的，完全没有跨域限制，数据传输更加安全高效。 |
