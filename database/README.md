# 数据库初始化说明

本目录用于保存智慧在线考试与学习反馈系统的数据库结构脚本、演示数据脚本和后续数据库变更记录。

## 数据库名称

建议使用数据库名：

```sql
smart_exam_system
```

## 执行顺序

1. 先执行 [`schema.sql`](schema.sql)，创建数据库与阶段 1、阶段 2、阶段 3 基础表。
2. 再执行 [`seed.sql`](seed.sql)，写入基础角色、演示账号、教师档案、学生档案、班级、科目、知识点、公告和 AI 提示词模板。

## 当前表范围

- 用户与角色：[`sys_user`](schema.sql)、[`sys_role`](schema.sql)、[`sys_user_role`](schema.sql)
- 阶段 2 档案：[`student_profile`](schema.sql)、[`teacher_profile`](schema.sql)
- 基础资料：[`edu_class`](schema.sql)、[`edu_subject`](schema.sql)、[`edu_knowledge_point`](schema.sql)、[`notice`](schema.sql)
- AI 预留：[`ai_provider_config`](schema.sql)、[`ai_prompt_template`](schema.sql)、[`ai_usage_log`](schema.sql)

题库、试卷、考试任务、答题记录、阅卷记录、成绩分析、防作弊日志等表将在后续阶段按主控文档逐步补充。

## 阶段 2 演示账号

| 角色 | 账号 | 密码 | 默认入口 |
|---|---|---|---|
| 管理员 | admin | admin123 | /admin |
| 教师 | teacher1 | teacher123 | /teacher |
| 学生 | student1 | student123 | /student |

演示账号密码在 [`seed.sql`](seed.sql) 中以 `sha256$salt$hash` 形式保存，不再使用明文或 `{noop}` 形式。

## 阶段 3 演示基础资料

| 类型 | 示例 |
|---|---|
| 班级 | 23本科计科1班 |
| 科目 | Java程序设计、数据库系统 |
| 知识点 | 集合框架、线程与并发、SQL查询、事务与ACID |
| 公告 | 在线考试系统阶段3公告 |

## 安全说明

1. 当前 Token 存储为后端内存会话，适合阶段 2 和阶段 3 演示与接口验证，后续可替换为 Spring Security、Sa Token 或 JWT。
2. AI 服务密钥不建议直接写入数据库脚本。当前仍使用环境变量或本地配置读取密钥，并在页面和日志中进行脱敏展示。
