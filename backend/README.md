# 后端服务

**课程**：Web程序设计课程设计（S3048I）  
**项目**：第二组 - 在线考试系统

本目录是智慧在线考试与学习反馈系统的 Spring Boot 后端，负责认证授权、业务 API、数据库初始化、AI 接入、文档文本抽取、日志与监控。

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8

## 启动

```bash
mvn spring-boot:run
```

或在项目根目录使用：

```cmd
scripts\run-backend.cmd
```

默认地址：`http://localhost:8080`。

## 数据库初始化

后端启动时自动执行：

- `src/main/resources/db/schema.sql`
- `src/main/resources/db/data.sql`

首次运行只需要提前创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## 主要接口

| 分组 | 路径 |
|---|---|
| 健康检查 | `GET /api/health` |
| 认证 | `/api/auth/login`, `/api/auth/register`, `/api/auth/me`, `/api/auth/menus`, `/api/auth/logout`, `/api/auth/profile`, `/api/auth/password`, `/api/auth/login-logs` |
| 邮箱验证码 | `/api/auth/send-login-code`, `/api/auth/login-by-code`, `/api/auth/send-bind-code`, `/api/auth/bind-email` |
| 三端概况 | `GET /api/overview/admin`, `GET /api/overview/teacher`, `GET /api/overview/student` |
| 基础数据 | `/api/basic/classes`, `/api/basic/courses`, `/api/basic/class-courses`, `/api/basic/teaching-assignments`, `/api/basic/student-memberships`, `/api/basic/subjects`, `/api/basic/knowledge-points`, `/api/basic/notices`, `/api/basic/summary` |
| 题库 | `/api/questions`, `/api/questions/summary`, `/api/questions/{id}/status` |
| AI | `/api/ai/status`, `/api/ai/questions/generate`, `/api/ai/questions/import-document`, `/api/ai/questions/generate-from-material`, `/api/ai/questions/save`, `/api/ai/wrong-question/explain`, `/api/ai/suggest-review` |
| 资料库/RAG | `/api/materials`, `/api/materials/{id}`, `/api/materials/{id}/questions/generate` |
| 试卷 | `/api/papers`, `/api/papers/generate`, `/api/papers/{id}/status` |
| 考试 | `/api/exams`, `/api/exams/teacher`, `/api/exams/student`, `/api/exams/attempt/{id}/start`, `/api/exams/attempt/{id}/save`, `/api/exams/attempt/{id}/submit` |
| 阅卷 | `/api/reviews/pending`, `/api/reviews/attempt/{id}` |
| 学生反馈 | `/api/student/grades`, `/api/student/exam-result/{id}`, `/api/student/wrong-questions`, `/api/student/mastery` |
| 分析画像 | `/api/analysis/overview`, `/api/analysis/teacher`, `/api/insight/classes/{classId}/students`, `/api/insight/students/{userId}` |
| 监控日志 | `/api/monitor/cheat-event`, `/api/monitor/cheat-events/{id}`, `/api/monitor/logs`, `/api/monitor/ai-logs` |
| 系统管理 | `/api/system/users`, `/api/system/roles` |
| 通知 | `/api/notifications/my`, `/api/notifications/unread-count`, `/api/notifications/{id}/read`, `/api/notifications/read-all` |

旧接口 `/api/admin/overview`、`/api/teacher/overview`、`/api/student/overview`、`/api/ai/generate-question`、`/api/ai/explain` 已废弃并从代码中移除。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` / `PORT` | `8080` | 服务端口 |
| `MYSQL_URL` | 本地 `smart_exam_system` | MySQL 连接串 |
| `MYSQL_USERNAME` | `root` | 数据库用户 |
| `MYSQL_PASSWORD` | `root` | 数据库密码 |
| `DB_INIT_MODE` | `always` | Spring SQL 初始化模式 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:*,http://127.0.0.1:*` | CORS 来源 |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容接口地址 |
| `OPENAI_API_KEY` | 空 | AI 密钥 |
| `OPENAI_MODEL` | `gpt-4o-mini` | 模型名称 |
| `AI_MOCK_ENABLED` | `true` | AI 模拟模式 |
| `AI_TIMEOUT_SECONDS` | `30` | AI 请求超时 |
| `UPLOAD_MAX_FILE_SIZE` | `25MB` | 单文件上传限制 |
| `UPLOAD_MAX_REQUEST_SIZE` | `28MB` | 上传请求限制 |
| `RESEND_API_KEY` | 空 | Resend API Key |
| `RESEND_FROM_EMAIL` | 空 | 验证码发件邮箱 |

邮件发送使用 `EmailServiceV3` 调用 Resend HTTP API，不再使用 SMTP 配置。
密码存储优先使用 PBKDF2 哈希，历史 SHA-256 哈希会在用户密码登录成功后自动升级。

## 初始账号

| 角色 | 账号 | 初始密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |

教师和学生账号通过注册入口创建。
