# Smart Exam System 智慧在线考试与学习反馈系统

本项目面向高校课程考核、课堂测验和阶段训练场景，提供管理员、教师、学生三端在线考试闭环：基础数据维护、题库建设、AI 辅助出题、试卷管理、考试发布、在线答题、自动判分、教师阅卷、成绩分析、防作弊记录、错题反馈与系统日志。

## 当前状态

项目已从早期“阶段式开发记录”整理为可运行的业务系统。三端默认入口均为“概况”，并置于菜单第一位；旧的角色占位概览、诊断接口、旧 AI 文本接口、重复初始化脚本和未使用 SMTP 配置已清理。

## 核心能力

- 三端概况：管理员、教师、学生分别查看当前角色相关的业务概览与快捷入口。
- 基础数据：班级、课程、课程班、授课分配、学生班级关系、科目、知识点、公告统一维护。
- 题库管理：支持单选、多选、判断、填空、主观题，含筛选、草稿、发布、撤回、删除和来源追踪。
- AI 出题台：支持直接生成题目、上传题目文档批量识别、上传课程资料按题型数量生成题目；资料库/RAG 支持上传课件生成知识点大纲，再按资料分段来源生成可审查题目。
- 试卷与考试：支持手动组卷、规则组卷、发布撤回、考试任务、在线答题、服务端草稿保存、本机离线草稿恢复与交卷。
- 阅卷与成绩：客观题自动评分，主观题教师评分，支持 AI 评分建议、成绩记录与结果详情。
- 学习反馈：错题本、知识点掌握度、学生画像与错题 AI 讲解。
- 监控与日志：切屏、失焦、粘贴、超时等异常事件记录，系统操作日志与 AI 调用审计查询。
- 账号与邮件：账号密码登录、邮箱验证码登录、邮箱绑定、登录日志、用户与角色管理；邮件发送使用 Resend HTTP API。

## 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3, Vite, TypeScript, Element Plus, ECharts |
| 后端 | Java 17, Spring Boot 3, JDBC, Bean Validation, AOP |
| 数据库 | MySQL 8 |
| AI | OpenAI 兼容 Chat Completions 接口，可配置 Base URL、模型、API Key、超时与模拟模式 |
| 部署 | Docker 多阶段构建，一体化 Jar 托管前端静态资源 |

## 目录结构

```text
smart-exam-system
├── backend/                 # Spring Boot 后端
├── frontend/                # Vue 前端
├── database/                # 数据库说明
├── docs/                    # 设计、部署、测试、审查文档
├── scripts/                 # Windows 本地辅助脚本
├── Dockerfile               # 一体化云部署构建
├── docker-compose.yml       # 本地容器运行参考
└── README.md
```

## 快速启动

首次运行前先创建数据库，表结构和初始数据由后端启动时自动执行：

```sql
CREATE DATABASE IF NOT EXISTS smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端开发启动：

```bash
cd backend
mvn spring-boot:run
```

前端开发启动：

```bash
cd frontend
npm install
npm run dev
```

访问地址：

```text
后端 API: http://localhost:8080
前端开发服务: http://127.0.0.1:3000
```

Windows 一体化本地启动推荐使用：

```cmd
scripts\run-all.cmd
```

该脚本会构建前端、复制到 Spring Boot 静态资源目录，并通过 `http://localhost:8080` 统一访问完整系统。

## 初始账号

| 角色 | 账号 | 初始密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |

教师和学生账号通过登录页注册入口创建。生产部署后应立即修改初始管理员密码。

## 数据库

唯一权威脚本位于：

- [backend/src/main/resources/db/schema.sql](backend/src/main/resources/db/schema.sql)
- [backend/src/main/resources/db/data.sql](backend/src/main/resources/db/data.sql)

Spring Boot 通过 `spring.sql.init.mode=always` 自动执行上述脚本。旧的 `docs/init.sql` 合并脚本已删除，避免和真实 schema 分叉。

## 关键接口

```text
GET  /api/health
GET  /api/ai/status

POST /api/auth/login
POST /api/auth/register
GET  /api/auth/me
GET  /api/auth/menus
PUT  /api/auth/profile
PUT  /api/auth/password

GET  /api/overview/admin
GET  /api/overview/teacher
GET  /api/overview/student

GET  /api/basic/summary
GET  /api/basic/classes
GET  /api/basic/courses
GET  /api/basic/class-courses
GET  /api/basic/teaching-assignments
GET  /api/basic/student-memberships
GET  /api/basic/subjects
GET  /api/basic/knowledge-points
GET  /api/basic/notices

GET  /api/questions
POST /api/questions
PUT  /api/questions/{id}
PUT  /api/questions/{id}/status
DELETE /api/questions/{id}

POST /api/ai/questions/generate
POST /api/ai/questions/import-document
POST /api/ai/questions/generate-from-material
POST /api/ai/questions/save
POST /api/ai/wrong-question/explain
POST /api/ai/suggest-review
```

完整接口分组见 [docs/api-design.md](docs/api-design.md)。

## 环境变量

常用变量见 [.env.example](.env.example)。核心项包括：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
CORS_ALLOWED_ORIGIN_PATTERNS
OPENAI_BASE_URL
OPENAI_API_KEY
OPENAI_MODEL
AI_MOCK_ENABLED
UPLOAD_MAX_FILE_SIZE
UPLOAD_MAX_REQUEST_SIZE
RESEND_API_KEY
RESEND_FROM_EMAIL
```

未配置 `OPENAI_API_KEY` 或开启 `AI_MOCK_ENABLED=true` 时，AI 功能使用本地模拟/规则兜底，不影响核心考试流程。

## 文档导航

- [backend/README.md](backend/README.md)：后端接口、配置和运行说明。
- [frontend/README.md](frontend/README.md)：前端页面、运行和构建说明。
- [docs/ai-design.md](docs/ai-design.md)：AI 功能设计。
- [docs/api-design.md](docs/api-design.md)：当前 API 分组。
- [docs/database-design.md](docs/database-design.md)：当前数据库结构说明。
- [docs/deploy-local.md](docs/deploy-local.md)：本地验收部署。
- [docs/deploy-railway.md](docs/deploy-railway.md)、[docs/deploy-render.md](docs/deploy-render.md)：云部署。
- [docs/email-setup.md](docs/email-setup.md)：Resend 邮件配置。
- [docs/test-records.md](docs/test-records.md)：最新验证记录。
- [docs/assessment.md](docs/assessment.md)：项目审查和清理记录。
