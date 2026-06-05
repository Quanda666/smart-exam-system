# 数据库设计记录

本文件用于持续记录数据库实体、表结构、字段含义、约束与阶段演进。

## 阶段 1 基础表

阶段 1 创建项目基础数据表和 AI 预留表：

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

## 阶段 2 新增与调整

阶段 2 围绕登录认证与角色权限补充用户档案表，并调整演示账号密码摘要：

| 表名 | 说明 | 关键字段 |
|---|---|---|
| student_profile | 学生档案表，用于绑定学生账号与班级 | user_id、student_no、class_id、status |
| teacher_profile | 教师档案表，用于绑定教师账号与教师工号、职称 | user_id、teacher_no、title、introduction、status |
| sys_user | 密码字段继续使用 password_hash | 阶段 2 种子数据使用 `sha256$salt$hash` 格式 |
| sys_role | 固定三类基础角色 | ADMIN、TEACHER、STUDENT |
| sys_user_role | 账号与角色关联 | admin、teacher1、student1 分别绑定三类角色 |

## 阶段 3 新增与调整

阶段 3 围绕基础资料管理补齐公告表，并强化基础资料唯一约束：

| 表名 | 说明 | 阶段 3 用途 |
|---|---|---|
| edu_class | 班级表 | 管理班级名称、专业、年级和启停状态，后续用于考试任务发布范围 |
| edu_subject | 科目表 | 管理考试科目，后续用于题库、试卷和统计分析归类 |
| edu_knowledge_point | 知识点表 | 管理科目下知识点，后续用于规则组卷、错题本和知识点薄弱分析 |
| notice | 公告表 | 管理系统公告和考试通知，供管理员、教师发布，学生查看 |

阶段 3 关键约束：

1. `edu_class.class_name` 保持唯一，避免重复班级。
2. `edu_subject.subject_name` 保持唯一，避免重复科目。
3. `edu_knowledge_point` 新增 `(subject_id, point_name)` 唯一约束，避免同一科目下知识点重复。
4. `notice.title` 保持唯一，便于演示种子数据重复执行时幂等更新。
5. 各基础资料表保留 `status`、`created_at`、`updated_at`、`deleted` 字段，支持启停、审计和逻辑删除。

## 阶段 2 演示账号

| 角色 | 账号 | 密码 | 数据库摘要说明 |
|---|---|---|---|
| 管理员 | admin | admin123 | 存储为带盐 SHA-256 摘要 |
| 教师 | teacher1 | teacher123 | 存储为带盐 SHA-256 摘要 |
| 学生 | student1 | student123 | 存储为带盐 SHA-256 摘要 |

## 阶段 3 演示数据

| 类型 | 示例数据 | 说明 |
|---|---|---|
| 班级 | 23本科计科1班 | 学生账号所属班级，后续发布考试使用 |
| 科目 | Java程序设计、数据库系统 | 支撑题库分类 |
| 知识点 | 集合框架、线程与并发、SQL查询、事务与ACID | 支撑组卷与学习反馈 |
| 公告 | 在线考试系统阶段3公告 | 支撑公告管理和学生端通知展示 |

## 设计说明

1. 阶段 2 暂不引入完整 Spring Security 数据库权限模型，先实现轻量 Token 与角色隔离，保证阶段目标可验证。
2. 用户、角色、用户角色表仍保留为后续扩展菜单权限、接口权限和用户管理模块的基础。
3. 学生档案和教师档案独立于用户表，便于后续关联班级、考试任务、题库创建人和阅卷记录。
4. 阶段 3 基础资料模块作为题库、试卷、考试任务、成绩分析的前置数据层，接口按角色进行读写隔离。
5. 当前数据库脚本可在云端 MySQL 服务容器中执行，用于 GitHub Actions 自动化验证。

## 后续阶段扩展

后续将按阶段逐步增加题库、试卷、考试任务、答题记录、批阅记录、错题本、防作弊日志等表，避免一次性设计过大导致返工。
