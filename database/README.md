# 数据库初始化说明

本目录用于保存智慧在线考试与学习反馈系统的数据库结构脚本、生产初始化数据脚本和后续数据库变更记录。

## 数据库名称

建议使用数据库名：

```sql
smart_exam_system
```

## 执行顺序

1. 先执行 [`schema.sql`](schema.sql)，创建数据库与系统业务表。
2. 再执行 [`seed.sql`](seed.sql)，写入基础角色、初始管理员、班级、科目、知识点、题库样例、试卷样例、公告和 AI 提示词模板。

## 当前表范围

- 用户与角色：[`sys_user`](schema.sql)、[`sys_role`](schema.sql)、[`sys_user_role`](schema.sql)
- 阶段 2 档案：[`student_profile`](schema.sql)、[`teacher_profile`](schema.sql)
- 基础资料：[`edu_class`](schema.sql)、[`edu_subject`](schema.sql)、[`edu_knowledge_point`](schema.sql)、[`notice`](schema.sql)
- AI 预留：[`ai_provider_config`](schema.sql)、[`ai_prompt_template`](schema.sql)、[`ai_usage_log`](schema.sql)

题库、试卷、考试任务、答题记录、阅卷记录、成绩分析、防作弊日志等表将在后续阶段按主控文档逐步补充。

## 初始管理员与注册策略

| 角色 | 账号 | 初始密码 | 默认入口 |
|---|---|---|---|
| 管理员 | admin | admin123 | /admin |

教师和学生账号不再由初始化脚本预置，需通过系统注册入口创建。初始化管理员密码在 [`seed.sql`](seed.sql) 中以 `sha256$salt$hash` 形式保存，生产部署后应及时修改。

## 初始化基础资料

| 类型 | 示例 |
|---|---|
| 班级 | 23本科计科1班 |
| 科目 | Java程序设计、数据库系统 |
| 知识点 | 集合框架、线程与并发、SQL查询、事务与ACID |
| 公告 | 在线考试系统阶段3公告 |

## 安全说明

1. 当前 Token 存储为后端内存会话，适合单实例部署；如需多实例或长期会话，可替换为 Spring Security、Sa Token 或 JWT。
2. AI 服务密钥不建议直接写入数据库脚本。当前仍使用环境变量或本地配置读取密钥，并在页面和日志中进行脱敏展示。
