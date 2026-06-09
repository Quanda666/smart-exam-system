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
