# 数据库初始化说明

本目录用于保存智慧在线考试与学习反馈系统的数据库结构脚本、演示数据脚本和后续数据库变更记录。

## 数据库名称

建议使用数据库名：

```sql
smart_exam_system
```

## 执行顺序

1. 先执行 [`schema.sql`](schema.sql)，创建数据库与阶段 1 基础表。
2. 再执行 [`seed.sql`](seed.sql)，写入基础角色、演示账号、班级、科目、知识点和 AI 提示词模板。

## 阶段 1 表范围

阶段 1 只创建项目骨架必须的数据表：

- 用户与角色：[`sys_user`](schema.sql)、[`sys_role`](schema.sql)、[`sys_user_role`](schema.sql)
- 基础资料：[`edu_class`](schema.sql)、[`edu_subject`](schema.sql)、[`edu_knowledge_point`](schema.sql)
- AI 预留：[`ai_provider_config`](schema.sql)、[`ai_prompt_template`](schema.sql)、[`ai_usage_log`](schema.sql)

题库、试卷、考试任务、答题记录、阅卷记录、成绩分析、防作弊日志等表将在后续阶段按主控文档逐步补充。

## 安全说明

AI 服务密钥不建议直接写入数据库脚本。阶段 1 使用环境变量或本地配置读取密钥，并在页面和日志中进行脱敏展示。
