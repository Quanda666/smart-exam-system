# 数据库设计

## 权威脚本

当前数据库结构以以下文件为准：

- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/data.sql`

后端启动时通过 Spring SQL Init 自动执行。旧的合并脚本 `docs/init.sql` 已删除，不再作为交付物维护。

## 初始化流程

首次部署只需要先创建库：

```sql
CREATE DATABASE IF NOT EXISTS smart_exam_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

应用启动后自动建表并写入初始化数据。如需手动初始化：

```bash
mysql -u root -p smart_exam_system < backend/src/main/resources/db/schema.sql
mysql -u root -p smart_exam_system < backend/src/main/resources/db/data.sql
```

## 表分组

### 用户与权限

| 表 | 说明 |
|---|---|
| `sys_user` | 用户账号、联系方式、头像、状态 |
| `sys_role` | 角色定义 |
| `sys_user_role` | 用户角色关系 |
| `student_profile` | 学生档案 |
| `teacher_profile` | 教师档案 |
| `user_token` | 登录 Token 会话 |
| `email_verification` | 邮箱验证码 |

### 基础数据

| 表 | 说明 |
|---|---|
| `edu_class` | 班级 |
| `edu_subject` | 科目 |
| `edu_course` | 课程 |
| `class_course` | 课程班 |
| `teacher_class_course` | 授课分配 |
| `student_class_membership` | 学生班级关系 |
| `student_course_enrollment` | 学生课程选课关系 |
| `edu_knowledge_point` | 知识点 |
| `notice` | 公告 |
| `notice_target` | 公告目标范围 |

### 题库、试卷和考试

| 表 | 说明 |
|---|---|
| `question` | 题目主表 |
| `question_option` | 客观题选项 |
| `paper` | 试卷 |
| `paper_question` | 试卷题目关系与分值 |
| `exam` | 考试任务 |
| `exam_class` | 考试班级范围 |
| `exam_target` | 考试目标范围 |
| `exam_attempt` | 学生考试尝试 |
| `exam_answer_draft` | 在线作答草稿 |
| `answer_record` | 作答记录 |
| `review_record` | 主观题批阅记录 |

### 反馈、监控和 AI

| 表 | 说明 |
|---|---|
| `wrong_question_book` | 错题本 |
| `cheat_event` | 防作弊异常事件 |
| `operation_log` | 操作日志 |
| `notification` | 通知 |
| `ai_provider_config` | AI 服务配置记录 |
| `ai_prompt_template` | AI 提示词模板 |
| `ai_usage_log` | AI 调用日志 |

## 设计约定

- 大部分业务表使用 `deleted` 字段做逻辑删除。
- 初始化数据使用固定主键和 `INSERT IGNORE`，可重复执行。
- `schema.sql` 包含旧库兼容说明，部分历史库缺列由启动迁移逻辑补齐。
- 考试、答题、阅卷、错题和日志表保留时间字段，便于审计和统计。
- AI 结果先进入业务草稿，不直接发布题目或决定主观题分数。

## 初始数据

`data.sql` 写入：

- `ADMIN`、`TEACHER`、`STUDENT` 三类角色。
- 初始管理员 `admin / admin123`。
- 示例班级、科目、课程、课程班、知识点和公告。
- AI 提示词模板。
