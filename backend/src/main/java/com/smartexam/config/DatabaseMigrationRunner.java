package com.smartexam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 应用启动后，幂等地把旧数据库补齐到当前版本所需结构。
 *
 * <p>背景：{@code spring.sql.init} 的 schema.sql 用的是 {@code CREATE TABLE IF NOT EXISTS}，
 * 对「已存在的旧表」不会补列；而 MySQL 又不支持 {@code ADD COLUMN IF NOT EXISTS}
 * （那是 MariaDB 语法）。因此线上持久库从旧版本升级后可能缺少新字段或新关系表。
 * 这里在应用启动后做一次轻量自愈，省去人工登库改表。</p>
 *
 * <p>这一步在 {@code spring.sql.init} 之后执行（ApplicationRunner 晚于数据源初始化），
 * 因此与 schema.sql/data.sql 不冲突，且对全新库为无操作（列与表都已存在）。</p>
 *
 * <p><b>关键约束</b>：任何异常只记录日志、绝不向外抛出——迁移失败也不能阻止应用启动，
 * 避免一次性表结构问题把整个线上服务拖垮。</p>
 */
@Component
@Order(0)
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public DatabaseMigrationRunner(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            log.warn("数据源不可用，跳过数据库结构自愈迁移");
            return;
        }
        ensureSysUserEmailVerifiedColumn(jdbc);
        ensureSysUserAvatarColumn(jdbc);
        ensureEmailVerificationTable(jdbc);
        ensureV4Columns(jdbc);
        ensureExamPublishColumns(jdbc);
        ensureRolePermissionTable(jdbc);
        ensureQuestionSourceColumns(jdbc);
        ensureRagTables(jdbc);
        ensureAiUsageLogTable(jdbc);
        ensureV4Tables(jdbc);
        backfillV4Data(jdbc);
        backfillRolePermissions(jdbc);
    }

    /** sys_user.email_verified 缺失则补列（V1.0 旧库升级场景）。 */
    private void ensureSysUserEmailVerifiedColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "sys_user", "email_verified",
                "ALTER TABLE sys_user ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 "
                        + "COMMENT '邮箱是否已验证 0未验证 1已验证'");
    }

    /** sys_user.avatar 缺失则补列（V2.0 头像功能升级场景）。 */
    private void ensureSysUserAvatarColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "sys_user", "avatar",
                "ALTER TABLE sys_user ADD COLUMN avatar LONGTEXT DEFAULT NULL "
                        + "COMMENT '头像 base64 dataURL（前端压缩后存储）'");
    }

    /** email_verification 表缺失则创建（DB_INIT_MODE=never 等 schema.sql 未执行的场景下兜底）。 */
    private void ensureEmailVerificationTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "创建 email_verification 表", """
                CREATE TABLE IF NOT EXISTS email_verification (
                  id         BIGINT       NOT NULL AUTO_INCREMENT,
                  email      VARCHAR(128) NOT NULL COMMENT '目标邮箱',
                  code       VARCHAR(16)  NOT NULL COMMENT '验证码',
                  purpose    VARCHAR(32)  NOT NULL COMMENT '用途 LOGIN/BIND',
                  used       TINYINT      NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用',
                  expires_at DATETIME     NOT NULL COMMENT '过期时间',
                  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_ev_email_purpose (email, purpose, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码'
                """);
    }

    /** V4.0：三端职责边界与教学数据域底座所需旧表补列。 */
    private void ensureV4Columns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "student_profile", "primary_class_id",
                "ALTER TABLE student_profile ADD COLUMN primary_class_id BIGINT DEFAULT NULL COMMENT '主专业班级 edu_class.id' AFTER class_id");
        addColumnIfMissing(jdbc, "student_profile", "enrollment_year",
                "ALTER TABLE student_profile ADD COLUMN enrollment_year VARCHAR(32) DEFAULT NULL COMMENT '入学年份/年级，如2023' AFTER primary_class_id");
        addColumnIfMissing(jdbc, "student_profile", "college",
                "ALTER TABLE student_profile ADD COLUMN college VARCHAR(128) DEFAULT NULL COMMENT '学院' AFTER enrollment_year");
        addColumnIfMissing(jdbc, "student_profile", "major",
                "ALTER TABLE student_profile ADD COLUMN major VARCHAR(128) DEFAULT NULL COMMENT '专业' AFTER college");
        addIndexIfMissing(jdbc, "student_profile", "idx_student_primary_class",
                "ALTER TABLE student_profile ADD INDEX idx_student_primary_class (primary_class_id)");
        addIndexIfMissing(jdbc, "student_profile", "idx_student_no",
                "ALTER TABLE student_profile ADD INDEX idx_student_no (student_no)");

        addColumnIfMissing(jdbc, "teacher_profile", "hire_date",
                "ALTER TABLE teacher_profile ADD COLUMN hire_date DATE DEFAULT NULL COMMENT '入职时间' AFTER teacher_no");
        addColumnIfMissing(jdbc, "teacher_profile", "college",
                "ALTER TABLE teacher_profile ADD COLUMN college VARCHAR(128) DEFAULT NULL COMMENT '学院/部门' AFTER title");
        addColumnIfMissing(jdbc, "teacher_profile", "introduction",
                "ALTER TABLE teacher_profile ADD COLUMN introduction VARCHAR(1000) DEFAULT NULL COMMENT '简介' AFTER college");
        addIndexIfMissing(jdbc, "teacher_profile", "idx_teacher_no",
                "ALTER TABLE teacher_profile ADD INDEX idx_teacher_no (teacher_no)");

        addColumnIfMissing(jdbc, "edu_class", "class_code",
                "ALTER TABLE edu_class ADD COLUMN class_code VARCHAR(64) DEFAULT NULL COMMENT '班级编码' AFTER class_name");
        addColumnIfMissing(jdbc, "edu_class", "class_type",
                "ALTER TABLE edu_class ADD COLUMN class_type VARCHAR(32) NOT NULL DEFAULT 'MAJOR' COMMENT '班级类型：MAJOR主专业班级/ELECTIVE选修临时班级/TEMPORARY临时班级' AFTER class_code");
        addIndexIfMissing(jdbc, "edu_class", "idx_class_type",
                "ALTER TABLE edu_class ADD INDEX idx_class_type (class_type)");
        addIndexIfMissing(jdbc, "exam_class", "idx_ec_class",
                "ALTER TABLE exam_class ADD INDEX idx_ec_class (class_id)");
    }

    /** 考试发布流程设置：次数限制与及格线。 */
    private void ensureExamPublishColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "exam", "max_attempts",
                "ALTER TABLE exam ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '允许考试次数' AFTER duration_minutes");
        addColumnIfMissing(jdbc, "exam", "pass_score",
                "ALTER TABLE exam ADD COLUMN pass_score DECIMAL(10,2) DEFAULT NULL COMMENT '及格线' AFTER max_attempts");
        addColumnIfMissing(jdbc, "exam_attempt", "attempt_no",
                "ALTER TABLE exam_attempt ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '第几次作答' AFTER user_id");
        addIndexIfMissing(jdbc, "exam_attempt", "idx_attempt_exam_user_no",
                "ALTER TABLE exam_attempt ADD INDEX idx_attempt_exam_user_no (exam_id, user_id, attempt_no)");
    }

    /** Page-level role permissions used by the role authorization UI. */
    private void ensureRolePermissionTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create role_page_permission table", """
                CREATE TABLE IF NOT EXISTS role_page_permission (
                  id         BIGINT      NOT NULL AUTO_INCREMENT,
                  role_code  VARCHAR(32) NOT NULL,
                  page_path  VARCHAR(128) NOT NULL,
                  sort_order INT         NOT NULL DEFAULT 0,
                  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_role_page_permission (role_code, page_path),
                  KEY idx_role_page_permission_role (role_code, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role page permissions'
                """);
    }

    /** AI 题目进入题库后保留来源，方便审计和后续质量统计。 */
    private void ensureQuestionSourceColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "question", "source_type",
                "ALTER TABLE question ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' "
                        + "COMMENT '来源：MANUAL手动/AI_GENERATED生成/AI_IMPORTED识别/AI_MATERIAL材料生成/AI_RAG资料库生成' AFTER created_by");
        addColumnIfMissing(jdbc, "question", "source_detail",
                "ALTER TABLE question ADD COLUMN source_detail VARCHAR(255) DEFAULT NULL "
                        + "COMMENT '来源说明，如上传文件名或生成入口' AFTER source_type");
        addColumnIfMissing(jdbc, "question", "material_id",
                "ALTER TABLE question ADD COLUMN material_id BIGINT DEFAULT NULL COMMENT '来源资料 course_material.id' AFTER source_detail");
        addColumnIfMissing(jdbc, "question", "source_page",
                "ALTER TABLE question ADD COLUMN source_page INT DEFAULT NULL COMMENT '来源页码/幻灯片序号' AFTER material_id");
        addColumnIfMissing(jdbc, "question", "source_paragraph",
                "ALTER TABLE question ADD COLUMN source_paragraph INT DEFAULT NULL COMMENT '来源段落序号' AFTER source_page");
        addColumnIfMissing(jdbc, "question", "source_excerpt",
                "ALTER TABLE question ADD COLUMN source_excerpt VARCHAR(500) DEFAULT NULL COMMENT '来源片段' AFTER source_paragraph");
        addColumnIfMissing(jdbc, "question", "ai_model",
                "ALTER TABLE question ADD COLUMN ai_model VARCHAR(64) DEFAULT NULL COMMENT '生成使用的 AI 模型' AFTER source_excerpt");
        addColumnIfMissing(jdbc, "question", "prompt_version",
                "ALTER TABLE question ADD COLUMN prompt_version VARCHAR(64) DEFAULT NULL COMMENT '生成使用的提示词版本' AFTER ai_model");
        addIndexIfMissing(jdbc, "question", "idx_question_source",
                "ALTER TABLE question ADD INDEX idx_question_source (source_type)");
        addIndexIfMissing(jdbc, "question", "idx_question_material",
                "ALTER TABLE question ADD INDEX idx_question_material (material_id)");
    }

    /** 资料库/RAG：课程资料、分段和知识点大纲。 */
    private void ensureRagTables(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "创建 course_material 表", """
                CREATE TABLE IF NOT EXISTS course_material (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  subject_id   BIGINT       NOT NULL COMMENT '关联科目 edu_subject.id',
                  title        VARCHAR(200) NOT NULL COMMENT '资料标题',
                  file_name    VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
                  file_type    VARCHAR(32)  DEFAULT NULL COMMENT '文件扩展名',
                  content_text MEDIUMTEXT   COMMENT '抽取后的资料文本',
                  outline_json MEDIUMTEXT   COMMENT 'AI/规则生成的知识点大纲 JSON',
                  uploaded_by  BIGINT       DEFAULT NULL COMMENT '上传人 sys_user.id',
                  status       TINYINT      NOT NULL DEFAULT 1,
                  deleted      TINYINT      NOT NULL DEFAULT 0,
                  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_material_subject (subject_id),
                  KEY idx_material_uploader (uploaded_by)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资料库'
                """);
        executeQuietly(jdbc, "创建 course_material_chunk 表", """
                CREATE TABLE IF NOT EXISTS course_material_chunk (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  material_id  BIGINT       NOT NULL COMMENT '课程资料 course_material.id',
                  chunk_order  INT          NOT NULL DEFAULT 0,
                  page_no      INT          NOT NULL DEFAULT 1 COMMENT '页码/幻灯片序号',
                  paragraph_no INT          NOT NULL DEFAULT 1 COMMENT '段落序号',
                  heading      VARCHAR(200) DEFAULT NULL,
                  content      TEXT         NOT NULL COMMENT '分段内容',
                  keywords     VARCHAR(500) DEFAULT NULL,
                  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_material_chunk_material (material_id, chunk_order),
                  KEY idx_material_chunk_location (material_id, page_no, paragraph_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资料分段'
                """);
        executeQuietly(jdbc, "创建 course_material_outline 表", """
                CREATE TABLE IF NOT EXISTS course_material_outline (
                  id               BIGINT       NOT NULL AUTO_INCREMENT,
                  material_id       BIGINT      NOT NULL COMMENT '课程资料 course_material.id',
                  outline_order     INT         NOT NULL DEFAULT 0,
                  title             VARCHAR(200) NOT NULL COMMENT '知识点标题',
                  summary           VARCHAR(1000) DEFAULT NULL COMMENT '知识点摘要',
                  keywords          VARCHAR(500) DEFAULT NULL COMMENT '关键词',
                  source_page       INT         DEFAULT NULL,
                  source_paragraph  INT         DEFAULT NULL,
                  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_material_outline_material (material_id, outline_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资料知识点大纲'
                """);
    }

    /** AI 审计日志表：兼容旧库跳过 schema.sql 或部分字段缺失的场景。 */
    private void ensureAiUsageLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "创建 ai_usage_log 表", """
                CREATE TABLE IF NOT EXISTS ai_usage_log (
                  id            BIGINT       NOT NULL AUTO_INCREMENT,
                  user_id       BIGINT       DEFAULT NULL,
                  scene         VARCHAR(64)  DEFAULT NULL,
                  prompt        TEXT         DEFAULT NULL,
                  `response`    TEXT         DEFAULT NULL,
                  `success`     TINYINT      NOT NULL DEFAULT 1,
                  error_message VARCHAR(500) DEFAULT NULL,
                  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_ai_log_user (user_id),
                  KEY idx_ai_log_scene_success_time (scene, `success`, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志'
                """);
        addColumnIfMissing(jdbc, "ai_usage_log", "user_id",
                "ALTER TABLE ai_usage_log ADD COLUMN user_id BIGINT DEFAULT NULL AFTER id");
        addColumnIfMissing(jdbc, "ai_usage_log", "scene",
                "ALTER TABLE ai_usage_log ADD COLUMN scene VARCHAR(64) DEFAULT NULL AFTER user_id");
        addColumnIfMissing(jdbc, "ai_usage_log", "prompt",
                "ALTER TABLE ai_usage_log ADD COLUMN prompt TEXT DEFAULT NULL AFTER scene");
        addColumnIfMissing(jdbc, "ai_usage_log", "response",
                "ALTER TABLE ai_usage_log ADD COLUMN `response` TEXT DEFAULT NULL AFTER prompt");
        addColumnIfMissing(jdbc, "ai_usage_log", "success",
                "ALTER TABLE ai_usage_log ADD COLUMN `success` TINYINT NOT NULL DEFAULT 1 AFTER `response`");
        addColumnIfMissing(jdbc, "ai_usage_log", "error_message",
                "ALTER TABLE ai_usage_log ADD COLUMN error_message VARCHAR(500) DEFAULT NULL AFTER `success`");
        addColumnIfMissing(jdbc, "ai_usage_log", "created_at",
                "ALTER TABLE ai_usage_log ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER error_message");
        addIndexIfMissing(jdbc, "ai_usage_log", "idx_ai_log_user",
                "ALTER TABLE ai_usage_log ADD INDEX idx_ai_log_user (user_id)");
        addIndexIfMissing(jdbc, "ai_usage_log", "idx_ai_log_scene_success_time",
                "ALTER TABLE ai_usage_log ADD INDEX idx_ai_log_scene_success_time (scene, `success`, created_at)");
    }

    /** V4.0：创建课程、开课、授课、选课、公告目标、考试目标等新关系表。 */
    private void ensureV4Tables(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "创建 edu_course 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程'
                """);

        executeQuietly(jdbc, "创建 class_course 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级课程/开课实例'
                """);

        executeQuietly(jdbc, "创建 teacher_class_course 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师-班级课程授课关系'
                """);

        executeQuietly(jdbc, "创建 student_class_membership 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生多班级归属'
                """);

        executeQuietly(jdbc, "创建 student_course_enrollment 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生课程选课关系'
                """);

        executeQuietly(jdbc, "创建 notice_target 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告目标范围'
                """);

        executeQuietly(jdbc, "创建 exam_target 表", """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试目标范围'
                """);
    }

    /** V4.0：以兼容方式回填旧数据，确保阶段一完成后新数据域服务可读到基础范围。 */
    private void backfillV4Data(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "回填学生主班级", """
                UPDATE student_profile
                SET primary_class_id = class_id
                WHERE primary_class_id IS NULL
                  AND class_id IS NOT NULL
                """);
        executeQuietly(jdbc, "回填班级类型", """
                UPDATE edu_class
                SET class_type = 'MAJOR'
                WHERE class_type IS NULL OR class_type = ''
                """);
        executeQuietly(jdbc, "回填班级编码", """
                UPDATE edu_class
                SET class_code = CONCAT('CLASS-', id)
                WHERE class_code IS NULL OR class_code = ''
                """);
        executeQuietly(jdbc, "由科目回填课程", """
                INSERT IGNORE INTO edu_course (course_code, course_name, subject_id, description, status, deleted)
                SELECT CONCAT('SUBJECT-', s.id), s.subject_name, s.id, s.description, s.status, s.deleted
                FROM edu_subject s
                WHERE s.deleted = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM edu_course existing
                      WHERE existing.subject_id = s.id AND existing.deleted = 0
                  )
                """);
        executeQuietly(jdbc, "由班级和课程回填默认开课", """
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
                  )
                """);
        executeQuietly(jdbc, "回填学生主班级归属", """
                INSERT IGNORE INTO student_class_membership (student_user_id, class_id, membership_type, source, status, deleted)
                SELECT user_id, primary_class_id, 'PRIMARY', 'MIGRATION', status, deleted
                FROM student_profile
                WHERE primary_class_id IS NOT NULL
                """);
        executeQuietly(jdbc, "回填学生默认课程选课", """
                INSERT IGNORE INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
                SELECT sp.user_id, cc.id, 'CLASS', 1, 0
                FROM student_profile sp
                JOIN class_course cc ON cc.class_id = sp.primary_class_id AND cc.deleted = 0
                WHERE sp.primary_class_id IS NOT NULL
                  AND sp.deleted = 0
                """);
        executeQuietly(jdbc, "回填历史公告为全系统公告", """
                INSERT IGNORE INTO notice_target (notice_id, target_type, target_id, target_code)
                SELECT id, 'SYSTEM', 0, ''
                FROM notice
                WHERE deleted = 0
                """);
        executeQuietly(jdbc, "回填旧考试班级范围到考试目标", """
                INSERT IGNORE INTO exam_target (exam_id, target_type, target_id, target_code)
                SELECT exam_id, 'CLASS', class_id, ''
                FROM exam_class
                """);
    }

    private void backfillRolePermissions(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "backfill role page permissions", """
                INSERT IGNORE INTO role_page_permission (role_code, page_path, sort_order)
                SELECT role_code, page_path, sort_order
                FROM (
                  SELECT 'ADMIN' role_code, '/admin' page_path, 0 sort_order
                  UNION ALL SELECT 'ADMIN', '/question-bank', 1
                  UNION ALL SELECT 'ADMIN', '/papers', 2
                  UNION ALL SELECT 'ADMIN', '/exam/analysis', 3
                  UNION ALL SELECT 'ADMIN', '/basic/data', 4
                  UNION ALL SELECT 'ADMIN', '/system/users', 5
                  UNION ALL SELECT 'ADMIN', '/system/roles', 6
                  UNION ALL SELECT 'ADMIN', '/monitor/logs', 7
                  UNION ALL SELECT 'TEACHER', '/teacher', 0
                  UNION ALL SELECT 'TEACHER', '/exam-tasks', 1
                  UNION ALL SELECT 'TEACHER', '/reviews', 2
                  UNION ALL SELECT 'TEACHER', '/teacher/analysis', 3
                  UNION ALL SELECT 'TEACHER', '/teacher/students', 4
                  UNION ALL SELECT 'TEACHER', '/question-bank', 5
                  UNION ALL SELECT 'TEACHER', '/papers', 6
                  UNION ALL SELECT 'TEACHER', '/basic/data', 7
                  UNION ALL SELECT 'STUDENT', '/student', 0
                  UNION ALL SELECT 'STUDENT', '/student/exams', 1
                  UNION ALL SELECT 'STUDENT', '/student/results', 2
                  UNION ALL SELECT 'STUDENT', '/student/wrong-questions', 3
                  UNION ALL SELECT 'STUDENT', '/basic/data', 4
                ) seed
                """);
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String tableName, String columnName, String ddl) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tableName, columnName);
            if (count != null && count > 0) {
                return;
            }
            jdbc.execute(ddl);
            log.info("数据库自愈迁移：已为 {} 补充 {} 列", tableName, columnName);
        } catch (Exception ex) {
            log.error("数据库自愈迁移：为 {} 补列 {} 失败（不影响应用启动），原因：{}", tableName, columnName, ex.getMessage());
        }
    }

    private void addIndexIfMissing(JdbcTemplate jdbc, String tableName, String indexName, String ddl) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    Integer.class, tableName, indexName);
            if (count != null && count > 0) {
                return;
            }
            jdbc.execute(ddl);
            log.info("数据库自愈迁移：已为 {} 补充 {} 索引", tableName, indexName);
        } catch (Exception ex) {
            log.error("数据库自愈迁移：为 {} 补索引 {} 失败（不影响应用启动），原因：{}", tableName, indexName, ex.getMessage());
        }
    }

    private void executeQuietly(JdbcTemplate jdbc, String action, String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ex) {
            log.error("数据库自愈迁移：{}失败（不影响应用启动），原因：{}", action, ex.getMessage());
        }
    }
}
