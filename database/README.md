# 数据库说明

数据库初始化脚本以后端资源目录为唯一权威来源：

- [backend/src/main/resources/db/schema.sql](../backend/src/main/resources/db/schema.sql)
- [backend/src/main/resources/db/data.sql](../backend/src/main/resources/db/data.sql)

旧的合并版 `docs/init.sql` 已删除，避免与真实表结构分叉。

## 数据库名称

推荐使用：

```sql
smart_exam_system
```

首次运行前创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Spring Boot 启动时会通过 `spring.sql.init` 自动执行 `schema.sql` 与 `data.sql`。如需手动初始化外部数据库，可按顺序执行：

```bash
mysql -u root -p smart_exam_system < backend/src/main/resources/db/schema.sql
mysql -u root -p smart_exam_system < backend/src/main/resources/db/data.sql
```

## 表范围

- 用户与角色：`sys_user`、`sys_role`、`sys_user_role`
- 用户档案：`student_profile`、`teacher_profile`
- 基础数据：`edu_class`、`edu_subject`、`edu_course`、`class_course`、`teacher_class_course`、`student_class_membership`、`student_course_enrollment`
- 教学内容：`edu_knowledge_point`、`notice`、`notice_target`
- 题库与试卷：`question`、`question_option`、`paper`、`paper_question`
- 考试与答题：`exam`、`exam_class`、`exam_target`、`exam_attempt`、`exam_answer_draft`、`answer_record`、`review_record`
- 学习反馈：`wrong_question_book`
- 监控与日志：`cheat_event`、`operation_log`
- 通知：`notification`
- AI：`ai_provider_config`、`ai_prompt_template`、`course_material`、`course_material_chunk`、`course_material_outline`、`ai_usage_log`
- 邮箱与会话：`email_verification`、`user_token`

`question` 表保留来源类型、资料 ID、来源页码/段落、来源片段、AI 模型和提示词版本，用于追踪手动题、AI 直接生成、题目文档识别、课程材料生成和资料库/RAG 生成来源。

## 初始数据

`data.sql` 写入基础角色、初始管理员、示例班级、科目、课程、课程班、知识点、公告与 AI 提示词模板。

| 角色 | 账号 | 初始密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |

生产部署后应立即修改初始管理员密码，并按学校实际数据维护班级、课程、授课分配和学生归属。
