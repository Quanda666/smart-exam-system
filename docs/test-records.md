# 测试记录

本文件用于记录每个阶段的验证方法、测试用例、执行结果和问题修复。

## 阶段 1 测试计划

| 编号 | 测试对象 | 操作 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| T1-001 | 后端健康检查 | 访问 `/api/health` | 返回 `success` 为 `true` | 已通过；后端可启动，`/api/health` 返回 `success=true`、`status=UP`。数据库当前未连接，接口已如实返回 `connected=false` |
| T1-002 | AI 状态检查 | 访问 `/api/ai/status` | 返回 AI 配置状态，不暴露 API Key | 已通过；接口返回 `mode=MOCK`、`available=true`、`apiKeyConfigured=false`，未暴露明文 API Key |
| T1-003 | 前端首页 | 启动前端并访问首页 | 页面正常展示项目基础信息 | 已通过；`127.0.0.1:3000` 返回 HTTP 200。`5173` 端口绑定被 Windows 拒绝，已将默认开发端口调整为 `3000` |
| T1-004 | 数据库脚本 | 执行 `schema.sql` 与 `seed.sql` | 基础表和演示数据创建成功 | 脚本已创建，包含用户、角色、班级、科目、知识点、AI 配置、AI 提示词模板、AI 调用日志；当前本机未找到 MySQL 客户端或 MySQL 服务，Docker 客户端存在但 Docker Desktop 后台未运行，因此暂未执行 |
| T1-005 | 项目结构检查 | 递归查看目录结构 | 基础目录和文件存在 | 已确认 `backend`、`frontend`、`database`、`docs`、`scripts`、`README.md`、`.gitignore`、`LICENSE` 等基础结构存在 |
| T1-006 | 前端构建 | 在 `frontend` 目录执行 `npm run build` | 类型检查通过并生成生产构建产物 | 构建通过；出现 Vite chunk 体积警告，阶段 1 可接受，后续按路由拆包优化 |
| T1-007 | 本地辅助脚本 | 检查 `scripts` 目录 | 存在环境检查、前端启动、后端启动脚本 | 已创建 `check-env.cmd`、`run-frontend.cmd`、`run-backend.cmd` 和 `scripts/README.md` |
| T1-008 | Git 提交与远程推送 | 初始化 Git 仓库并推送到 GitHub | 本地提交成功，远程仓库存在阶段 1 成果 | 已提交 `4774fbe chore: 初始化阶段1项目骨架`，并推送到 `https://github.com/Quanda666/smart-exam-system.git` 的 `main` 分支 |
| T1-009 | 云端自动化验证方案 | 编写 GitHub Actions MySQL 服务容器验证流程 | 不依赖本地 MySQL 和 Docker Desktop，也能验证数据库脚本、后端接口与前端构建 | 已新增 `.github/workflows/cloud-verify.yml`，推送后由 GitHub Actions 执行 |
| T1-010 | 容器化部署配置 | 编写 Dockerfile、Nginx 配置与 Compose 编排 | 支持 Docker 环境可用时启动 MySQL、后端、前端 | 已新增 `backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`、`docker-compose.yml` |

## 阶段 1 环境检查记录

| 工具 | 检查结果 | 说明 |
|---|---|---|
| Node.js | 已找到 | 路径为 `C:\Program Files\nodejs\node.exe` |
| npm | 已找到 | 路径为 `C:\Program Files\nodejs\npm.cmd` |
| Java | 已找到 | 本机存在 JDK 25 和 JDK 11 |
| javac | 已找到 | 本机存在 JDK 编译器 |
| Maven | 已找到但未加入 PATH | 可用路径为 `C:\Users\86132\.local\apache-maven-3.9.16\bin\mvn.cmd`，脚本已支持自动识别该路径 |
| Maven Daemon | 未找到 | `where mvnd` 未找到可执行文件 |
| Gradle | 未找到 | `where gradle` 未找到可执行文件 |
| 前端依赖 | 已安装 | 已生成 `frontend/package-lock.json` 与 `frontend/node_modules` |
| 前端开发服务 | 已启动 | `http://127.0.0.1:3000/` 返回 HTTP 200 |
| Docker | 客户端存在，Engine 未就绪 | Docker CLI 已找到，但 Docker Desktop 长时间处于 `starting`，本地暂不作为主要验证路径 |
| MySQL | 未找到 | 未找到 `mysql.exe`、MySQL 服务或常见安装目录 |
| Git | 已找到 | Git 已安装，且全局用户名和邮箱已配置 |

## 阶段 1 后续复测步骤

1. 优先使用 GitHub Actions 的 `Cloud verification` 工作流完成云端 MySQL 服务容器验证。
2. 如果 Docker Desktop 恢复正常，可使用 `docker compose up --build` 做本地容器化验证。
3. 如果选择 Railway 或 Render 等平台部署，按 `docs/cloud-deployment.md` 配置环境变量与数据库连接。
4. 云端验证通过后，将 Actions 截图保存到 `docs/report-assets`，用于实训报告“系统测试与部署验证”。
5. GitHub 阶段 1 成果已推送；后续继续按阶段提交。
