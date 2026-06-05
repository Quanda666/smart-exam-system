USE smart_exam_system;

INSERT INTO sys_role (role_code, role_name, status)
VALUES
  ('ADMIN', '管理员', 1),
  ('TEACHER', '教师', 1),
  ('STUDENT', '学生', 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = VALUES(status);

INSERT INTO sys_user (username, password_hash, real_name, phone, email, status)
VALUES
  ('admin', 'sha256$admin-stage2-salt$c99c4e073f3b4ad0ac1daeeb6628472f62b74cb82a0538e94fa6253f4f93651d', '系统管理员', NULL, 'admin@example.com', 1),
  ('teacher1', 'sha256$teacher-stage2-salt$5b4278abf3a831fb9f44c1abe28b3a2638753060fae00bfdbfbf8ee9535abeb8', '演示教师一', NULL, 'teacher1@example.com', 1),
  ('student1', 'sha256$student-stage2-salt$eccad4a3297ed7e56b116c71709eb0df8de81a2a9005cec8335f950a2e36273f', '演示学生一', NULL, 'student1@example.com', 1)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), real_name = VALUES(real_name), status = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON u.username = 'admin' AND r.role_code = 'ADMIN';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON u.username = 'teacher1' AND r.role_code = 'TEACHER';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON u.username = 'student1' AND r.role_code = 'STUDENT';

INSERT INTO edu_class (class_name, major, grade, status)
VALUES ('23本科计科1班', '计算机科学与技术', '2023级', 1)
ON DUPLICATE KEY UPDATE major = VALUES(major), grade = VALUES(grade), status = VALUES(status);

INSERT INTO teacher_profile (user_id, teacher_no, title, introduction, status)
SELECT id, 'T2024001', '讲师', '负责在线考试系统演示课程的题库、试卷与阅卷。', 1 FROM sys_user WHERE username = 'teacher1'
ON DUPLICATE KEY UPDATE title = VALUES(title), introduction = VALUES(introduction), status = VALUES(status);

INSERT INTO student_profile (user_id, student_no, class_id, status)
SELECT u.id, 'S2024001', c.id, 1 FROM sys_user u JOIN edu_class c ON c.class_name = '23本科计科1班' WHERE u.username = 'student1'
ON DUPLICATE KEY UPDATE class_id = VALUES(class_id), status = VALUES(status);

INSERT INTO edu_subject (subject_name, description, status)
VALUES
  ('Java程序设计', '用于演示 Java 基础、集合、线程、面向对象等题目。', 1),
  ('数据库系统', '用于演示 SQL、事务、索引、数据库设计等题目。', 1)
ON DUPLICATE KEY UPDATE description = VALUES(description), status = VALUES(status);

INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
SELECT id, NULL, '集合框架', 1, 1 FROM edu_subject WHERE subject_name = 'Java程序设计'
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
SELECT id, NULL, '线程与并发', 2, 1 FROM edu_subject WHERE subject_name = 'Java程序设计'
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
SELECT id, NULL, 'SQL查询', 1, 1 FROM edu_subject WHERE subject_name = '数据库系统'
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
SELECT id, NULL, '事务与ACID', 2, 1 FROM edu_subject WHERE subject_name = '数据库系统'
ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order), status = VALUES(status);

INSERT INTO notice (title, content, publisher_id, status, publish_time)
SELECT '在线考试系统阶段3公告', '基础资料管理已启用，可维护班级、科目、知识点和公告数据。', id, 1, NOW()
FROM sys_user WHERE username = 'admin'
ON DUPLICATE KEY UPDATE content = VALUES(content), status = VALUES(status), publish_time = VALUES(publish_time);

INSERT INTO ai_provider_config (provider_name, base_url, model_name, api_key_ref, mock_enabled, timeout_seconds, status)
VALUES ('OpenAI兼容服务-模拟模式', 'https://api.openai.com/v1', 'gpt-4o-mini', 'OPENAI_API_KEY', 1, 30, 1);

INSERT INTO ai_prompt_template (scene_code, template_name, template_content, version_no, status)
VALUES
  ('QUESTION_GENERATE', 'AI辅助出题提示词', '请根据科目、知识点、题型、难度和数量生成在线考试题目草稿，输出必须包含题干、选项、参考答案和解析。', 'v1', 1),
  ('QUESTION_EXPLANATION', 'AI题目解析提示词', '请根据题干、参考答案和知识点生成适合学生理解的题目解析和易错点说明。', 'v1', 1),
  ('SUBJECTIVE_REVIEW', 'AI主观题评分建议提示词', '请根据评分标准、参考答案和学生答案给出建议分、建议评语和评分依据。最终分数由教师确认。', 'v1', 1),
  ('WRONG_QUESTION_EXPLAIN', 'AI错题讲解提示词', '请根据学生错题、错误答案、正确答案和知识点生成通俗易懂的错题讲解与复习建议。', 'v1', 1)
ON DUPLICATE KEY UPDATE template_name = VALUES(template_name), template_content = VALUES(template_content), status = VALUES(status);
