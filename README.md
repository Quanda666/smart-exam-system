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

当前已完成阶段 6：考试任务发布与学生答题。

本阶段已完成：

1. 后端登录接口、退出接口、当前用户接口、角色菜单接口。
2. 后端轻量 Token 会话与角色访问控制。
3. 管理员、教师、学生三类角色专属工作台接口。
4. 班级、科目、知识点、公告的基础资料接口。
5. 题库管理接口，支持单选、多选、判断、填空、主观题维护、筛选、发布撤回和删除。
6. 试卷管理接口，支持手动组卷、规则组卷、试卷预览、发布撤回。
7. 考试任务接口，支持教师创建考试、学生获取考试列表、开始考试和提交答案。
8. 前端登录页、演示账号选择、登录状态保存、角色菜单和角色首页雏形。
9. 前端基础资料管理、题库管理、试卷管理页面。
10. 前端考试任务管理、学生考试中心和在线答题页面。
11. 数据库新增考试任务、考试班级、学生考试记录和答案记录表。
12. 后端测试和前端生产构建验证。

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
GET  http://localhost:8080/api/auth/demo-users
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

## 演示账号

| 角色 | 账号 | 密码 | 默认入口 |
|---|---|---|---|
| 管理员 | admin | admin123 | /admin |
| 教师 | teacher1 | teacher123 | /teacher |
| 学生 | student1 | student123 | /student |

## 数据库初始化

数据库脚本位于 [`database`](database) 目录：

- [`database/schema.sql`](database/schema.sql)：创建数据库、用户角色表、学生档案表、教师档案表、基础资料表、公告表、题目表、题目选项表、试卷表、试卷题目关联表、考试相关表和 AI 预留表。
- [`database/seed.sql`](database/seed.sql)：写入演示角色、账号、档案、科目、班级、知识点、公告、题库题目、演示试卷、演示考试和 AI 提示词模板。

建议数据库名：

```text
smart_exam_system
```

## 云端验证与部署

为了减少对本地 Docker Desktop 和本地 MySQL 的依赖，项目已补充云端优先验证方案，详见 [`docs/cloud-deployment.md`](docs/cloud-deployment.md)。

当前推荐优先级：

1. 使用 GitHub Actions 的 MySQL 服务容器完成自动化验证。
2. 使用 Railway 部署后端和 MySQL，作为接近真实环境的演示方案。
3. 使用 Render、Koyeb、Fly.io 或 GitHub Codespaces 作为补充方案。

相关配置：

- [`backend/Dockerfile`](backend/Dockerfile)
- [`frontend/Dockerfile`](frontend/Dockerfile)
- [`frontend/nginx.conf`](frontend/nginx.conf)
- [`docker-compose.yml`](docker-compose.yml)
- [`.github/workflows/cloud-verify.yml`](.github/workflows/cloud-verify.yml)

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
