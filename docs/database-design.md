# 数据库设计记录

本文件用于持续记录数据库实体、表结构、字段含义、约束与阶段演进。

## 阶段 1 基础表

阶段 1 只创建项目基础数据表和 AI 预留表：

| 表名 | 说明 |
|---|---|
| sys_user | 登录用户基础信息 |
| sys_role | 系统角色 |
| sys_user_role | 用户角色关联 |
| edu_class | 班级基础信息 |
| edu_subject | 科目基础信息 |
| edu_knowledge_point | 知识点基础信息 |
| ai_provider_config | OpenAI 兼容服务配置 |
| ai_prompt_template | AI 提示词模板 |
| ai_usage_log | AI 调用日志 |

## 后续阶段扩展

后续将按阶段逐步增加题库、试卷、考试任务、答题记录、批阅记录、错题本、防作弊日志等表，避免一次性设计过大导致返工。
