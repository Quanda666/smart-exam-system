-- V4.0 数据库迁移脚本：三端职责边界与教学数据域底座（MySQL 8.x 兼容，可重复执行）
-- 用途：把旧库升级到支持「主班级 + 选修班级」「班级-课程-教师-学生多对多」「公告/考试目标范围」。
-- 执行方式：mysql -u root -p smart_exam_system < migration-v4.sql
--
-- 说明：MySQL 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，因此补列使用 information_schema + 动态 SQL。
--      本脚本只做兼容式加表、加列和旧数据回填，不删除历史考试、成绩、错题或日志。

-- ============================================================
-- 1. 旧表补列：学生/教师档案、班级类型
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'student_profile'
                      AND COLUMN_NAME = 'primary_class_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE student_profile ADD COLUMN primary_class_id BIGINT DEFAULT NULL COMMENT ''主专业班级 edu_class.id'' AFTER class_id',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'student_profile'
                      AND COLUMN_NAME = 'enrollment_year');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE student_profile ADD COLUMN enrollment_year VARCHAR(32) DEFAULT NULL COMMENT ''入学年份/年级，如2023'' AFTER primary_class_id',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'student_profile'
                      AND COLUMN_NAME = 'college');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE student_profile ADD COLUMN college VARCHAR(128) DEFAULT NULL COMMENT ''学院'' AFTER enrollment_year',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'student_profile'
                      AND COLUMN_NAME = 'major');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE student_profile ADD COLUMN major VARCHAR(128) DEFAULT NULL COMMENT ''专业'' AFTER college',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'teacher_profile'
                      AND COLUMN_NAME = 'hire_date');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE teacher_profile ADD COLUMN hire_date DATE DEFAULT NULL COMMENT ''入职时间'' AFTER teacher_no',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'teacher_profile'
                      AND COLUMN_NAME = 'college');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE teacher_profile ADD COLUMN college VARCHAR(128) DEFAULT NULL COMMENT ''学院/部门'' AFTER title',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'teacher_profile'
                      AND COLUMN_NAME = 'introduction');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE teacher_profile ADD COLUMN introduction VARCHAR(1000) DEFAULT NULL COMMENT ''简介'' AFTER college',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'edu_class'
                      AND COLUMN_NAME = 'class_code');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE edu_class ADD COLUMN class_code VARCHAR(64) DEFAULT NULL COMMENT ''班级编码'' AFTER class_name',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'edu_class'
                      AND COLUMN_NAME = 'class_type');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE edu_class ADD COLUMN class_type VARCHAR(32) NOT NULL DEFAULT ''MAJOR'' COMMENT ''班级类型：MAJOR主专业班级/ELECTIVE选修临时班级/TEMPORARY临时班级'' AFTER class_code',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'edu_class'
                      AND INDEX_NAME = 'uk_class_code');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE edu_class ADD UNIQUE KEY uk_class_code (class_code)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'edu_class'
                      AND INDEX_NAME = 'idx_class_type');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE edu_class ADD INDEX idx_class_type (class_type)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 新增教学组织与范围表
-- ============================================================

CREATE TABLE IF NOT EXISTS edu_course (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  course_code VARCHAR(64)   NOT NULL COMMENT '课程编码',
  course_name VARCHAR(128)  NOT NULL COMMENT '课程名称',
  subject_id  BIGINT        DEFAULT NULL COMMENT '关联科目 edu_subject.id',
  credit      DECIMAL(4,1)  DEFAULT NULL COMMENT '学分',
  description VARCHAR(500)  DEFAULT NULL,
  status      TINYINT       NOT NULL DEFAULT 1,
  deleted     TINYINT       NOT NULL DEFAULT 0,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_code (course_code),
  KEY idx_course_subject (subject_id),
  KEY idx_course_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程';

CREATE TABLE IF NOT EXISTS class_course (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  class_id    BIGINT       NOT NULL COMMENT '班级 edu_class.id',
  course_id   BIGINT       NOT NULL COMMENT '课程 edu_course.id',
  term_name   VARCHAR(64)  NOT NULL DEFAULT '默认学期' COMMENT '开课学期',
  status      TINYINT      NOT NULL DEFAULT 1,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_class_course_term (class_id, course_id, term_name),
  KEY idx_class_course_class (class_id),
  KEY idx_class_course_course (course_id),
  KEY idx_class_course_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级课程/开课实例';

CREATE TABLE IF NOT EXISTS teacher_class_course (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  teacher_user_id  BIGINT      NOT NULL COMMENT '教师 sys_user.id',
  class_course_id  BIGINT      NOT NULL COMMENT '班级课程 class_course.id',
  teacher_role     VARCHAR(32) NOT NULL DEFAULT 'LECTURER' COMMENT '授课角色：LECTURER主讲/ASSISTANT助教',
  status           TINYINT     NOT NULL DEFAULT 1,
  deleted          TINYINT     NOT NULL DEFAULT 0,
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_class_course (teacher_user_id, class_course_id, teacher_role),
  KEY idx_tcc_teacher (teacher_user_id),
  KEY idx_tcc_class_course (class_course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师-班级课程授课关系';

CREATE TABLE IF NOT EXISTS student_class_membership (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  student_user_id BIGINT      NOT NULL COMMENT '学生 sys_user.id',
  class_id         BIGINT     NOT NULL COMMENT '班级 edu_class.id',
  membership_type VARCHAR(32) NOT NULL DEFAULT 'PRIMARY' COMMENT '归属类型：PRIMARY主班级/ELECTIVE选修班级/TEMPORARY临时班级',
  source          VARCHAR(32) DEFAULT 'ADMIN' COMMENT '来源：ADMIN/IMPORT/APPLY/SYSTEM',
  status          TINYINT     NOT NULL DEFAULT 1,
  deleted         TINYINT     NOT NULL DEFAULT 0,
  joined_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at         DATETIME    DEFAULT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_class_membership (student_user_id, class_id, membership_type),
  KEY idx_scm_student (student_user_id),
  KEY idx_scm_class (class_id),
  KEY idx_scm_type (membership_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生多班级归属';

CREATE TABLE IF NOT EXISTS student_course_enrollment (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  student_user_id BIGINT      NOT NULL COMMENT '学生 sys_user.id',
  class_course_id BIGINT      NOT NULL COMMENT '班级课程 class_course.id',
  enrollment_type VARCHAR(32) NOT NULL DEFAULT 'CLASS' COMMENT '选课类型：CLASS主班级同步/ELECTIVE选修/ASSIGNED手动分配',
  status          TINYINT     NOT NULL DEFAULT 1,
  deleted         TINYINT     NOT NULL DEFAULT 0,
  enrolled_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dropped_at      DATETIME    DEFAULT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_course_enrollment (student_user_id, class_course_id),
  KEY idx_sce_student (student_user_id),
  KEY idx_sce_class_course (class_course_id),
  KEY idx_sce_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生课程选课关系';

CREATE TABLE IF NOT EXISTS notice_target (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  notice_id   BIGINT      NOT NULL COMMENT '公告 notice.id',
  target_type VARCHAR(32) NOT NULL COMMENT 'SYSTEM/ROLE/CLASS/CLASS_COURSE/USER',
  target_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '目标ID；SYSTEM/ROLE 可为0',
  target_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '角色编码等文本目标，如 TEACHER/STUDENT',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notice_target (notice_id, target_type, target_id, target_code),
  KEY idx_notice_target_notice (notice_id),
  KEY idx_notice_target_lookup (target_type, target_id, target_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告目标范围';

CREATE TABLE IF NOT EXISTS exam_target (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  exam_id     BIGINT      NOT NULL COMMENT '考试 exam.id',
  target_type VARCHAR(32) NOT NULL DEFAULT 'CLASS_COURSE' COMMENT 'CLASS/CLASS_COURSE/USER',
  target_id   BIGINT      NOT NULL COMMENT '目标ID',
  target_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '预留文本目标',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_target (exam_id, target_type, target_id, target_code),
  KEY idx_exam_target_exam (exam_id),
  KEY idx_exam_target_lookup (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试目标范围';

-- ============================================================
-- 3. 旧数据回填
-- ============================================================

UPDATE student_profile
SET primary_class_id = class_id
WHERE primary_class_id IS NULL
  AND class_id IS NOT NULL;

UPDATE edu_class
SET class_type = 'MAJOR'
WHERE class_type IS NULL OR class_type = '';

UPDATE edu_class
SET class_code = CONCAT('CLASS-', id)
WHERE class_code IS NULL OR class_code = '';

INSERT IGNORE INTO edu_course (course_code, course_name, subject_id, description, status, deleted)
SELECT CONCAT('SUBJECT-', s.id), s.subject_name, s.id, s.description, s.status, s.deleted
FROM edu_subject s
WHERE s.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM edu_course existing
      WHERE existing.subject_id = s.id AND existing.deleted = 0
  );

INSERT IGNORE INTO class_course (class_id, course_id, term_name, status, deleted)
SELECT c.id, co.id, '默认学期', 1, 0
FROM edu_class c
JOIN edu_course co ON co.deleted = 0
WHERE c.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM class_course existing
      WHERE existing.class_id = c.id
        AND existing.course_id = co.id
        AND existing.deleted = 0
  );

INSERT IGNORE INTO student_class_membership (student_user_id, class_id, membership_type, source, status, deleted)
SELECT user_id, primary_class_id, 'PRIMARY', 'MIGRATION', status, deleted
FROM student_profile
WHERE primary_class_id IS NOT NULL;

INSERT IGNORE INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
SELECT sp.user_id, cc.id, 'CLASS', 1, 0
FROM student_profile sp
JOIN class_course cc ON cc.class_id = sp.primary_class_id AND cc.deleted = 0
WHERE sp.primary_class_id IS NOT NULL
  AND sp.deleted = 0;

INSERT IGNORE INTO notice_target (notice_id, target_type, target_id, target_code)
SELECT id, 'SYSTEM', 0, ''
FROM notice
WHERE deleted = 0;

INSERT IGNORE INTO exam_target (exam_id, target_type, target_id, target_code)
SELECT exam_id, 'CLASS', class_id, ''
FROM exam_class;
