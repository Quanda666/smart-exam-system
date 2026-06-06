-- ============================================================
-- Smart Exam System - 生产初始化数据 (MySQL 8.x)
-- ============================================================
-- 配合 spring.sql.init.mode=always，本脚本会在每次启动时执行，
-- 因此所有语句均使用固定主键 + INSERT IGNORE 保证幂等（可重复执行不报错）。
-- 初始管理员：admin / admin123（密码以 sha256$salt$hash 形式存储，部署后请尽快修改）。
-- ============================================================

-- ---------- 系统角色 ----------
INSERT IGNORE INTO sys_role (id, role_code, role_name, status, deleted) VALUES
  (1, 'ADMIN',   '管理员', 1, 0),
  (2, 'TEACHER', '教师',   1, 0),
  (3, 'STUDENT', '学生',   1, 0);

-- ---------- 初始管理员 admin / admin123 ----------
INSERT IGNORE INTO sys_user (id, username, password_hash, real_name, status, deleted) VALUES
  (1, 'admin', 'sha256$8f3a9c2e1b7d4056af6e2c9d1840b3a7$9139ea64805f37cf32696cb3a693b2544aba658343de5e1d3ed726082b279c5b', '系统管理员', 1, 0);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ---------- 基础资料：班级 ----------
INSERT IGNORE INTO edu_class (id, class_name, major, grade, status, deleted) VALUES
  (1, '23本科计科1班', '计算机科学与技术', '2023', 1, 0);

-- ---------- 基础资料：科目 ----------
INSERT IGNORE INTO edu_subject (id, subject_name, description, status, deleted) VALUES
  (1, 'Java程序设计', 'Java 语言基础与面向对象编程', 1, 0),
  (2, '数据库系统',   '关系型数据库原理与 SQL 应用', 1, 0);

-- ---------- 基础资料：知识点 ----------
INSERT IGNORE INTO edu_knowledge_point (id, subject_id, parent_id, point_name, sort_order, status, deleted) VALUES
  (1, 1, NULL, '集合框架',     1, 1, 0),
  (2, 1, NULL, '线程与并发',   2, 1, 0),
  (3, 2, NULL, 'SQL查询',      1, 1, 0),
  (4, 2, NULL, '事务与ACID',   2, 1, 0);

-- ---------- 公告 ----------
INSERT IGNORE INTO notice (id, title, content, publisher_id, status, publish_time, deleted) VALUES
  (1, '在线考试系统上线公告', '欢迎使用智慧在线考试与学习反馈系统。教师与学生账号请通过注册入口创建，管理员请及时修改默认密码。', 1, 1, NOW(), 0);

-- ---------- AI 提示词模板（预留） ----------
INSERT IGNORE INTO ai_prompt_template (id, template_code, template_name, content, status, deleted) VALUES
  (1, 'GENERATE_QUESTION', '出题助手',       '为{subject}生成一道{difficulty}的{questionType}题目，关于知识点：{knowledgePoint}。', 1, 0),
  (2, 'EXPLAIN',           '讲解助手',       '请详细解释以下内容：\n{content}', 1, 0),
  (3, 'SUGGEST_REVIEW',    '主观题评分助手', '请对以下主观题答案进行评分并给出评语。\n题目：{question}\n参考答案：{correctAnswer}\n学生答案：{studentAnswer}', 1, 0);
