# 后端服务

本目录为智慧在线考试与学习反馈系统后端服务，当前已进入阶段 5：试卷管理与规则组卷。

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8，可选；未启动数据库时后端仍可通过内置演示数据完成阶段 2 登录验证、阶段 3 基础资料演示、阶段 4 题库演示和阶段 5 试卷演示，健康接口会返回数据库未连接状态。

## 启动命令

```bash
mvn spring-boot:run
```

Windows 本地也可在项目根目录使用：

```cmd
scripts\run-backend.cmd
```

## 默认接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/health | 健康检查，包含应用、时间和数据库连通状态 |
| GET | /api/ai/status | AI 配置状态，返回模拟模式、模型名、Base URL 和密钥配置状态，不返回明文密钥 |
| GET | /api/auth/demo-users | 获取演示账号 |
| POST | /api/auth/login | 登录并返回 Token、用户信息和角色菜单 |
| GET | /api/auth/me | 获取当前用户信息，需要 Token |
| GET | /api/auth/menus | 获取当前用户菜单，需要 Token |
| POST | /api/auth/logout | 退出登录，需要 Token |
| GET | /api/admin/overview | 管理员工作台，需要 ADMIN 角色 |
| GET | /api/teacher/overview | 教师工作台，需要 TEACHER 角色 |
| GET | /api/student/overview | 学生首页，需要 STUDENT 角色 |
| GET | /api/basic/summary | 基础资料统计，需要登录 |
| GET | /api/basic/classes | 班级列表，ADMIN、TEACHER 可访问 |
| POST | /api/basic/classes | 新增班级，仅 ADMIN |
| PUT | /api/basic/classes/{id} | 修改班级，仅 ADMIN |
| DELETE | /api/basic/classes/{id} | 删除班级，仅 ADMIN |
| GET | /api/basic/subjects | 科目列表，三类角色可访问 |
| POST | /api/basic/subjects | 新增科目，ADMIN、TEACHER 可访问 |
| PUT | /api/basic/subjects/{id} | 修改科目，ADMIN、TEACHER 可访问 |
| DELETE | /api/basic/subjects/{id} | 删除科目，ADMIN、TEACHER 可访问 |
| GET | /api/basic/knowledge-points | 知识点列表，三类角色可访问 |
| POST | /api/basic/knowledge-points | 新增知识点，ADMIN、TEACHER 可访问 |
| PUT | /api/basic/knowledge-points/{id} | 修改知识点，ADMIN、TEACHER 可访问 |
| DELETE | /api/basic/knowledge-points/{id} | 删除知识点，ADMIN、TEACHER 可访问 |
| GET | /api/basic/notices | 公告列表，三类角色可访问 |
| POST | /api/basic/notices | 新增公告，ADMIN、TEACHER 可访问 |
| PUT | /api/basic/notices/{id} | 修改公告，ADMIN、TEACHER 可访问 |
| DELETE | /api/basic/notices/{id} | 删除公告，ADMIN、TEACHER 可访问 |
| GET | /api/questions/summary | 题库统计，ADMIN、TEACHER 可访问 |
| GET | /api/questions | 题目列表，支持关键词、科目、知识点、题型、难度、状态筛选 |
| POST | /api/questions | 新增题目，ADMIN、TEACHER 可访问 |
| PUT | /api/questions/{id} | 修改题目，ADMIN、TEACHER 可访问 |
| PUT | /api/questions/{id}/status | 发布或撤回题目，ADMIN、TEACHER 可访问 |
| DELETE | /api/questions/{id} | 删除题目，ADMIN、TEACHER 可访问 |
| GET | /api/papers/summary | 试卷统计，ADMIN、TEACHER 可访问 |
| GET | /api/papers | 试卷列表，支持关键词、科目、状态筛选 |
| POST | /api/papers | 手动组卷创建试卷 |
| POST | /api/papers/generate | 规则组卷创建试卷 |
| GET | /api/papers/{id} | 获取试卷详情，含题目列表 |
| PUT | /api/papers/{id} | 更新试卷题目、分值和基础信息 |
| DELETE | /api/papers/{id} | 删除试卷，ADMIN、TEACHER 可访问 |

## 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | admin | admin123 |
| 教师 | teacher1 | teacher123 |
| 学生 | student1 | student123 |

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| SERVER_PORT | 8080 | 后端端口 |
| MYSQL_URL | jdbc:mysql://localhost:3306/smart_exam_system | 数据库连接地址 |
| MYSQL_USERNAME | root | 数据库用户名 |
| MYSQL_PASSWORD | root | 数据库密码 |
| OPENAI_BASE_URL | https://api.openai.com/v1 | OpenAI 兼容接口地址 |
| OPENAI_API_KEY | 空 | AI 密钥，不配置时 AI 处于未配置或模拟状态 |
| OPENAI_MODEL | gpt-4o-mini | 默认模型 |
| AI_MOCK_ENABLED | true | 是否启用 AI 模拟响应 |

## 阶段说明

阶段 5 已在阶段 4 题库管理基础上，新增试卷管理接口，支持手动组卷、规则组卷、试卷预览、发布撤回和删除。后续阶段可基于已发布试卷进入考试任务发布。
