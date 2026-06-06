# 数据库初始化说明

> **脚本实际位置**：应用运行时使用的初始化脚本位于
> [`backend/src/main/resources/db/schema.sql`](../backend/src/main/resources/db/schema.sql)（建表）与
> [`backend/src/main/resources/db/data.sql`](../backend/src/main/resources/db/data.sql)（初始数据）。
> Spring Boot 启动时通过 `spring.sql.init`（`mode=always`）**自动执行**它们完成建表与初始化，
> **云端部署无需手动导入**。脚本均为幂等（`CREATE TABLE IF NOT EXISTS` + 固定主键 `INSERT IGNORE`），可重复执行。
>
> 如需对外部数据库手动初始化，可使用
> [`scripts/migrate-database.sh`](../scripts/migrate-database.sh) /
> [`scripts/migrate-database.cmd`](../scripts/migrate-database.cmd)。

本目录用于保存数据库结构说明与后续数据库变更记录。

## 数据库名称

建议使用数据库名：

```sql
smart_exam_system
```

## 执行顺序

1. 先执行 [`schema.sql`](../backend/src/main/resources/db/schema.sql)，创建系统业务表。
2. 再执行 [`data.sql`](../backend/src/main/resources/db/data.sql)，写入基础角色、初始管理员、班级、科目、知识点、公告和 AI 提示词模板。

## 当前表范围

- 用户与角色：`sys_user`、`sys_role`、`sys_user_role`
- 档案：`student_profile`、`teacher_profile`
- 基础资料：`edu_class`、`edu_subject`、`edu_knowledge_point`、`notice`
- 题库：`question`、`question_option`
- 试卷：`paper`、`paper_question`
- 考试与答卷：`exam`、`exam_class`、`exam_attempt`、`answer_record`、`review_record`
- 学习反馈：`wrong_question_book`
- 监控与日志：`cheat_event`、`operation_log`
- AI 预留：`ai_provider_config`、`ai_prompt_template`、`ai_usage_log`

> 表结构由后端各 `Service` 中实际使用的 SQL 反推整理，逻辑删除统一使用 `deleted` 字段，
> 选项表 `question_option`、试卷题目表 `paper_question` 随主表物理删除。

## 初始管理员与注册策略

| 角色 | 账号 | 初始密码 | 默认入口 |
|---|---|---|---|
| 管理员 | admin | admin123 | /admin |

教师和学生账号不由初始化脚本预置，需通过系统注册入口创建。初始管理员密码在
[`data.sql`](../backend/src/main/resources/db/data.sql) 中以 `sha256$salt$hash` 形式保存，生产部署后应及时修改。

## 初始化基础资料

| 类型 | 示例 |
|---|---|
| 班级 | 23本科计科1班 |
| 科目 | Java程序设计、数据库系统 |
| 知识点 | 集合框架、线程与并发、SQL查询、事务与ACID |
| 公告 | 在线考试系统上线公告 |

## 安全说明

1. 当前 Token 存储为后端内存会话，适合单实例部署；如需多实例或长期会话，可替换为 Spring Security、Sa Token 或 JWT。
2. AI 服务密钥不写入数据库脚本，仅通过环境变量或本地配置读取，并在页面和日志中脱敏展示。
