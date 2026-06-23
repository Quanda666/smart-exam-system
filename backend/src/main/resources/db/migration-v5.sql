-- V5.0 考试发布流程字段：次数限制、及格线
-- 执行方式：mysql -u root -p smart_exam_system < migration-v5.sql

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE exam ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT ''允许考试次数'' AFTER duration_minutes',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'exam' AND COLUMN_NAME = 'max_attempts'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS role_page_permission (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  role_code  VARCHAR(32)  NOT NULL COMMENT '角色编码',
  page_path  VARCHAR(128) NOT NULL COMMENT '页面路径',
  sort_order INT          NOT NULL DEFAULT 0 COMMENT '菜单排序',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_page_permission (role_code, page_path),
  KEY idx_role_page_permission_role (role_code, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色页面权限';

INSERT IGNORE INTO role_page_permission (role_code, page_path, sort_order) VALUES
  ('ADMIN', '/admin', 0),
  ('ADMIN', '/question-bank', 1),
  ('ADMIN', '/papers', 2),
  ('ADMIN', '/exam/analysis', 3),
  ('ADMIN', '/basic/data', 4),
  ('ADMIN', '/system/users', 5),
  ('ADMIN', '/system/roles', 6),
  ('ADMIN', '/monitor/logs', 7),
  ('TEACHER', '/teacher', 0),
  ('TEACHER', '/exam-tasks', 1),
  ('TEACHER', '/reviews', 2),
  ('TEACHER', '/teacher/analysis', 3),
  ('TEACHER', '/teacher/students', 4),
  ('TEACHER', '/question-bank', 5),
  ('TEACHER', '/papers', 6),
  ('TEACHER', '/basic/data', 7),
  ('STUDENT', '/student', 0),
  ('STUDENT', '/student/exams', 1),
  ('STUDENT', '/student/results', 2),
  ('STUDENT', '/student/wrong-questions', 3),
  ('STUDENT', '/basic/data', 4);

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE exam_attempt ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT ''第几次作答'' AFTER user_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'exam_attempt' AND COLUMN_NAME = 'attempt_no'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE exam_attempt ADD INDEX idx_attempt_exam_user_no (exam_id, user_id, attempt_no)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'exam_attempt' AND INDEX_NAME = 'idx_attempt_exam_user_no'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE exam ADD COLUMN pass_score DECIMAL(10,2) DEFAULT NULL COMMENT ''及格线'' AFTER max_attempts',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'exam' AND COLUMN_NAME = 'pass_score'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
