# Smart Exam System 智慧在线考试与学习反馈系统

本仓库用于第七组《计算机工程综合能力实训》项目“在线考试系统”的开发、测试、答辩展示与实训报告沉淀。

## 项目定位

本系统面向高校课程考核、课堂测验和阶段训练场景，围绕题库管理、试卷管理、考试发布、在线答题、自动评分、教师阅卷、成绩分析、防作弊记录、错题反馈与 AI 辅助学习构建完整在线考试闭环。

## 团队信息

| 分工 | 学号 | 姓名 |
|---|---|---|
| 组长 | 2312402060134 | 陈绍杰 |
| 成员 | 2312402060135 | 张嘉豪 |
| 成员 | 2312402060133 | 黄权达 |
| 成员 | 2312402060128 | 黄浩然 |

## 技术栈规划

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3、Vite、TypeScript、Element Plus、ECharts |
| 后端 | Java 17、Spring Boot 3、轻量 Token 认证，后续可扩展 Spring Security 或 Sa Token |
| 数据库 | MySQL 8 |
| AI 接入 | OpenAI 兼容接口适配层，可配置 Base URL、模型名、API Key 与模拟响应 |
| 协作 | Git、GitHub、GitHub Actions、阶段记录、测试记录 |

## 当前阶段

当前已完成阶段 9：防作弊、日志与学生反馈。

本阶段已完成：

1. 后端登录接口、退出接口、当前用户接口、角色菜单接口。
2. 后端轻量 Token 会话与角色访问控制。
3. 管理员、教师、学生三类角色专属工作台接口。
4. 班级、科目、知识点、公告的基础资料接口。
5. 题库管理接口，支持单选、多选、判断、填空、主观题维护、筛选、发布撤回和删除。
6. 试卷管理接口，支持手动组卷、规则组卷、试卷预览、发布撤回。
7. 考试任务接口，支持教师创建考试、学生获取考试列表、开始考试和提交答案。
8. 阅卷、成绩、错题本、知识点掌握度、防作弊和日志等相关接口。
9. 前端实现了完整的教师端（基础资料、题库、试卷、考试任务、阅卷）和学生端（考试中心、在线答题、成绩查询、错题本）核心页面。
10. 数据库已补充考试、答案、批阅、错题、防作弊和日志相关表。
11. 后端测试和前端生产构建验证。

## 目录结构

```text
smart-exam-system
├─ backend                    # 后端 Spring Boot 项目
├─ frontend                   # 前端 Vue 项目
├─ database                   # 数据库脚本与说明
├─ docs                       # 接口、数据库、AI、测试等文档
├─ scripts                    # Windows 本地辅助脚本
├─ 第七组-在线考试系统项目主控文档.md
├─ README.md
├─ .gitignore
└─ LICENSE
```

## 后端快速启动

```bash
cd backend
mvn spring-boot:run
```

Windows 本地可在项目根目录使用：

```cmd
scripts\run-backend.cmd
```

后端默认地址：

```text
http://localhost:8080
```

核心接口：

```text
GET  http://localhost:8080/api/health
GET  http://localhost:8080/api/ai/status
GET  http://localhost:8080/api/auth/register-options
POST http://localhost:8080/api/auth/register
POST http://localhost:8080/api/auth/login
GET  http://localhost:8080/api/auth/me
GET  http://localhost:8080/api/admin/overview
GET  http://localhost:8080/api/teacher/overview
GET  http://localhost:8080/api/student/overview
GET  http://localhost:8080/api/basic/summary
GET  http://localhost:8080/api/basic/classes
GET  http://localhost:8080/api/basic/subjects
GET  http://localhost:8080/api/basic/knowledge-points
GET  http://localhost:8080/api/basic/notices
GET  http://localhost:8080/api/questions/summary
GET  http://localhost:8080/api/questions
POST http://localhost:8080/api/questions
PUT  http://localhost:8080/api/questions/{id}
PUT  http://localhost:8080/api/questions/{id}/status
DELETE http://localhost:8080/api/questions/{id}
GET  http://localhost:8080/api/papers/summary
GET  http://localhost:8080/api/papers
POST http://localhost:8080/api/papers
POST http://localhost:8080/api/papers/generate
GET  http://localhost:8080/api/papers/{id}
PUT  http://localhost:8080/api/papers/{id}
DELETE http://localhost:8080/api/papers/{id}
GET  http://localhost:8080/api/exams/teacher
GET  http://localhost:8080/api/exams/student
POST http://localhost:8080/api/exams
POST http://localhost:8080/api/exams/attempt/{id}/start
POST http://localhost:8080/api/exams/attempt/{id}/submit
GET  http://localhost:8080/api/reviews/pending
GET  http://localhost:8080/api/reviews/attempt/{id}
POST http://localhost:8080/api/reviews/attempt/{id}
GET  http://localhost:8080/api/student/grades
GET  http://localhost:8080/api/student/exam-result/{id}
GET  http://localhost:8080/api/student/wrong-questions
GET  http://localhost:8080/api/student/mastery
POST http://localhost:8080/api/monitor/cheat-event
GET  http://localhost:8080/api/monitor/cheat-events/{id}
GET  http://localhost:8080/api/monitor/logs
POST http://localhost:8080/api/ai/generate-question
POST http://localhost:8080/api/ai/explain
POST http://localhost:8080/api/ai/suggest-review
```

## 前端快速启动

```bash
cd frontend
npm install
npm run dev
```

Windows 本地可在项目根目录使用：

```cmd
scripts\run-frontend.cmd
```

前端默认地址：

```text
http://127.0.0.1:3000
```

## 账号初始化与注册

系统初始化脚本仅保留一个管理员账号用于首次登录和系统维护：

| 角色 | 账号 | 初始密码 | 默认入口 |
|---|---|---|---|
| 管理员 | admin | admin123 | /admin |

教师和学生账号通过登录页的注册入口创建，不再提供内置教师/学生账号或快速填充账号。生产部署后建议管理员尽快修改初始管理员密码。

## 数据库初始化

数据库脚本位于 [`database`](database) 目录：

- [`database/schema.sql`](database/schema.sql)：创建了完整的数据库表结构，包括用户、角色、基础资料、题库、试卷、考试、答题、批阅、错题本、防作弊和日志等。
- [`database/seed.sql`](database/seed.sql)：写入生产初始化所需的基础角色、初始管理员、班级、科目、知识点、题库样例、试卷样例、公告和 AI 提示词模板。

建议数据库名：

```text
smart_exam_system
```

## 云端验证与部署

为了实现更简单、更低资源消耗的一键部署，项目已将部署架构升级为**一体化单容器部署（前端打包进后端由后端托管）**。详细部署文档：[Railway 部署指南](docs/deploy-railway.md)、[Render 部署指南](docs/deploy-render.md)、[外部数据库配置](docs/external-database.md)、[故障排查](docs/troubleshooting.md)。

一键自动部署做法：

1. 用 Railway 连接 GitHub 仓库。
2. Railway 会自动检测并读取根目录下的 [`Dockerfile`](Dockerfile) 进行多阶段构建（前端 Vue 构建后直接打包入后端 Jar 中运行）。
3. 在 Railway 上另外创建一个空 MySQL 数据库，并根据文档初始化库名 `smart_exam_system`。
4. 在 Web 服务上绑定公网域名并添加 MySQL 连接串等环境变量，系统即自动运行并上线。

相关配置：

- 根目录多阶段构建描述文件：[`Dockerfile`](Dockerfile)
- 自动 SPA 路由重定向：[`backend/src/main/java/com/smartexam/controller/SpaController.java`](backend/src/main/java/com/smartexam/controller/SpaController.java)
- [`.github/workflows/cloud-verify.yml`](.github/workflows/cloud-verify.yml)
- [`docker-compose.yml`](docker-compose.yml)

## 辅助脚本

Windows 环境可使用 [`scripts`](scripts) 目录下的脚本进行环境检查和启动：

```cmd
scripts\check-env.cmd
scripts\run-backend.cmd
scripts\run-frontend.cmd
```

其中 [`scripts/check-env.cmd`](scripts/check-env.cmd) 会检查 Java、Maven、Node.js、npm 和前端依赖状态；[`scripts/run-backend.cmd`](scripts/run-backend.cmd) 用于启动后端；[`scripts/run-frontend.cmd`](scripts/run-frontend.cmd) 用于安装依赖并启动前端。

## AI 接入说明

AI 模块当前只做可配置规划和状态接口预留，不影响系统核心运行。

建议通过环境变量配置：

```text
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=你的密钥
OPENAI_MODEL=gpt-4o-mini
AI_MOCK_ENABLED=true
```

若未配置 API Key，系统应返回未启用或模拟模式状态，核心考试流程不受影响。

## 主控文档

所有后续开发以 [`第七组-在线考试系统项目主控文档.md`](第七组-在线考试系统项目主控文档.md) 为主线推进，每完成一个阶段都应同步记录进度、验证结果和可写入实训报告的内容。
