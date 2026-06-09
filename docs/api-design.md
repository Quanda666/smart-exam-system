# API 设计

本文档记录当前后端真实 API 分组。所有业务接口默认需要登录 Token，除登录、注册、健康检查和 AI 状态等公开接口外，后端会按角色进行权限校验。

## 公共接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 应用和数据库健康检查 |
| GET | `/api/ai/status` | AI 配置状态，密钥脱敏 |

## 认证与账号

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/register` | 教师或学生注册 |
| GET | `/api/auth/register-options` | 注册选项 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/auth/menus` | 当前角色菜单 |
| POST | `/api/auth/logout` | 退出登录 |
| PUT | `/api/auth/password` | 修改密码 |
| PUT | `/api/auth/profile` | 修改个人资料 |
| POST | `/api/auth/send-login-code` | 发送邮箱登录验证码 |
| POST | `/api/auth/login-by-code` | 邮箱验证码登录 |
| POST | `/api/auth/send-bind-code` | 发送邮箱绑定验证码 |
| POST | `/api/auth/bind-email` | 绑定邮箱 |
| GET | `/api/auth/login-logs` | 登录日志 |
| GET | `/api/auth/access-matrix` | 权限矩阵 |

## 三端概况

| 方法 | 路径 | 角色 |
|---|---|---|
| GET | `/api/overview/admin` | ADMIN |
| GET | `/api/overview/teacher` | TEACHER |
| GET | `/api/overview/student` | STUDENT |

## 基础数据

| 路径 | 说明 |
|---|---|
| `/api/basic/summary` | 基础数据统计 |
| `/api/basic/classes` | 班级 |
| `/api/basic/courses` | 课程 |
| `/api/basic/class-courses` | 课程班 |
| `/api/basic/teaching-assignments` | 授课分配 |
| `/api/basic/student-memberships` | 学生班级关系 |
| `/api/basic/subjects` | 科目 |
| `/api/basic/knowledge-points` | 知识点 |
| `/api/basic/notices` | 公告 |

列表接口使用 `GET`，新增使用 `POST`，修改使用 `PUT /{id}`，删除使用 `DELETE /{id}`。部分关系表只提供新增和删除。

## 题库与 AI

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/questions/summary` | 题库统计 |
| GET | `/api/questions` | 题目列表与筛选 |
| POST | `/api/questions` | 新增题目 |
| PUT | `/api/questions/{id}` | 修改题目 |
| PUT | `/api/questions/{id}/status` | 发布或撤回 |
| DELETE | `/api/questions/{id}` | 删除题目 |
| POST | `/api/ai/questions/generate` | AI 直接生成题目草稿 |
| POST | `/api/ai/questions/import-document` | 上传题目文档并识别 |
| POST | `/api/ai/questions/generate-from-material` | 上传课程资料并生成题目 |
| POST | `/api/ai/questions/save` | 保存 AI 草稿到题库 |
| POST | `/api/ai/wrong-question/explain` | 错题 AI 讲解 |
| POST | `/api/ai/suggest-review` | 主观题评分建议 |
| POST | `/api/materials` | 上传课程资料，抽取文本并生成知识点大纲 |
| GET | `/api/materials` | 资料库列表 |
| GET | `/api/materials/{id}` | 资料详情、知识点大纲和分段 |
| POST | `/api/materials/{id}/questions/generate` | 基于资料库分段检索生成题目草稿 |

已移除旧接口：`/api/ai/generate-question`、`/api/ai/explain`。

## 试卷、考试、阅卷

| 分组 | 路径 |
|---|---|
| 试卷 | `/api/papers`, `/api/papers/summary`, `/api/papers/generate`, `/api/papers/{id}`, `/api/papers/{id}/status` |
| 考试 | `/api/exams`, `/api/exams/teacher`, `/api/exams/student`, `/api/exams/{id}`, `/api/exams/{id}/close`, `/api/exams/{id}/scores/export` |
| 作答 | `/api/exams/attempt/{attemptId}/start`, `/api/exams/attempt/{attemptId}/save`, `/api/exams/attempt/{attemptId}/submit` |
| 阅卷 | `/api/reviews/pending`, `/api/reviews/attempt/{attemptId}` |

## 学生反馈与分析

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/grades` | 成绩列表 |
| GET | `/api/student/exam-result/{attemptId}` | 单次考试结果 |
| GET | `/api/student/wrong-questions` | 错题本 |
| GET | `/api/student/mastery` | 知识点掌握度 |
| GET | `/api/analysis/overview` | 分析总览 |
| GET | `/api/analysis/teacher` | 教师分析 |
| GET | `/api/insight/classes/{classId}/students` | 班级学生画像 |
| GET | `/api/insight/students/{userId}` | 单个学生画像 |

## 监控、通知和系统管理

| 分组 | 路径 |
|---|---|
| 监控日志 | `/api/monitor/cheat-event`, `/api/monitor/cheat-events/{attemptId}`, `/api/monitor/logs`, `/api/monitor/ai-logs` |
| 通知 | `/api/notifications/my`, `/api/notifications/unread-count`, `/api/notifications/{id}/read`, `/api/notifications/read-all` |
| 用户管理 | `/api/system/users`, `/api/system/users/summary`, `/api/system/users/{id}/status`, `/api/system/users/{id}/password` |
| 角色管理 | `/api/system/roles` |

## 已清理接口

- `/api/admin/overview`
- `/api/teacher/overview`
- `/api/student/overview`
- `/api/questions/student-deny-check`
- `/api/diagnostic/smtp-test`
- `/api/ai/generate-question`
- `/api/ai/explain`
