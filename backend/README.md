# 后端服务

本目录为智慧在线考试与学习反馈系统后端服务，当前已进入阶段 9：防作弊与日志。

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8，可选；未启动数据库时后端仍可通过内置演示数据完成阶段 2 到阶段 9 的核心演示，健康接口会返回数据库未连接状态。

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
| GET | /api/exams/teacher | 教师查询考试任务，ADMIN、TEACHER 可访问 |
| GET | /api/exams/student | 学生查询个人考试列表，STUDENT 可访问 |
| POST | /api/exams | 创建考试任务 |
| POST | /api/exams/attempt/{id}/start | 学生开始考试 |
| POST | /api/exams/attempt/{id}/submit | 学生提交答案 |
| GET | /api/reviews/pending | 获取待批阅列表 |
| GET | /api/reviews/attempt/{id} | 获取待批阅答卷详情 |
| POST | /api/reviews/attempt/{id} | 提交主观题批阅 |
| GET | /api/student/grades | 获取个人成绩列表 |
| GET | /api/student/exam-result/{id} | 获取单次考试结果 |
| GET | /api/student/wrong-questions | 获取错题本 |
| GET | /api/student/mastery | 获取知识点掌握度 |
| POST | /api/monitor/cheat-event | 记录异常事件 |
| GET | /api/monitor/cheat-events/{id} | 查看异常事件 |
| GET | /api/monitor/logs | 查询操作日志 |
| POST | /api/ai/generate-question | AI 辅助出题 |
| POST | /api/ai/explain | AI 内容解释 |
| POST | /api/ai/suggest-review | AI 评分建议 |

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

阶段 10 添加了 AI 辅助能力，包括 AI 辅助出题、AI 内容解释和 AI 评分建议，标志着所有核心功能已开发完成。
