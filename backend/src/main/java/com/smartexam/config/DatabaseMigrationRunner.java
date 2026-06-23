package com.smartexam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Statement;

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
        ensureLoginAttemptTable(jdbc);
        ensureV4Columns(jdbc);
        ensureExamPublishColumnsClean(jdbc);
        ensureAttemptSubmitAuditColumns(jdbc);
        ensureAttemptResilienceColumns(jdbc);
        ensureExamSubmitResponseTable(jdbc);
        ensureExamSnapshotTables(jdbc);
        ensureExamQuestionSnapshotKnowledgePointColumn(jdbc);
        ensureExamApprovalLogTable(jdbc);
        ensureExamApprovalReminderLogTable(jdbc);
        ensureExamApprovalReminderLogColumns(jdbc);
        ensureSystemJobLockTable(jdbc);
        ensureScoreReleaseTableClean(jdbc);
        ensureScoreReleaseNoteColumn(jdbc);
        ensureScoreReleaseAuditColumns(jdbc);
        ensureScoreReleaseUniqueExam(jdbc);
        ensureScoreReleaseLogTable(jdbc);
        ensureScoreAppealTableClean(jdbc);
        ensureScoreAppealExamIdColumn(jdbc);
        ensureScoreAppealActiveTargetIndex(jdbc);
        ensureScoreAppealHandlingResultColumn(jdbc);
        ensureScoreAppealRecheckColumns(jdbc);
        ensureScoreAppealLogTable(jdbc);
        ensureScoreAppealLogExamIdConsistency(jdbc);
        ensureReviewScoreLogTable(jdbc);
        ensureReviewScoreLogExamIdConsistency(jdbc);
        ensureAnswerRecordUniqueIndex(jdbc);
        ensureReviewScoreLogAnswerConsistency(jdbc);
        ensureWrongQuestionBookSnapshotIdentity(jdbc);
        ensureSystemConfigTableClean(jdbc);
        ensureSystemConfigLogTable(jdbc);
        ensureNotificationRelationColumns(jdbc);
        ensureRolePermissionTable(jdbc);
        ensureQuestionSourceColumns(jdbc);
        ensureQuestionReviewColumns(jdbc);
        ensureQuestionVersionTables(jdbc);
        ensureRagTables(jdbc);
        ensureAiUsageLogTable(jdbc);
        ensureCheatEventBatchColumns(jdbc);
        ensureCheatEventOwnershipConsistency(jdbc);
        ensureCheatEventRiskScoreConsistency(jdbc);
        ensureMonitorSessionTable(jdbc);
        ensureMonitorSessionOwnershipConsistency(jdbc);
        ensureMonitorSessionEventAggregateConsistency(jdbc);
        ensureMonitorSessionSubmittedStateConsistency(jdbc);
        ensureMonitorActionTable(jdbc);
        ensureMonitorSessionUniqueIdentity(jdbc);
        ensureMonitorActionOwnershipConsistency(jdbc);
        ensureExamAttemptUniqueIdentity(jdbc);
        ensureV4Tables(jdbc);
        backfillV4Data(jdbc);
        backfillExamSnapshots(jdbc);
        backfillDefaultSystemConfigsClean(jdbc);
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
    private void ensureExamQuestionSnapshotKnowledgePointColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "exam_question_snapshot", "knowledge_point_id",
                "ALTER TABLE exam_question_snapshot ADD COLUMN knowledge_point_id BIGINT DEFAULT NULL AFTER question_id");
        addIndexIfMissing(jdbc, "exam_question_snapshot", "idx_exam_question_snapshot_kp",
                "ALTER TABLE exam_question_snapshot ADD INDEX idx_exam_question_snapshot_kp (knowledge_point_id)");
    }

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

    private void ensureLoginAttemptTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "创建 login_attempt 表", """
                CREATE TABLE IF NOT EXISTS login_attempt (
                  account       VARCHAR(128) NOT NULL COMMENT '登录账号/邮箱，小写归一化',
                  failure_count INT          NOT NULL DEFAULT 0 COMMENT '连续失败次数',
                  locked_until  DATETIME     DEFAULT NULL COMMENT '临时锁定截止时间',
                  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (account),
                  KEY idx_login_attempt_locked_until (locked_until)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录失败锁定状态'
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
        addColumnIfMissing(jdbc, "exam_attempt", "submit_type",
                "ALTER TABLE exam_attempt ADD COLUMN submit_type VARCHAR(32) DEFAULT NULL COMMENT 'MANUAL/TIMEOUT/FORCED' AFTER submit_time");
        addColumnIfMissing(jdbc, "exam_attempt", "submit_reason",
                "ALTER TABLE exam_attempt ADD COLUMN submit_reason VARCHAR(255) DEFAULT NULL AFTER submit_type");
        addColumnIfMissing(jdbc, "exam_attempt", "rules_confirmed_at",
                "ALTER TABLE exam_attempt ADD COLUMN rules_confirmed_at DATETIME DEFAULT NULL AFTER start_time");
        addIndexIfMissing(jdbc, "exam_attempt", "idx_attempt_exam_user_no",
                "ALTER TABLE exam_attempt ADD INDEX idx_attempt_exam_user_no (exam_id, user_id, attempt_no)");
    }

    private void ensureExamPublishColumnsClean(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "exam", "max_attempts",
                "ALTER TABLE exam ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '允许考试次数' AFTER duration_minutes");
        addColumnIfMissing(jdbc, "exam", "pass_score",
                "ALTER TABLE exam ADD COLUMN pass_score DECIMAL(10,2) DEFAULT NULL COMMENT '及格线' AFTER max_attempts");
        addColumnIfMissing(jdbc, "exam_attempt", "attempt_no",
                "ALTER TABLE exam_attempt ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '第几次作答' AFTER user_id");
        addIndexIfMissing(jdbc, "exam_attempt", "idx_attempt_exam_user_no",
                "ALTER TABLE exam_attempt ADD INDEX idx_attempt_exam_user_no (exam_id, user_id, attempt_no)");
    }

    private void ensureAttemptSubmitAuditColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "exam_attempt", "submit_type",
                "ALTER TABLE exam_attempt ADD COLUMN submit_type VARCHAR(32) DEFAULT NULL COMMENT 'MANUAL/TIMEOUT/FORCED' AFTER submit_time");
        addColumnIfMissing(jdbc, "exam_attempt", "submit_reason",
                "ALTER TABLE exam_attempt ADD COLUMN submit_reason VARCHAR(255) DEFAULT NULL AFTER submit_type");
    }

    private void ensureAttemptResilienceColumns(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_answer_draft table", """
                CREATE TABLE IF NOT EXISTS exam_answer_draft (
                  id         BIGINT   NOT NULL AUTO_INCREMENT,
                  attempt_id BIGINT   NOT NULL,
                  answers    TEXT,
                  client_draft_id VARCHAR(80) DEFAULT NULL,
                  revision BIGINT NOT NULL DEFAULT 0,
                  saved_count INT NOT NULL DEFAULT 0,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_draft_attempt (attempt_id),
                  KEY idx_draft_attempt_revision (attempt_id, revision)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing(jdbc, "exam_attempt", "last_heartbeat_at",
                "ALTER TABLE exam_attempt ADD COLUMN last_heartbeat_at DATETIME DEFAULT NULL AFTER submit_reason");
        addColumnIfMissing(jdbc, "exam_attempt", "rules_confirmed_at",
                "ALTER TABLE exam_attempt ADD COLUMN rules_confirmed_at DATETIME DEFAULT NULL AFTER start_time");
        addColumnIfMissing(jdbc, "exam_attempt", "last_draft_saved_at",
                "ALTER TABLE exam_attempt ADD COLUMN last_draft_saved_at DATETIME DEFAULT NULL AFTER last_heartbeat_at");
        addColumnIfMissing(jdbc, "exam_attempt", "draft_version",
                "ALTER TABLE exam_attempt ADD COLUMN draft_version BIGINT NOT NULL DEFAULT 0 AFTER last_draft_saved_at");
        addColumnIfMissing(jdbc, "exam_attempt", "submit_token",
                "ALTER TABLE exam_attempt ADD COLUMN submit_token VARCHAR(80) DEFAULT NULL AFTER draft_version");
        addColumnIfMissing(jdbc, "exam_attempt", "submit_payload_hash",
                "ALTER TABLE exam_attempt ADD COLUMN submit_payload_hash VARCHAR(64) DEFAULT NULL AFTER submit_token");
        addIndexIfMissing(jdbc, "exam_attempt", "idx_attempt_submit_token",
                "ALTER TABLE exam_attempt ADD INDEX idx_attempt_submit_token (submit_token)");

        addColumnIfMissing(jdbc, "exam_answer_draft", "client_draft_id",
                "ALTER TABLE exam_answer_draft ADD COLUMN client_draft_id VARCHAR(80) DEFAULT NULL AFTER answers");
        addColumnIfMissing(jdbc, "exam_answer_draft", "revision",
                "ALTER TABLE exam_answer_draft ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 AFTER client_draft_id");
        addColumnIfMissing(jdbc, "exam_answer_draft", "saved_count",
                "ALTER TABLE exam_answer_draft ADD COLUMN saved_count INT NOT NULL DEFAULT 0 AFTER revision");
        addColumnIfMissing(jdbc, "exam_answer_draft", "created_at",
                "ALTER TABLE exam_answer_draft ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER saved_count");
        addIndexIfMissing(jdbc, "exam_answer_draft", "idx_draft_attempt_revision",
                "ALTER TABLE exam_answer_draft ADD INDEX idx_draft_attempt_revision (attempt_id, revision)");
        deduplicateExamAnswerDraftsBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "exam_answer_draft", "uk_draft_attempt",
                "ALTER TABLE exam_answer_draft ADD UNIQUE KEY uk_draft_attempt (attempt_id)");
    }

    private void ensureNotificationRelationColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "notification", "related_type",
                "ALTER TABLE notification ADD COLUMN related_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型，如 EXAM_ATTEMPT' AFTER link");
        addColumnIfMissing(jdbc, "notification", "related_id",
                "ALTER TABLE notification ADD COLUMN related_id BIGINT DEFAULT NULL COMMENT '关联业务ID' AFTER related_type");
        addColumnIfMissing(jdbc, "notification", "dedup_key",
                "ALTER TABLE notification ADD COLUMN dedup_key VARCHAR(255) DEFAULT NULL AFTER related_id");
        addIndexIfMissing(jdbc, "notification", "idx_notification_related",
                "ALTER TABLE notification ADD INDEX idx_notification_related (related_type, related_id)");
        addIndexIfMissing(jdbc, "notification", "uk_notification_dedup_key",
                "ALTER TABLE notification ADD UNIQUE KEY uk_notification_dedup_key (dedup_key)");
    }

    private void ensureSystemConfigLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create system_config_log table", """
                CREATE TABLE IF NOT EXISTS system_config_log (
                  id          BIGINT        NOT NULL AUTO_INCREMENT,
                  config_key  VARCHAR(128)  NOT NULL,
                  old_value   VARCHAR(1000) DEFAULT NULL,
                  new_value   VARCHAR(1000) NOT NULL,
                  value_type  VARCHAR(32)   NOT NULL,
                  category    VARCHAR(64)   NOT NULL,
                  actor_id    BIGINT        NOT NULL,
                  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_config_log_key_time (config_key, created_at),
                  KEY idx_config_log_category_time (category, created_at),
                  KEY idx_config_log_actor_time (actor_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureExamSubmitResponseTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_submit_response table", """
                CREATE TABLE IF NOT EXISTS exam_submit_response (
                  id                  BIGINT      NOT NULL AUTO_INCREMENT,
                  attempt_id          BIGINT      NOT NULL,
                  submit_token        VARCHAR(80) DEFAULT NULL,
                  submit_payload_hash VARCHAR(64) DEFAULT NULL,
                  response_json       LONGTEXT    NOT NULL,
                  created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_exam_submit_response_attempt (attempt_id),
                  KEY idx_exam_submit_response_token (submit_token),
                  KEY idx_exam_submit_response_hash (submit_payload_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试提交响应快照'
                """);
        addColumnIfMissing(jdbc, "exam_submit_response", "submit_token",
                "ALTER TABLE exam_submit_response ADD COLUMN submit_token VARCHAR(80) DEFAULT NULL AFTER attempt_id");
        addColumnIfMissing(jdbc, "exam_submit_response", "submit_payload_hash",
                "ALTER TABLE exam_submit_response ADD COLUMN submit_payload_hash VARCHAR(64) DEFAULT NULL AFTER submit_token");
        addColumnIfMissing(jdbc, "exam_submit_response", "response_json",
                "ALTER TABLE exam_submit_response ADD COLUMN response_json LONGTEXT DEFAULT NULL AFTER submit_payload_hash");
        addColumnIfMissing(jdbc, "exam_submit_response", "created_at",
                "ALTER TABLE exam_submit_response ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER response_json");
        addColumnIfMissing(jdbc, "exam_submit_response", "updated_at",
                "ALTER TABLE exam_submit_response ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
        deduplicateExamSubmitResponsesBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "exam_submit_response", "uk_exam_submit_response_attempt",
                "ALTER TABLE exam_submit_response ADD UNIQUE KEY uk_exam_submit_response_attempt (attempt_id)");
        addIndexIfMissing(jdbc, "exam_submit_response", "idx_exam_submit_response_token",
                "ALTER TABLE exam_submit_response ADD INDEX idx_exam_submit_response_token (submit_token)");
        addIndexIfMissing(jdbc, "exam_submit_response", "idx_exam_submit_response_hash",
                "ALTER TABLE exam_submit_response ADD INDEX idx_exam_submit_response_hash (submit_payload_hash)");
    }

    /** Exam snapshots freeze candidate scope and paper content at publish time. */
    private void ensureExamSnapshotTables(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_candidate_snapshot table", """
                CREATE TABLE IF NOT EXISTS exam_candidate_snapshot (
                  id          BIGINT       NOT NULL AUTO_INCREMENT,
                  exam_id     BIGINT       NOT NULL,
                  user_id     BIGINT       NOT NULL COMMENT '考生 sys_user.id',
                  source_type VARCHAR(32)  NOT NULL DEFAULT 'LEGACY' COMMENT 'CLASS/CLASS_COURSE/USER/LEGACY',
                  source_id   BIGINT       DEFAULT NULL,
                  real_name   VARCHAR(64)  DEFAULT NULL,
                  student_no  VARCHAR(64)  DEFAULT NULL,
                  class_name  VARCHAR(128) DEFAULT NULL,
                  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_exam_candidate_snapshot (exam_id, user_id),
                  KEY idx_exam_candidate_user (user_id, exam_id),
                  KEY idx_exam_candidate_source (source_type, source_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试考生范围快照'
                """);
        executeQuietly(jdbc, "create exam_question_snapshot table", """
                CREATE TABLE IF NOT EXISTS exam_question_snapshot (
                  id             BIGINT        NOT NULL AUTO_INCREMENT,
                  exam_id        BIGINT        NOT NULL,
                  paper_id       BIGINT        NOT NULL,
                  question_id    BIGINT        NOT NULL,
                  knowledge_point_id BIGINT    DEFAULT NULL,
                  question_type  VARCHAR(32)   NOT NULL,
                  stem           TEXT          NOT NULL,
                  correct_answer TEXT          DEFAULT NULL,
                  analysis       TEXT          DEFAULT NULL,
                  score          DECIMAL(6,2)  NOT NULL DEFAULT 0.00,
                  sort_order     INT           NOT NULL DEFAULT 0,
                  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_exam_question_snapshot (exam_id, question_id),
                  KEY idx_exam_question_snapshot_exam (exam_id, sort_order),
                  KEY idx_exam_question_snapshot_question (question_id),
                  KEY idx_exam_question_snapshot_kp (knowledge_point_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试试卷题目快照'
                """);
        executeQuietly(jdbc, "create exam_question_option_snapshot table", """
                CREATE TABLE IF NOT EXISTS exam_question_option_snapshot (
                  id             BIGINT        NOT NULL AUTO_INCREMENT,
                  exam_id        BIGINT        NOT NULL,
                  question_id    BIGINT        NOT NULL,
                  option_label   VARCHAR(16)   NOT NULL,
                  option_content VARCHAR(1000) NOT NULL,
                  sort_order     INT           NOT NULL DEFAULT 0,
                  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_exam_question_option_snapshot (exam_id, question_id, option_label),
                  KEY idx_exam_question_option_snapshot (exam_id, question_id, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试试卷选项快照'
                """);
    }

    private void ensureExamApprovalLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_approval_log table", """
                CREATE TABLE IF NOT EXISTS exam_approval_log (
                  id          BIGINT      NOT NULL AUTO_INCREMENT,
                  exam_id     BIGINT      NOT NULL,
                  action      VARCHAR(32) NOT NULL,
                  status_from TINYINT     DEFAULT NULL,
                  status_to   TINYINT     DEFAULT NULL,
                  note        VARCHAR(1000) DEFAULT NULL,
                  actor_id    BIGINT      NOT NULL,
                  candidate_count        INT NOT NULL DEFAULT 0,
                  notified_student_count INT NOT NULL DEFAULT 0,
                  notified_attempt_count INT NOT NULL DEFAULT 0,
                  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_exam_approval_exam (exam_id, created_at),
                  KEY idx_exam_approval_actor (actor_id, created_at),
                  KEY idx_exam_approval_action (action, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing(jdbc, "exam_approval_log", "candidate_count",
                "ALTER TABLE exam_approval_log ADD COLUMN candidate_count INT NOT NULL DEFAULT 0 AFTER actor_id");
        addColumnIfMissing(jdbc, "exam_approval_log", "notified_student_count",
                "ALTER TABLE exam_approval_log ADD COLUMN notified_student_count INT NOT NULL DEFAULT 0 AFTER candidate_count");
        addColumnIfMissing(jdbc, "exam_approval_log", "notified_attempt_count",
                "ALTER TABLE exam_approval_log ADD COLUMN notified_attempt_count INT NOT NULL DEFAULT 0 AFTER notified_student_count");
    }

    private void ensureExamApprovalReminderLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_approval_reminder_log table", """
                CREATE TABLE IF NOT EXISTS exam_approval_reminder_log (
                  id                 BIGINT       NOT NULL AUTO_INCREMENT,
                  triggered_by       BIGINT       NOT NULL,
                  overdue_hours      INT          NOT NULL,
                  cooldown_hours     INT          NOT NULL,
                  overdue_exam_count INT          NOT NULL DEFAULT 0,
                  recipient_count    INT          NOT NULL DEFAULT 0,
                  status             VARCHAR(32)  NOT NULL,
                  trigger_source     VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
                  node_id            VARCHAR(128) DEFAULT NULL,
                  duration_ms        BIGINT       DEFAULT NULL,
                  message            VARCHAR(1000) DEFAULT NULL,
                  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_approval_reminder_created (created_at),
                  KEY idx_approval_reminder_status (status, created_at),
                  KEY idx_approval_reminder_actor (triggered_by, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureExamApprovalReminderLogColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "exam_approval_reminder_log", "trigger_source",
                "ALTER TABLE exam_approval_reminder_log ADD COLUMN trigger_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' AFTER status");
        addIndexIfMissing(jdbc, "exam_approval_reminder_log", "idx_approval_reminder_source",
                "ALTER TABLE exam_approval_reminder_log ADD INDEX idx_approval_reminder_source (trigger_source, created_at)");
        addColumnIfMissing(jdbc, "exam_approval_reminder_log", "node_id",
                "ALTER TABLE exam_approval_reminder_log ADD COLUMN node_id VARCHAR(128) DEFAULT NULL AFTER trigger_source");
        addColumnIfMissing(jdbc, "exam_approval_reminder_log", "duration_ms",
                "ALTER TABLE exam_approval_reminder_log ADD COLUMN duration_ms BIGINT DEFAULT NULL AFTER node_id");
        addIndexIfMissing(jdbc, "exam_approval_reminder_log", "idx_approval_reminder_node",
                "ALTER TABLE exam_approval_reminder_log ADD INDEX idx_approval_reminder_node (node_id, created_at)");
    }

    private void ensureSystemJobLockTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create system_job_lock table", """
                CREATE TABLE IF NOT EXISTS system_job_lock (
                  id           BIGINT       NOT NULL AUTO_INCREMENT,
                  lock_key     VARCHAR(128) NOT NULL,
                  owner_id     VARCHAR(128) NOT NULL,
                  locked_until DATETIME     NOT NULL,
                  acquired_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_system_job_lock_key (lock_key),
                  KEY idx_system_job_lock_until (locked_until),
                  KEY idx_system_job_lock_owner (owner_id, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    /** Score release table: students can only see grades after an explicit publish action. */
    private void ensureScoreReleaseTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_release table", """
                CREATE TABLE IF NOT EXISTS score_release (
                  id           BIGINT   NOT NULL AUTO_INCREMENT,
                  exam_id      BIGINT   NOT NULL,
                  status       TINYINT  NOT NULL DEFAULT 0 COMMENT '0未发布/已撤回 1已发布',
                  published_by BIGINT   DEFAULT NULL,
                  published_at DATETIME DEFAULT NULL,
                  revoked_by   BIGINT   DEFAULT NULL,
                  revoked_at   DATETIME DEFAULT NULL,
                  publish_note VARCHAR(500) DEFAULT NULL,
                  revoke_reason VARCHAR(500) DEFAULT NULL,
                  note         VARCHAR(500) DEFAULT NULL,
                  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_score_release_exam (exam_id),
                  KEY idx_score_release_status (status, published_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩发布状态'
                """);
    }

    /** Score appeal table: students can request manual handling after scores are released. */
    private void ensureScoreAppealTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_appeal table", """
                CREATE TABLE IF NOT EXISTS score_appeal (
                  id            BIGINT   NOT NULL AUTO_INCREMENT,
                  attempt_id    BIGINT   NOT NULL,
                  exam_id       BIGINT   NOT NULL COMMENT '考试ID快照，用于成绩发布阻断',
                  question_id   BIGINT   DEFAULT NULL,
                  user_id       BIGINT   NOT NULL COMMENT '申诉学生 sys_user.id',
                  reason        VARCHAR(1000) NOT NULL,
                  status        TINYINT  NOT NULL DEFAULT 0 COMMENT '0待处理 1已回复 2已关闭',
                  teacher_reply VARCHAR(1000) DEFAULT NULL,
                  handling_result VARCHAR(32) DEFAULT NULL,
                  handled_by    BIGINT   DEFAULT NULL,
                  handled_at    DATETIME DEFAULT NULL,
                  recheck_note  VARCHAR(1000) DEFAULT NULL,
                  rechecked_by  BIGINT   DEFAULT NULL,
                  rechecked_at  DATETIME DEFAULT NULL,
                  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_score_appeal_attempt (attempt_id),
                  KEY idx_score_appeal_exam_status (exam_id, status, handling_result),
                  KEY idx_score_appeal_active_target (attempt_id, user_id, question_id, status),
                  KEY idx_score_appeal_user (user_id, created_at),
                  KEY idx_score_appeal_status (status, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩申诉'
                """);
    }

    /** System runtime settings maintained by admins. */
    private void ensureSystemConfigTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create system_config table", """
                CREATE TABLE IF NOT EXISTS system_config (
                  id           BIGINT        NOT NULL AUTO_INCREMENT,
                  config_key   VARCHAR(128)  NOT NULL,
                  config_value VARCHAR(1000) NOT NULL,
                  value_type   VARCHAR(32)   NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN',
                  category     VARCHAR(64)   NOT NULL DEFAULT 'GENERAL',
                  description  VARCHAR(255)  DEFAULT NULL,
                  editable     TINYINT       NOT NULL DEFAULT 1,
                  updated_by   BIGINT        DEFAULT NULL,
                  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_system_config_key (config_key),
                  KEY idx_system_config_category (category)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置'
                """);
    }

    private void ensureScoreReleaseTableClean(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_release table", """
                CREATE TABLE IF NOT EXISTS score_release (
                  id           BIGINT   NOT NULL AUTO_INCREMENT,
                  exam_id      BIGINT   NOT NULL,
                  status       TINYINT  NOT NULL DEFAULT 0 COMMENT '0未发布/已撤回 1已发布',
                  published_by BIGINT   DEFAULT NULL,
                  published_at DATETIME DEFAULT NULL,
                  revoked_by   BIGINT   DEFAULT NULL,
                  revoked_at   DATETIME DEFAULT NULL,
                  publish_note VARCHAR(500) DEFAULT NULL,
                  revoke_reason VARCHAR(500) DEFAULT NULL,
                  note         VARCHAR(500) DEFAULT NULL,
                  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_score_release_exam (exam_id),
                  KEY idx_score_release_status (status, published_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩发布状态'
                """);
    }

    private void ensureScoreReleaseNoteColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "score_release", "note",
                "ALTER TABLE score_release ADD COLUMN note VARCHAR(500) DEFAULT NULL AFTER revoked_at");
    }

    private void ensureScoreReleaseAuditColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "score_release", "publish_note",
                "ALTER TABLE score_release ADD COLUMN publish_note VARCHAR(500) DEFAULT NULL AFTER revoked_at");
        addColumnIfMissing(jdbc, "score_release", "revoke_reason",
                "ALTER TABLE score_release ADD COLUMN revoke_reason VARCHAR(500) DEFAULT NULL AFTER publish_note");
        executeQuietly(jdbc, "backfill score release revoke reason", """
                UPDATE score_release
                SET revoke_reason = note
                WHERE revoke_reason IS NULL AND note IS NOT NULL
                """);
    }

    private void ensureScoreReleaseUniqueExam(JdbcTemplate jdbc) {
        deduplicateScoreReleaseBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "score_release", "uk_score_release_exam",
                "ALTER TABLE score_release ADD UNIQUE KEY uk_score_release_exam (exam_id)");
        addIndexIfMissing(jdbc, "score_release", "idx_score_release_status",
                "ALTER TABLE score_release ADD INDEX idx_score_release_status (status, published_at)");
    }

    private void deduplicateScoreReleaseBeforeUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_score_release_merge");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_score_release_dedup");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_score_release_merge AS
                            SELECT sr.exam_id,
                                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(sr.id ORDER BY
                                           sr.status DESC,
                                           sr.published_at DESC,
                                           sr.revoked_at DESC,
                                           sr.updated_at DESC,
                                           sr.id DESC), ',', 1) AS UNSIGNED) AS keep_id,
                                   MAX(sr.status) AS merged_status,
                                   MAX(sr.published_at) AS merged_published_at,
                                   MAX(sr.revoked_at) AS merged_revoked_at,
                                   MIN(sr.created_at) AS merged_created_at
                            FROM score_release sr
                            GROUP BY sr.exam_id
                            HAVING COUNT(*) > 1
                            """);
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_score_release_dedup AS
                            SELECT sr.id AS duplicate_id, m.keep_id
                            FROM score_release sr
                            JOIN tmp_score_release_merge m ON m.exam_id = sr.exam_id
                            WHERE sr.id <> m.keep_id
                            """);
                    statement.execute("""
                            UPDATE score_release sr
                            JOIN tmp_score_release_merge m ON m.keep_id = sr.id
                            SET sr.status = m.merged_status,
                                sr.published_at = CASE
                                    WHEN m.merged_status = 1 THEN COALESCE(sr.published_at, m.merged_published_at)
                                    ELSE sr.published_at
                                END,
                                sr.revoked_at = CASE
                                    WHEN m.merged_status = 0 THEN COALESCE(sr.revoked_at, m.merged_revoked_at)
                                    ELSE sr.revoked_at
                                END,
                                sr.created_at = LEAST(sr.created_at, m.merged_created_at)
                            """);
                    statement.execute("""
                            DELETE sr
                            FROM score_release sr
                            JOIN tmp_score_release_dedup d ON d.duplicate_id = sr.id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_score_release_dedup");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_score_release_merge");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: score_release duplicate cleanup failed before adding unique exam index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureScoreReleaseLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_release_log table", """
                CREATE TABLE IF NOT EXISTS score_release_log (
                  id                     BIGINT       NOT NULL AUTO_INCREMENT,
                  exam_id                BIGINT       NOT NULL,
                  action                 VARCHAR(32)  NOT NULL,
                  status_from            TINYINT      DEFAULT NULL,
                  status_to              TINYINT      NOT NULL,
                  note                   VARCHAR(500) DEFAULT NULL,
                  actor_id               BIGINT       NOT NULL,
                  visible_attempt_count  INT          NOT NULL DEFAULT 0,
                  notified_student_count INT          NOT NULL DEFAULT 0,
                  notified_attempt_count INT          NOT NULL DEFAULT 0,
                  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_score_release_log_exam (exam_id, created_at),
                  KEY idx_score_release_log_actor (actor_id, created_at),
                  KEY idx_score_release_log_action (action, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='score release audit log'
                """);
    }

    private void ensureScoreAppealTableClean(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_appeal table", """
                CREATE TABLE IF NOT EXISTS score_appeal (
                  id            BIGINT   NOT NULL AUTO_INCREMENT,
                  attempt_id    BIGINT   NOT NULL,
                  exam_id       BIGINT   NOT NULL COMMENT '考试ID快照，用于成绩发布阻断',
                  question_id   BIGINT   DEFAULT NULL,
                  user_id       BIGINT   NOT NULL COMMENT '申诉学生 sys_user.id',
                  reason        VARCHAR(1000) NOT NULL,
                  status        TINYINT  NOT NULL DEFAULT 0 COMMENT '0待处理 1已回复 2已关闭',
                  teacher_reply VARCHAR(1000) DEFAULT NULL,
                  handling_result VARCHAR(32) DEFAULT NULL,
                  handled_by    BIGINT   DEFAULT NULL,
                  handled_at    DATETIME DEFAULT NULL,
                  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_score_appeal_attempt (attempt_id),
                  KEY idx_score_appeal_exam_status (exam_id, status, handling_result),
                  KEY idx_score_appeal_active_target (attempt_id, user_id, question_id, status),
                  KEY idx_score_appeal_user (user_id, created_at),
                  KEY idx_score_appeal_status (status, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩申诉'
                """);
    }

    private void ensureScoreAppealExamIdColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "score_appeal", "exam_id",
                "ALTER TABLE score_appeal ADD COLUMN exam_id BIGINT DEFAULT NULL AFTER attempt_id");
        executeQuietly(jdbc, "backfill score appeal exam id", """
                UPDATE score_appeal sa
                JOIN exam_attempt a ON a.id = sa.attempt_id
                SET sa.exam_id = a.exam_id
                WHERE sa.exam_id IS NULL OR sa.exam_id <> a.exam_id
                """);
        addIndexIfMissing(jdbc, "score_appeal", "idx_score_appeal_exam_status",
                "ALTER TABLE score_appeal ADD INDEX idx_score_appeal_exam_status "
                        + "(exam_id, status, handling_result)");
    }

    private void ensureScoreAppealActiveTargetIndex(JdbcTemplate jdbc) {
        addIndexIfMissing(jdbc, "score_appeal", "idx_score_appeal_active_target",
                "ALTER TABLE score_appeal ADD INDEX idx_score_appeal_active_target "
                        + "(attempt_id, user_id, question_id, status)");
    }

    private void ensureScoreAppealHandlingResultColumn(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "score_appeal", "handling_result",
                "ALTER TABLE score_appeal ADD COLUMN handling_result VARCHAR(32) DEFAULT NULL AFTER teacher_reply");
        executeQuietly(jdbc, "backfill score appeal handling result", """
                UPDATE score_appeal
                SET handling_result = 'MAINTAINED'
                WHERE handling_result IS NULL AND status <> 0
                """);
    }

    private void ensureScoreAppealRecheckColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "score_appeal", "recheck_note",
                "ALTER TABLE score_appeal ADD COLUMN recheck_note VARCHAR(1000) DEFAULT NULL AFTER handled_at");
        addColumnIfMissing(jdbc, "score_appeal", "rechecked_by",
                "ALTER TABLE score_appeal ADD COLUMN rechecked_by BIGINT DEFAULT NULL AFTER recheck_note");
        addColumnIfMissing(jdbc, "score_appeal", "rechecked_at",
                "ALTER TABLE score_appeal ADD COLUMN rechecked_at DATETIME DEFAULT NULL AFTER rechecked_by");
    }

    private void ensureScoreAppealLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create score_appeal_log table", """
                CREATE TABLE IF NOT EXISTS score_appeal_log (
                  id              BIGINT        NOT NULL AUTO_INCREMENT,
                  appeal_id       BIGINT        NOT NULL,
                  attempt_id      BIGINT        NOT NULL,
                  exam_id         BIGINT        NOT NULL,
                  question_id     BIGINT        DEFAULT NULL,
                  user_id         BIGINT        NOT NULL,
                  action          VARCHAR(32)   NOT NULL,
                  status_from     TINYINT       DEFAULT NULL,
                  status_to       TINYINT       NOT NULL,
                  handling_result VARCHAR(32)   DEFAULT NULL,
                  note            VARCHAR(1000) DEFAULT NULL,
                  actor_id        BIGINT        NOT NULL,
                  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_score_appeal_log_appeal (appeal_id, created_at),
                  KEY idx_score_appeal_log_exam (exam_id, created_at),
                  KEY idx_score_appeal_log_actor (actor_id, created_at),
                  KEY idx_score_appeal_log_action (action, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='score appeal audit log'
                """);
    }

    private void ensureScoreAppealLogExamIdConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile score appeal log exam id", """
                UPDATE score_appeal_log l
                JOIN exam_attempt a ON a.id = l.attempt_id
                SET l.exam_id = a.exam_id
                WHERE l.exam_id IS NULL OR l.exam_id <> a.exam_id
                """);
    }

    private void ensureReviewScoreLogTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create review_score_log table", """
                CREATE TABLE IF NOT EXISTS review_score_log (
                  id               BIGINT        NOT NULL AUTO_INCREMENT,
                  attempt_id       BIGINT        NOT NULL,
                  answer_record_id BIGINT        NOT NULL,
                  question_id      BIGINT        NOT NULL,
                  exam_id          BIGINT        NOT NULL,
                  user_id          BIGINT        NOT NULL,
                  old_score        DECIMAL(6,2)  DEFAULT NULL,
                  new_score        DECIMAL(6,2)  NOT NULL DEFAULT 0.00,
                  max_score        DECIMAL(6,2)  NOT NULL DEFAULT 0.00,
                  comment          VARCHAR(1000) DEFAULT NULL,
                  reviewer_id      BIGINT        NOT NULL,
                  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_review_score_log_attempt (attempt_id, created_at),
                  KEY idx_review_score_log_answer (answer_record_id, created_at),
                  KEY idx_review_score_log_exam (exam_id, created_at),
                  KEY idx_review_score_log_reviewer (reviewer_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='review score audit log'
                """);
    }

    private void ensureReviewScoreLogExamIdConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile review score log exam id", """
                UPDATE review_score_log l
                JOIN exam_attempt a ON a.id = l.attempt_id
                SET l.exam_id = a.exam_id
                WHERE l.exam_id IS NULL OR l.exam_id <> a.exam_id
                """);
    }

    private void ensureReviewScoreLogAnswerConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile review score log answer ownership", """
                UPDATE review_score_log l
                JOIN answer_record ar ON ar.id = l.answer_record_id
                JOIN exam_attempt a ON a.id = ar.attempt_id
                SET l.attempt_id = ar.attempt_id,
                    l.question_id = ar.question_id,
                    l.exam_id = a.exam_id,
                    l.user_id = a.user_id
                WHERE l.attempt_id <> ar.attempt_id
                   OR l.question_id <> ar.question_id
                   OR l.exam_id <> a.exam_id
                   OR l.user_id <> a.user_id
                """);
    }

    private void ensureWrongQuestionBookSnapshotIdentity(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create wrong_question_book table", """
                CREATE TABLE IF NOT EXISTS wrong_question_book (
                  id              BIGINT   NOT NULL AUTO_INCREMENT,
                  user_id         BIGINT   NOT NULL,
                  exam_id         BIGINT   NOT NULL DEFAULT 0,
                  question_id     BIGINT   NOT NULL,
                  wrong_count     INT      NOT NULL DEFAULT 1,
                  last_wrong_time DATETIME DEFAULT NULL,
                  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_wrong_user_exam_question (user_id, exam_id, question_id),
                  KEY idx_wrong_user (user_id),
                  KEY idx_wrong_exam_question (exam_id, question_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wrong question book'
                """);
        addColumnIfMissing(jdbc, "wrong_question_book", "exam_id",
                "ALTER TABLE wrong_question_book ADD COLUMN exam_id BIGINT NOT NULL DEFAULT 0 AFTER user_id");
        executeQuietly(jdbc, "backfill wrong question book exam identity", """
                UPDATE wrong_question_book w
                JOIN (
                  SELECT ea.user_id, ar.question_id, MAX(ea.exam_id) AS exam_id
                  FROM answer_record ar
                  JOIN exam_attempt ea ON ea.id = ar.attempt_id
                  JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                  JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                  WHERE ea.status = 5
                    AND ea.score IS NOT NULL
                    AND ar.review_status = 1
                    AND ar.is_correct = 0
                  GROUP BY ea.user_id, ar.question_id
                ) source ON source.user_id = w.user_id AND source.question_id = w.question_id
                SET w.exam_id = source.exam_id
                WHERE w.exam_id = 0
                """);
        executeQuietly(jdbc, "drop legacy wrong question unique key",
                "ALTER TABLE wrong_question_book DROP INDEX uk_wrong_user_question");
        deduplicateWrongQuestionBookBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "wrong_question_book", "uk_wrong_user_exam_question",
                "ALTER TABLE wrong_question_book ADD UNIQUE KEY uk_wrong_user_exam_question (user_id, exam_id, question_id)");
        addIndexIfMissing(jdbc, "wrong_question_book", "idx_wrong_exam_question",
                "ALTER TABLE wrong_question_book ADD INDEX idx_wrong_exam_question (exam_id, question_id)");
    }

    private void deduplicateWrongQuestionBookBeforeUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_wrong_question_book_merge");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_wrong_question_book_merge AS
                            SELECT user_id,
                                   exam_id,
                                   question_id,
                                   MAX(id) AS keep_id,
                                   SUM(wrong_count) AS merged_wrong_count,
                                   MAX(last_wrong_time) AS merged_last_wrong_time,
                                   MIN(created_at) AS merged_created_at
                            FROM wrong_question_book
                            GROUP BY user_id, exam_id, question_id
                            HAVING COUNT(*) > 1
                            """);
                    statement.execute("""
                            UPDATE wrong_question_book w
                            JOIN tmp_wrong_question_book_merge m ON m.keep_id = w.id
                            SET w.wrong_count = m.merged_wrong_count,
                                w.last_wrong_time = COALESCE(m.merged_last_wrong_time, w.last_wrong_time),
                                w.created_at = LEAST(w.created_at, m.merged_created_at)
                            """);
                    statement.execute("""
                            DELETE w
                            FROM wrong_question_book w
                            JOIN tmp_wrong_question_book_merge m
                              ON m.user_id = w.user_id
                             AND m.exam_id = w.exam_id
                             AND m.question_id = w.question_id
                            WHERE w.id <> m.keep_id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_wrong_question_book_merge");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: wrong_question_book duplicate cleanup failed before adding snapshot identity unique index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureAnswerRecordUniqueIndex(JdbcTemplate jdbc) {
        deduplicateAnswerRecordsBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "answer_record", "uk_answer_attempt_question",
                "ALTER TABLE answer_record ADD UNIQUE KEY uk_answer_attempt_question (attempt_id, question_id)");
    }

    private void deduplicateAnswerRecordsBeforeUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_answer_record_dedup");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_answer_record_dedup AS
                            SELECT ar.id AS duplicate_id, keepers.keep_id
                            FROM answer_record ar
                            JOIN (
                              SELECT attempt_id, question_id, MAX(id) AS keep_id
                              FROM answer_record
                              GROUP BY attempt_id, question_id
                              HAVING COUNT(*) > 1
                            ) keepers
                              ON keepers.attempt_id = ar.attempt_id
                             AND keepers.question_id = ar.question_id
                            WHERE ar.id <> keepers.keep_id
                            """);
                    statement.execute("""
                            UPDATE review_record rr
                            JOIN tmp_answer_record_dedup d ON d.duplicate_id = rr.answer_record_id
                            SET rr.answer_record_id = d.keep_id
                            """);
                    statement.execute("""
                            UPDATE review_score_log rsl
                            JOIN tmp_answer_record_dedup d ON d.duplicate_id = rsl.answer_record_id
                            SET rsl.answer_record_id = d.keep_id
                            """);
                    statement.execute("""
                            DELETE ar FROM answer_record ar
                            JOIN tmp_answer_record_dedup d ON d.duplicate_id = ar.id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_answer_record_dedup");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: answer_record duplicate cleanup failed before adding unique index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void deduplicateExamAnswerDraftsBeforeUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_answer_draft_merge");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_exam_answer_draft_merge AS
                            SELECT d.attempt_id,
                                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(d.id ORDER BY d.revision DESC,
                                           d.updated_at DESC, d.id DESC), ',', 1) AS UNSIGNED) AS keep_id,
                                   SUM(COALESCE(d.saved_count, 0)) AS merged_saved_count,
                                   MIN(d.created_at) AS merged_created_at
                            FROM exam_answer_draft d
                            GROUP BY d.attempt_id
                            HAVING COUNT(*) > 1
                            """);
                    statement.execute("""
                            UPDATE exam_answer_draft d
                            JOIN tmp_exam_answer_draft_merge m ON m.keep_id = d.id
                            SET d.saved_count = GREATEST(d.saved_count, m.merged_saved_count),
                                d.created_at = LEAST(d.created_at, m.merged_created_at)
                            """);
                    statement.execute("""
                            DELETE d
                            FROM exam_answer_draft d
                            JOIN tmp_exam_answer_draft_merge m ON m.attempt_id = d.attempt_id
                            WHERE d.id <> m.keep_id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_answer_draft_merge");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: exam_answer_draft duplicate cleanup failed before adding unique attempt index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void deduplicateExamSubmitResponsesBeforeUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_submit_response_merge");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_exam_submit_response_merge AS
                            SELECT r.attempt_id,
                                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(r.id ORDER BY
                                           CASE WHEN r.response_json IS NULL OR r.response_json = '' THEN 0 ELSE 1 END DESC,
                                           r.updated_at DESC,
                                           r.id DESC), ',', 1) AS UNSIGNED) AS keep_id
                            FROM exam_submit_response r
                            GROUP BY r.attempt_id
                            HAVING COUNT(*) > 1
                            """);
                    statement.execute("""
                            DELETE r
                            FROM exam_submit_response r
                            JOIN tmp_exam_submit_response_merge m ON m.attempt_id = r.attempt_id
                            WHERE r.id <> m.keep_id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_submit_response_merge");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: exam_submit_response duplicate cleanup failed before adding unique attempt index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureExamAttemptUniqueIdentity(JdbcTemplate jdbc) {
        deduplicateExamAttemptsBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "exam_attempt", "uk_attempt_exam_user_no",
                "ALTER TABLE exam_attempt ADD UNIQUE KEY uk_attempt_exam_user_no (exam_id, user_id, attempt_no)");
    }

    private void deduplicateExamAttemptsBeforeUniqueIndex(JdbcTemplate jdbc) {
        boolean hasAnswerRecord = tableExists(jdbc, "answer_record");
        boolean hasReviewRecord = tableExists(jdbc, "review_record");
        boolean hasReviewScoreLog = tableExists(jdbc, "review_score_log");
        boolean hasSubmitResponse = tableExists(jdbc, "exam_submit_response");
        boolean hasScoreAppeal = tableExists(jdbc, "score_appeal");
        boolean hasScoreAppealLog = tableExists(jdbc, "score_appeal_log");
        boolean hasCheatEvent = tableExists(jdbc, "cheat_event");
        boolean hasMonitorSession = tableExists(jdbc, "exam_monitor_session");
        boolean hasMonitorAction = tableExists(jdbc, "exam_monitor_action");
        boolean hasAnswerDraft = tableExists(jdbc, "exam_answer_draft");
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_keep");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_dedup");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_exam_attempt_keep AS
                            SELECT a.exam_id,
                                   a.user_id,
                                   a.attempt_no,
                                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(a.id ORDER BY a.status DESC,
                                           CASE WHEN a.score IS NULL THEN 0 ELSE 1 END DESC,
                                           a.submit_time DESC,
                                           a.updated_at DESC,
                                           a.id DESC), ',', 1) AS UNSIGNED) AS keep_id
                            FROM exam_attempt a
                            JOIN (
                              SELECT exam_id, user_id, attempt_no
                              FROM exam_attempt
                              GROUP BY exam_id, user_id, attempt_no
                              HAVING COUNT(*) > 1
                            ) duplicate_identity
                              ON duplicate_identity.exam_id = a.exam_id
                             AND duplicate_identity.user_id = a.user_id
                             AND duplicate_identity.attempt_no = a.attempt_no
                            GROUP BY a.exam_id, a.user_id, a.attempt_no
                            """);
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_exam_attempt_dedup AS
                            SELECT a.id AS duplicate_id, k.keep_id
                            FROM exam_attempt a
                            JOIN tmp_exam_attempt_keep k
                              ON k.exam_id = a.exam_id
                             AND k.user_id = a.user_id
                             AND k.attempt_no = a.attempt_no
                            WHERE a.id <> k.keep_id
                            """);

                    if (hasAnswerRecord) {
                        statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_answer_record_dedup");
                        statement.execute("""
                                CREATE TEMPORARY TABLE tmp_exam_attempt_answer_record_dedup AS
                                SELECT ar.id AS duplicate_id, keep_ar.id AS keep_id
                                FROM answer_record ar
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = ar.attempt_id
                                JOIN answer_record keep_ar
                                  ON keep_ar.attempt_id = d.keep_id
                                 AND keep_ar.question_id = ar.question_id
                                """);
                        if (hasReviewRecord) {
                            statement.execute("""
                                    UPDATE review_record rr
                                    JOIN tmp_exam_attempt_answer_record_dedup d ON d.duplicate_id = rr.answer_record_id
                                    SET rr.answer_record_id = d.keep_id
                                    """);
                        }
                        if (hasReviewScoreLog) {
                            statement.execute("""
                                    UPDATE review_score_log rsl
                                    JOIN tmp_exam_attempt_answer_record_dedup d ON d.duplicate_id = rsl.answer_record_id
                                    SET rsl.answer_record_id = d.keep_id
                                    """);
                        }
                        statement.execute("""
                                DELETE ar
                                FROM answer_record ar
                                JOIN tmp_exam_attempt_answer_record_dedup d ON d.duplicate_id = ar.id
                                """);
                        statement.execute("""
                                UPDATE answer_record ar
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = ar.attempt_id
                                SET ar.attempt_id = d.keep_id
                                """);
                        statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_answer_record_dedup");
                    }

                    if (hasSubmitResponse) {
                        statement.execute("""
                                DELETE r
                                FROM exam_submit_response r
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = r.attempt_id
                                JOIN exam_submit_response keeper ON keeper.attempt_id = d.keep_id
                                """);
                        statement.execute("""
                                UPDATE exam_submit_response r
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = r.attempt_id
                                SET r.attempt_id = d.keep_id
                                """);
                    }
                    if (hasAnswerDraft) {
                        statement.execute("""
                                DELETE draft
                                FROM exam_answer_draft draft
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = draft.attempt_id
                                JOIN exam_answer_draft keeper ON keeper.attempt_id = d.keep_id
                                """);
                        statement.execute("""
                                UPDATE exam_answer_draft draft
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = draft.attempt_id
                                SET draft.attempt_id = d.keep_id
                                """);
                    }
                    if (hasMonitorSession) {
                        if (hasMonitorAction) {
                            statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_monitor_session_dedup");
                            statement.execute("""
                                    CREATE TEMPORARY TABLE tmp_exam_monitor_session_dedup AS
                                    SELECT s.id AS duplicate_session_id, keeper.id AS keep_session_id
                                    FROM exam_monitor_session s
                                    JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = s.attempt_id
                                    JOIN exam_monitor_session keeper ON keeper.attempt_id = d.keep_id
                                    """);
                            statement.execute("""
                                    UPDATE exam_monitor_action ma
                                    JOIN tmp_exam_monitor_session_dedup d ON d.duplicate_session_id = ma.session_id
                                    SET ma.session_id = d.keep_session_id
                                    """);
                            statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_monitor_session_dedup");
                        }
                        statement.execute("""
                                DELETE s
                                FROM exam_monitor_session s
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = s.attempt_id
                                JOIN exam_monitor_session keeper ON keeper.attempt_id = d.keep_id
                                """);
                        statement.execute("""
                                UPDATE exam_monitor_session s
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = s.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET s.attempt_id = d.keep_id,
                                    s.exam_id = keep_attempt.exam_id,
                                    s.user_id = keep_attempt.user_id
                                """);
                    }
                    if (hasMonitorAction) {
                        statement.execute("""
                                UPDATE exam_monitor_action ma
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = ma.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET ma.attempt_id = d.keep_id,
                                    ma.exam_id = keep_attempt.exam_id,
                                    ma.user_id = keep_attempt.user_id
                                """);
                    }
                    if (hasCheatEvent) {
                        statement.execute("""
                                DELETE ce
                                FROM cheat_event ce
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = ce.attempt_id
                                JOIN cheat_event keeper
                                  ON keeper.attempt_id = d.keep_id
                                 AND keeper.client_event_id = ce.client_event_id
                                WHERE ce.client_event_id IS NOT NULL
                                """);
                        statement.execute("""
                                UPDATE cheat_event ce
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = ce.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET ce.attempt_id = d.keep_id,
                                    ce.exam_id = keep_attempt.exam_id,
                                    ce.user_id = keep_attempt.user_id
                                """);
                    }
                    if (hasScoreAppeal) {
                        statement.execute("""
                                UPDATE score_appeal sa
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = sa.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET sa.attempt_id = d.keep_id,
                                    sa.exam_id = keep_attempt.exam_id,
                                    sa.user_id = keep_attempt.user_id
                                """);
                    }
                    if (hasScoreAppealLog) {
                        statement.execute("""
                                UPDATE score_appeal_log sal
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = sal.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET sal.attempt_id = d.keep_id,
                                    sal.exam_id = keep_attempt.exam_id,
                                    sal.user_id = keep_attempt.user_id
                                """);
                    }
                    if (hasReviewScoreLog) {
                        statement.execute("""
                                UPDATE review_score_log rsl
                                JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = rsl.attempt_id
                                JOIN exam_attempt keep_attempt ON keep_attempt.id = d.keep_id
                                SET rsl.attempt_id = d.keep_id,
                                    rsl.exam_id = keep_attempt.exam_id,
                                    rsl.user_id = keep_attempt.user_id
                                """);
                    }

                    statement.execute("""
                            DELETE a
                            FROM exam_attempt a
                            JOIN tmp_exam_attempt_dedup d ON d.duplicate_id = a.id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_dedup");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_exam_attempt_keep");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: exam_attempt duplicate cleanup failed before adding unique identity index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureSystemConfigTableClean(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create system_config table", """
                CREATE TABLE IF NOT EXISTS system_config (
                  id           BIGINT        NOT NULL AUTO_INCREMENT,
                  config_key   VARCHAR(128)  NOT NULL,
                  config_value VARCHAR(1000) NOT NULL,
                  value_type   VARCHAR(32)   NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN',
                  category     VARCHAR(64)   NOT NULL DEFAULT 'GENERAL',
                  description  VARCHAR(255)  DEFAULT NULL,
                  editable     TINYINT       NOT NULL DEFAULT 1,
                  updated_by   BIGINT        DEFAULT NULL,
                  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_system_config_key (config_key),
                  KEY idx_system_config_category (category)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置'
                """);
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
    private void ensureQuestionReviewColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "question", "review_status",
                "ALTER TABLE question ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' AFTER status");
        addColumnIfMissing(jdbc, "question", "reviewed_by",
                "ALTER TABLE question ADD COLUMN reviewed_by BIGINT DEFAULT NULL AFTER review_status");
        addColumnIfMissing(jdbc, "question", "reviewed_at",
                "ALTER TABLE question ADD COLUMN reviewed_at DATETIME DEFAULT NULL AFTER reviewed_by");
        addColumnIfMissing(jdbc, "question", "review_comment",
                "ALTER TABLE question ADD COLUMN review_comment VARCHAR(500) DEFAULT NULL AFTER reviewed_at");
        addIndexIfMissing(jdbc, "question", "idx_question_review_status",
                "ALTER TABLE question ADD INDEX idx_question_review_status (review_status, status, deleted)");
        executeQuietly(jdbc, "backfill question review status", """
                UPDATE question
                SET review_status = CASE WHEN status = 1 THEN 'APPROVED' ELSE 'DRAFT' END
                WHERE review_status IS NULL OR review_status = ''
                """);
    }

    private void ensureQuestionVersionTables(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "question", "version_no",
                "ALTER TABLE question ADD COLUMN version_no INT NOT NULL DEFAULT 1 AFTER status");
        addColumnIfMissing(jdbc, "paper_question", "question_version_id",
                "ALTER TABLE paper_question ADD COLUMN question_version_id BIGINT DEFAULT NULL AFTER question_id");
        addIndexIfMissing(jdbc, "paper_question", "idx_pq_question_version",
                "ALTER TABLE paper_question ADD INDEX idx_pq_question_version (question_version_id)");
        executeQuietly(jdbc, "create question_version table", """
                CREATE TABLE IF NOT EXISTS question_version (
                  id                 BIGINT        NOT NULL AUTO_INCREMENT,
                  question_id        BIGINT        NOT NULL,
                  version_no         INT           NOT NULL DEFAULT 1,
                  subject_id         BIGINT        NOT NULL,
                  knowledge_point_id BIGINT        DEFAULT NULL,
                  question_type      VARCHAR(32)   NOT NULL,
                  difficulty         VARCHAR(32)   NOT NULL,
                  stem               TEXT          NOT NULL,
                  correct_answer     TEXT          DEFAULT NULL,
                  analysis           TEXT          DEFAULT NULL,
                  default_score      DECIMAL(6,2)  NOT NULL DEFAULT 5.00,
                  status             TINYINT       NOT NULL DEFAULT 0,
                  review_status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
                  source_type        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
                  source_detail      VARCHAR(255)  DEFAULT NULL,
                  material_id        BIGINT        DEFAULT NULL,
                  source_page        INT           DEFAULT NULL,
                  source_paragraph   INT           DEFAULT NULL,
                  source_excerpt     VARCHAR(500)  DEFAULT NULL,
                  ai_model           VARCHAR(64)   DEFAULT NULL,
                  prompt_version     VARCHAR(64)   DEFAULT NULL,
                  snapshot_reason    VARCHAR(32)   NOT NULL DEFAULT 'EDIT',
                  snapshot_by        BIGINT        DEFAULT NULL,
                  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_question_version (question_id, version_no),
                  KEY idx_question_version_question (question_id, id),
                  KEY idx_question_version_review (review_status, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        executeQuietly(jdbc, "create question_version_option table", """
                CREATE TABLE IF NOT EXISTS question_version_option (
                  id                  BIGINT        NOT NULL AUTO_INCREMENT,
                  question_version_id BIGINT        NOT NULL,
                  question_id         BIGINT        NOT NULL,
                  option_label        VARCHAR(16)   NOT NULL,
                  option_content      VARCHAR(1000) NOT NULL,
                  is_correct          TINYINT       NOT NULL DEFAULT 0,
                  sort_order          INT           NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_qvo_version_label (question_version_id, option_label),
                  KEY idx_qvo_version (question_version_id),
                  KEY idx_qvo_question (question_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addIndexIfMissing(jdbc, "question_version_option", "uk_qvo_version_label",
                "ALTER TABLE question_version_option ADD UNIQUE KEY uk_qvo_version_label (question_version_id, option_label)");
        executeQuietly(jdbc, "create question_review_log table", """
                CREATE TABLE IF NOT EXISTS question_review_log (
                  id                 BIGINT       NOT NULL AUTO_INCREMENT,
                  question_id        BIGINT       NOT NULL,
                  version_no         INT          NOT NULL DEFAULT 1,
                  action_type        VARCHAR(32)  NOT NULL,
                  from_status        TINYINT      DEFAULT NULL,
                  to_status          TINYINT      DEFAULT NULL,
                  from_review_status VARCHAR(32)  DEFAULT NULL,
                  to_review_status   VARCHAR(32)  DEFAULT NULL,
                  comment            VARCHAR(500) DEFAULT NULL,
                  operated_by        BIGINT       DEFAULT NULL,
                  operated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_question_review_log_question (question_id, operated_at),
                  KEY idx_question_review_log_action (action_type, operated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        executeQuietly(jdbc, "backfill question versions", """
                INSERT IGNORE INTO question_version (
                    question_id, version_no, subject_id, knowledge_point_id, question_type, difficulty,
                    stem, correct_answer, analysis, default_score, status, review_status, source_type,
                    source_detail, material_id, source_page, source_paragraph, source_excerpt, ai_model,
                    prompt_version, snapshot_reason, snapshot_by, created_at
                )
                SELECT id, version_no, subject_id, knowledge_point_id, question_type, difficulty,
                       stem, correct_answer, analysis, default_score, status, review_status, source_type,
                       source_detail, material_id, source_page, source_paragraph, source_excerpt, ai_model,
                       prompt_version, 'MIGRATION', created_by, created_at
                FROM question
                WHERE deleted = 0
                """);
        executeQuietly(jdbc, "backfill question version options", """
                INSERT IGNORE INTO question_version_option (
                    question_version_id, question_id, option_label, option_content, is_correct, sort_order
                )
                SELECT qv.id, qo.question_id, qo.option_label, qo.option_content, qo.is_correct, qo.sort_order
                FROM question_option qo
                JOIN question_version qv ON qv.question_id = qo.question_id AND qv.version_no = 1
                """);
        executeQuietly(jdbc, "backfill paper question version ids", """
                UPDATE paper_question pq
                JOIN question q ON q.id = pq.question_id
                JOIN question_version qv ON qv.question_id = q.id AND qv.version_no = q.version_no
                SET pq.question_version_id = qv.id
                WHERE pq.question_version_id IS NULL
                """);
    }

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
                  UNION ALL SELECT 'ADMIN', '/exam-approvals', 1
                  UNION ALL SELECT 'ADMIN', '/question-bank', 2
                  UNION ALL SELECT 'ADMIN', '/materials', 3
                  UNION ALL SELECT 'ADMIN', '/papers', 4
                  UNION ALL SELECT 'ADMIN', '/exam/analysis', 5
                  UNION ALL SELECT 'ADMIN', '/exam-monitor', 6
                  UNION ALL SELECT 'ADMIN', '/basic/data', 7
                  UNION ALL SELECT 'ADMIN', '/system/users', 8
                  UNION ALL SELECT 'ADMIN', '/system/roles', 9
                  UNION ALL SELECT 'ADMIN', '/system/config', 10
                  UNION ALL SELECT 'ADMIN', '/monitor/logs', 11
                  UNION ALL SELECT 'TEACHER', '/teacher', 0
                  UNION ALL SELECT 'TEACHER', '/exam-tasks', 1
                  UNION ALL SELECT 'TEACHER', '/exam-monitor', 2
                  UNION ALL SELECT 'TEACHER', '/reviews', 3
                  UNION ALL SELECT 'TEACHER', '/teacher/analysis', 4
                  UNION ALL SELECT 'TEACHER', '/teacher/students', 5
                  UNION ALL SELECT 'TEACHER', '/question-bank', 6
                  UNION ALL SELECT 'TEACHER', '/materials', 7
                  UNION ALL SELECT 'TEACHER', '/papers', 8
                  UNION ALL SELECT 'TEACHER', '/basic/data', 9
                  UNION ALL SELECT 'STUDENT', '/student', 0
                  UNION ALL SELECT 'STUDENT', '/student/exams', 1
                  UNION ALL SELECT 'STUDENT', '/student/results', 2
                  UNION ALL SELECT 'STUDENT', '/student/wrong-questions', 3
                  UNION ALL SELECT 'STUDENT', '/basic/data', 4
                ) seed
                """);
    }

    private void backfillExamSnapshots(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "backfill exam candidate snapshots", """
                INSERT IGNORE INTO exam_candidate_snapshot
                    (exam_id, user_id, source_type, source_id, real_name, student_no, class_name)
                SELECT DISTINCT ea.exam_id, ea.user_id, 'LEGACY', NULL,
                       u.real_name, sp.student_no, c.class_name
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN sys_user u ON u.id = ea.user_id
                LEFT JOIN student_profile sp ON sp.user_id = ea.user_id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE e.deleted = 0
                """);
        executeQuietly(jdbc, "backfill exam question snapshots", """
                INSERT IGNORE INTO exam_question_snapshot
                    (exam_id, paper_id, question_id, knowledge_point_id, question_type, stem, correct_answer, analysis, score, sort_order)
                SELECT e.id, e.paper_id, q.id, q.knowledge_point_id, q.question_type, q.stem, q.correct_answer,
                       q.analysis, pq.score, pq.sort_order
                FROM exam e
                JOIN paper_question pq ON pq.paper_id = e.paper_id
                JOIN question q ON q.id = pq.question_id
                WHERE e.deleted = 0
                """);
        executeQuietly(jdbc, "backfill exam question snapshot knowledge points", """
                UPDATE exam_question_snapshot eqs
                JOIN question q ON q.id = eqs.question_id
                SET eqs.knowledge_point_id = q.knowledge_point_id
                WHERE eqs.knowledge_point_id IS NULL
                  AND q.knowledge_point_id IS NOT NULL
                """);
        executeQuietly(jdbc, "backfill exam option snapshots", """
                INSERT IGNORE INTO exam_question_option_snapshot
                    (exam_id, question_id, option_label, option_content, sort_order)
                SELECT e.id, qo.question_id, qo.option_label, qo.option_content, qo.sort_order
                FROM exam e
                JOIN paper_question pq ON pq.paper_id = e.paper_id
                JOIN question_option qo ON qo.question_id = pq.question_id
                WHERE e.deleted = 0
                """);
    }

    private void ensureMonitorSessionTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_monitor_session table", """
                CREATE TABLE IF NOT EXISTS exam_monitor_session (
                  id                BIGINT      NOT NULL AUTO_INCREMENT,
                  attempt_id        BIGINT      NOT NULL,
                  exam_id           BIGINT      NOT NULL,
                  user_id           BIGINT      NOT NULL,
                  status            VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
                  last_heartbeat_at DATETIME    DEFAULT NULL,
                  last_event_at     DATETIME    DEFAULT NULL,
                  event_count       INT         NOT NULL DEFAULT 0,
                  risk_score        INT         NOT NULL DEFAULT 0,
                  last_event_type   VARCHAR(64) DEFAULT NULL,
                  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_monitor_attempt (attempt_id),
                  KEY idx_monitor_exam_status (exam_id, status, last_heartbeat_at),
                  KEY idx_monitor_user (user_id),
                  KEY idx_monitor_risk (exam_id, risk_score)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing(jdbc, "exam_monitor_session", "status",
                "ALTER TABLE exam_monitor_session ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ONLINE' AFTER user_id");
        addColumnIfMissing(jdbc, "exam_monitor_session", "last_heartbeat_at",
                "ALTER TABLE exam_monitor_session ADD COLUMN last_heartbeat_at DATETIME DEFAULT NULL AFTER status");
        addColumnIfMissing(jdbc, "exam_monitor_session", "last_event_at",
                "ALTER TABLE exam_monitor_session ADD COLUMN last_event_at DATETIME DEFAULT NULL AFTER last_heartbeat_at");
        addColumnIfMissing(jdbc, "exam_monitor_session", "event_count",
                "ALTER TABLE exam_monitor_session ADD COLUMN event_count INT NOT NULL DEFAULT 0 AFTER last_event_at");
        addColumnIfMissing(jdbc, "exam_monitor_session", "risk_score",
                "ALTER TABLE exam_monitor_session ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER event_count");
        addColumnIfMissing(jdbc, "exam_monitor_session", "last_event_type",
                "ALTER TABLE exam_monitor_session ADD COLUMN last_event_type VARCHAR(64) DEFAULT NULL AFTER risk_score");
        addColumnIfMissing(jdbc, "exam_monitor_session", "created_at",
                "ALTER TABLE exam_monitor_session ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER last_event_type");
        addColumnIfMissing(jdbc, "exam_monitor_session", "updated_at",
                "ALTER TABLE exam_monitor_session ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
        addIndexIfMissing(jdbc, "exam_monitor_session", "idx_monitor_exam_status",
                "ALTER TABLE exam_monitor_session ADD INDEX idx_monitor_exam_status (exam_id, status, last_heartbeat_at)");
        addIndexIfMissing(jdbc, "exam_monitor_session", "idx_monitor_user",
                "ALTER TABLE exam_monitor_session ADD INDEX idx_monitor_user (user_id)");
        addIndexIfMissing(jdbc, "exam_monitor_session", "idx_monitor_risk",
                "ALTER TABLE exam_monitor_session ADD INDEX idx_monitor_risk (exam_id, risk_score)");
    }

    private void ensureMonitorSessionUniqueIdentity(JdbcTemplate jdbc) {
        deduplicateMonitorSessionsBeforeUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "exam_monitor_session", "uk_monitor_attempt",
                "ALTER TABLE exam_monitor_session ADD UNIQUE KEY uk_monitor_attempt (attempt_id)");
    }

    private void deduplicateMonitorSessionsBeforeUniqueIndex(JdbcTemplate jdbc) {
        boolean hasMonitorAction = tableExists(jdbc, "exam_monitor_action");
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_monitor_session_merge");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_monitor_session_dedup");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_monitor_session_merge AS
                            SELECT s.attempt_id,
                                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(s.id ORDER BY
                                           CASE WHEN s.status = 'SUBMITTED' THEN 3
                                                WHEN s.status = 'ONLINE' THEN 2
                                                ELSE 1 END DESC,
                                           s.last_heartbeat_at DESC,
                                           s.event_count DESC,
                                           s.risk_score DESC,
                                           s.updated_at DESC,
                                           s.id DESC), ',', 1) AS UNSIGNED) AS keep_id,
                                   MAX(CASE WHEN s.status = 'SUBMITTED' THEN 1 ELSE 0 END) AS has_submitted,
                                   MAX(CASE WHEN s.status = 'ONLINE' THEN 1 ELSE 0 END) AS has_online,
                                   MAX(s.last_heartbeat_at) AS merged_last_heartbeat_at,
                                   MAX(s.last_event_at) AS merged_last_event_at,
                                   MAX(s.event_count) AS merged_event_count,
                                   MAX(s.risk_score) AS merged_risk_score,
                                   MIN(s.created_at) AS merged_created_at
                            FROM exam_monitor_session s
                            GROUP BY s.attempt_id
                            HAVING COUNT(*) > 1
                            """);
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_monitor_session_dedup AS
                            SELECT s.id AS duplicate_session_id, m.keep_id
                            FROM exam_monitor_session s
                            JOIN tmp_monitor_session_merge m ON m.attempt_id = s.attempt_id
                            WHERE s.id <> m.keep_id
                            """);
                    if (hasMonitorAction) {
                        statement.execute("""
                                UPDATE exam_monitor_action ma
                                JOIN tmp_monitor_session_dedup d ON d.duplicate_session_id = ma.session_id
                                SET ma.session_id = d.keep_id
                                """);
                    }
                    statement.execute("""
                            UPDATE exam_monitor_session s
                            JOIN tmp_monitor_session_merge m ON m.keep_id = s.id
                            SET s.status = CASE
                                    WHEN m.has_submitted = 1 THEN 'SUBMITTED'
                                    WHEN m.has_online = 1 THEN 'ONLINE'
                                    ELSE s.status
                                END,
                                s.last_heartbeat_at = COALESCE(m.merged_last_heartbeat_at, s.last_heartbeat_at),
                                s.last_event_at = COALESCE(m.merged_last_event_at, s.last_event_at),
                                s.event_count = GREATEST(s.event_count, m.merged_event_count),
                                s.risk_score = GREATEST(s.risk_score, m.merged_risk_score),
                                s.created_at = LEAST(s.created_at, m.merged_created_at)
                            """);
                    statement.execute("""
                            DELETE s
                            FROM exam_monitor_session s
                            JOIN tmp_monitor_session_dedup d ON d.duplicate_session_id = s.id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_monitor_session_dedup");
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_monitor_session_merge");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: exam_monitor_session duplicate cleanup failed before adding unique attempt index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureMonitorSessionOwnershipConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile monitor session ownership", """
                UPDATE exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                SET s.exam_id = a.exam_id,
                    s.user_id = a.user_id
                WHERE s.exam_id <> a.exam_id
                   OR s.user_id <> a.user_id
                """);
    }

    private void ensureMonitorSessionEventAggregateConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create monitor sessions from cheat events", """
                INSERT INTO exam_monitor_session (
                    attempt_id, exam_id, user_id, status, last_heartbeat_at,
                    last_event_at, event_count, risk_score, last_event_type
                )
                SELECT agg.attempt_id,
                       agg.exam_id,
                       agg.user_id,
                       CASE WHEN a.status >= 2 THEN 'SUBMITTED' ELSE 'OFFLINE' END,
                       NULL,
                       agg.last_event_at,
                       agg.event_count,
                       agg.risk_score,
                       latest.event_type
                FROM (
                    SELECT ce.attempt_id,
                           a.exam_id,
                           a.user_id,
                           COUNT(*) AS event_count,
                           COALESCE(SUM(ce.risk_score), 0) AS risk_score,
                           MAX(ce.event_time) AS last_event_at
                    FROM cheat_event ce
                    JOIN exam_attempt a ON a.id = ce.attempt_id
                    GROUP BY ce.attempt_id, a.exam_id, a.user_id
                ) agg
                JOIN exam_attempt a ON a.id = agg.attempt_id
                JOIN cheat_event latest ON latest.id = (
                    SELECT ce2.id
                    FROM cheat_event ce2
                    WHERE ce2.attempt_id = agg.attempt_id
                    ORDER BY ce2.event_time DESC, ce2.id DESC
                    LIMIT 1
                )
                LEFT JOIN exam_monitor_session s ON s.attempt_id = agg.attempt_id
                WHERE s.id IS NULL
                """);
        executeQuietly(jdbc, "reconcile monitor session event aggregates", """
                UPDATE exam_monitor_session s
                JOIN (
                    SELECT ce.attempt_id,
                           COUNT(*) AS event_count,
                           COALESCE(SUM(ce.risk_score), 0) AS risk_score,
                           MAX(ce.event_time) AS last_event_at
                    FROM cheat_event ce
                    GROUP BY ce.attempt_id
                ) agg ON agg.attempt_id = s.attempt_id
                JOIN cheat_event latest ON latest.id = (
                    SELECT ce2.id
                    FROM cheat_event ce2
                    WHERE ce2.attempt_id = agg.attempt_id
                    ORDER BY ce2.event_time DESC, ce2.id DESC
                    LIMIT 1
                )
                SET s.event_count = agg.event_count,
                    s.risk_score = agg.risk_score,
                    s.last_event_at = agg.last_event_at,
                    s.last_event_type = latest.event_type
                WHERE s.event_count <> agg.event_count
                   OR s.risk_score <> agg.risk_score
                   OR s.last_event_at IS NULL
                   OR s.last_event_at <> agg.last_event_at
                   OR COALESCE(s.last_event_type, '') <> COALESCE(latest.event_type, '')
                """);
    }

    private void ensureMonitorSessionSubmittedStateConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile monitor session submitted state", """
                UPDATE exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                SET s.status = 'SUBMITTED'
                WHERE a.status >= 2
                  AND s.status <> 'SUBMITTED'
                """);
    }

    private void ensureCheatEventBatchColumns(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "cheat_event", "client_event_id",
                "ALTER TABLE cheat_event ADD COLUMN client_event_id VARCHAR(80) DEFAULT NULL AFTER extra_info");
        addColumnIfMissing(jdbc, "cheat_event", "client_event_time",
                "ALTER TABLE cheat_event ADD COLUMN client_event_time DATETIME DEFAULT NULL AFTER client_event_id");
        deduplicateCheatEventsBeforeClientEventUniqueIndex(jdbc);
        addIndexIfMissing(jdbc, "cheat_event", "uk_cheat_attempt_client_event",
                "ALTER TABLE cheat_event ADD UNIQUE KEY uk_cheat_attempt_client_event (attempt_id, client_event_id)");
        addIndexIfMissing(jdbc, "cheat_event", "idx_cheat_attempt_client_time",
                "ALTER TABLE cheat_event ADD INDEX idx_cheat_attempt_client_time (attempt_id, client_event_time)");
    }

    private void deduplicateCheatEventsBeforeClientEventUniqueIndex(JdbcTemplate jdbc) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_cheat_event_client_dedup");
                    statement.execute("""
                            CREATE TEMPORARY TABLE tmp_cheat_event_client_dedup AS
                            SELECT ce.id AS duplicate_id
                            FROM cheat_event ce
                            JOIN (
                              SELECT attempt_id,
                                     client_event_id,
                                     CAST(SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY
                                             CASE WHEN client_event_time IS NULL THEN 1 ELSE 0 END ASC,
                                             client_event_time ASC,
                                             event_time ASC,
                                             id ASC), ',', 1) AS UNSIGNED) AS keep_id
                              FROM cheat_event
                              WHERE client_event_id IS NOT NULL
                              GROUP BY attempt_id, client_event_id
                              HAVING COUNT(*) > 1
                            ) keepers
                              ON keepers.attempt_id = ce.attempt_id
                             AND keepers.client_event_id = ce.client_event_id
                            WHERE ce.id <> keepers.keep_id
                            """);
                    statement.execute("""
                            DELETE ce
                            FROM cheat_event ce
                            JOIN tmp_cheat_event_client_dedup d ON d.duplicate_id = ce.id
                            """);
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_cheat_event_client_dedup");
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("Database migration: cheat_event client event duplicate cleanup failed before adding unique index (startup continues), reason: {}",
                    ex.getMessage());
        }
    }

    private void ensureCheatEventOwnershipConsistency(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "cheat_event", "exam_id",
                "ALTER TABLE cheat_event ADD COLUMN exam_id BIGINT DEFAULT NULL AFTER attempt_id");
        addColumnIfMissing(jdbc, "cheat_event", "user_id",
                "ALTER TABLE cheat_event ADD COLUMN user_id BIGINT DEFAULT NULL AFTER exam_id");
        executeQuietly(jdbc, "reconcile cheat event ownership", """
                UPDATE cheat_event ce
                JOIN exam_attempt a ON a.id = ce.attempt_id
                SET ce.exam_id = a.exam_id,
                    ce.user_id = a.user_id
                WHERE ce.exam_id IS NULL
                   OR ce.user_id IS NULL
                   OR ce.exam_id <> a.exam_id
                   OR ce.user_id <> a.user_id
                """);
        addIndexIfMissing(jdbc, "cheat_event", "idx_cheat_exam_time",
                "ALTER TABLE cheat_event ADD INDEX idx_cheat_exam_time (exam_id, event_time)");
        addIndexIfMissing(jdbc, "cheat_event", "idx_cheat_user_time",
                "ALTER TABLE cheat_event ADD INDEX idx_cheat_user_time (user_id, event_time)");
    }

    private void ensureCheatEventRiskScoreConsistency(JdbcTemplate jdbc) {
        addColumnIfMissing(jdbc, "cheat_event", "risk_score",
                "ALTER TABLE cheat_event ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER event_type");
        executeQuietly(jdbc, "reconcile cheat event risk score", """
                UPDATE cheat_event ce
                SET ce.risk_score = CASE ce.event_type
                    WHEN 'PASTE' THEN 8
                    WHEN 'COPY' THEN 6
                    WHEN 'FULLSCREEN_EXIT' THEN 5
                    WHEN 'PAGE_UNLOAD_ATTEMPT' THEN 5
                    WHEN 'NETWORK_OFFLINE' THEN 4
                    WHEN 'HISTORY_BACK_ATTEMPT' THEN 4
                    WHEN 'VISIBILITY_HIDDEN' THEN 3
                    WHEN 'WINDOW_BLUR' THEN 3
                    WHEN 'CONTEXT_MENU' THEN 3
                    WHEN 'HEARTBEAT_FAILED' THEN 2
                    WHEN 'NETWORK_ONLINE' THEN 1
                    ELSE 0
                END
                WHERE ce.risk_score <> CASE ce.event_type
                    WHEN 'PASTE' THEN 8
                    WHEN 'COPY' THEN 6
                    WHEN 'FULLSCREEN_EXIT' THEN 5
                    WHEN 'PAGE_UNLOAD_ATTEMPT' THEN 5
                    WHEN 'NETWORK_OFFLINE' THEN 4
                    WHEN 'HISTORY_BACK_ATTEMPT' THEN 4
                    WHEN 'VISIBILITY_HIDDEN' THEN 3
                    WHEN 'WINDOW_BLUR' THEN 3
                    WHEN 'CONTEXT_MENU' THEN 3
                    WHEN 'HEARTBEAT_FAILED' THEN 2
                    WHEN 'NETWORK_ONLINE' THEN 1
                    ELSE 0
                END
                """);
        addIndexIfMissing(jdbc, "cheat_event", "idx_cheat_exam_risk_time",
                "ALTER TABLE cheat_event ADD INDEX idx_cheat_exam_risk_time (exam_id, risk_score, event_time)");
    }

    private void ensureMonitorActionTable(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "create exam_monitor_action table", """
                CREATE TABLE IF NOT EXISTS exam_monitor_action (
                  id          BIGINT      NOT NULL AUTO_INCREMENT,
                  session_id  BIGINT      NOT NULL,
                  attempt_id  BIGINT      NOT NULL,
                  exam_id     BIGINT      NOT NULL,
                  user_id     BIGINT      NOT NULL,
                  action_type VARCHAR(32) NOT NULL,
                  note        VARCHAR(1000) DEFAULT NULL,
                  notification_sent TINYINT NOT NULL DEFAULT 0,
                  notification_id BIGINT DEFAULT NULL,
                  handled_by  BIGINT      NOT NULL,
                  handled_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_monitor_action_session (session_id, handled_at),
                  KEY idx_monitor_action_exam (exam_id, handled_at),
                  KEY idx_monitor_action_attempt (attempt_id),
                  KEY idx_monitor_action_notification (notification_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing(jdbc, "exam_monitor_action", "notification_sent",
                "ALTER TABLE exam_monitor_action ADD COLUMN notification_sent TINYINT NOT NULL DEFAULT 0 AFTER note");
        addColumnIfMissing(jdbc, "exam_monitor_action", "notification_id",
                "ALTER TABLE exam_monitor_action ADD COLUMN notification_id BIGINT DEFAULT NULL AFTER notification_sent");
        addIndexIfMissing(jdbc, "exam_monitor_action", "idx_monitor_action_notification",
                "ALTER TABLE exam_monitor_action ADD INDEX idx_monitor_action_notification (notification_id)");
    }

    private void ensureMonitorActionOwnershipConsistency(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "reconcile monitor action ownership", """
                UPDATE exam_monitor_action ma
                JOIN exam_monitor_session s ON s.id = ma.session_id
                JOIN exam_attempt a ON a.id = s.attempt_id
                SET ma.attempt_id = s.attempt_id,
                    ma.exam_id = a.exam_id,
                    ma.user_id = a.user_id
                WHERE ma.attempt_id <> s.attempt_id
                   OR ma.exam_id <> a.exam_id
                   OR ma.user_id <> a.user_id
                """);
    }

    private void backfillDefaultSystemConfigsClean(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "backfill system configs", """
                INSERT IGNORE INTO system_config (config_key, config_value, value_type, category, description, editable)
                SELECT config_key, config_value, value_type, category, description, editable
                FROM (
                  SELECT 'exam.defaultDurationMinutes' config_key, '60' config_value, 'NUMBER' value_type, 'EXAM' category,
                         '发布考试时的默认考试时长（分钟）' description, 1 editable
                  UNION ALL SELECT 'exam.maxAttemptsLimit', '20', 'NUMBER', 'EXAM', '单场考试允许配置的最大作答次数上限', 1
                  UNION ALL SELECT 'exam.draftSaveIntervalSeconds', '30', 'NUMBER', 'EXAM', '学生端自动保存草稿建议间隔（秒）', 1
                  UNION ALL SELECT 'exam.draftRedisWriteBackEnabled', 'false', 'BOOLEAN', 'EXAM', '是否启用 Redis 草稿写回模式（关闭时为写穿 DB）', 1
                  UNION ALL SELECT 'exam.draftRedisFlushBatchSize', '200', 'NUMBER', 'EXAM', 'Redis 草稿刷盘任务每批处理数量', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyWarningThreshold', '100', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyHighThreshold', '500', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数高风险阈值', 1
                  UNION ALL SELECT 'exam.draftCacheErrorWarningThreshold', '5', 'NUMBER', 'EXAM', 'Redis 草稿缓存错误计数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheStaleFlushWarningSeconds', '300', 'NUMBER', 'EXAM', 'Redis 草稿存在 dirty 时最近刷盘陈旧阈值（秒）', 1
                  UNION ALL SELECT 'monitor.eventReportIntervalSeconds', '10', 'NUMBER', 'MONITOR', '学生端监考事件批量上报建议间隔（秒）', 1
                  UNION ALL SELECT 'monitor.riskWarningThreshold', '8', 'NUMBER', 'MONITOR', '监考风险提示阈值', 1
                  UNION ALL SELECT 'monitor.riskHighThreshold', '20', 'NUMBER', 'MONITOR', '监考高风险阈值', 1
                  UNION ALL SELECT 'score.appealEnabled', 'true', 'BOOLEAN', 'SCORE', '是否允许学生对已发布成绩提交申诉', 1
                  UNION ALL SELECT 'score.appealWindowDays', '7', 'NUMBER', 'SCORE', '成绩发布后允许申诉的天数', 1
                  UNION ALL SELECT 'approval.slaOverdueHours', '24', 'NUMBER', 'APPROVAL', '考试发布审批超时阈值（小时）', 1
                  UNION ALL SELECT 'approval.reminderEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否允许发送考试审批超时提醒', 1
                  UNION ALL SELECT 'approval.reminderCooldownHours', '6', 'NUMBER', 'APPROVAL', '审批超时提醒冷却间隔（小时）', 1
                  UNION ALL SELECT 'approval.reminderScheduleEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否启用审批超时自动提醒任务', 1
                  UNION ALL SELECT 'approval.reminderScheduleIntervalMinutes', '60', 'NUMBER', 'APPROVAL', '审批超时自动提醒任务最小执行间隔（分钟）', 1
                  UNION ALL SELECT 'system.maintenanceMode', 'false', 'BOOLEAN', 'SYSTEM', '系统维护模式开关（预留）', 1
                  UNION ALL SELECT 'system.testFixtureEnabled', 'false', 'BOOLEAN', 'SYSTEM', '是否启用测试夹具造数接口（仅测试环境打开）', 1
                ) seed
                """);
    }

    private void backfillDefaultSystemConfigs(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "backfill system configs", """
                INSERT IGNORE INTO system_config (config_key, config_value, value_type, category, description, editable)
                SELECT config_key, config_value, value_type, category, description, editable
                FROM (
                  SELECT 'exam.defaultDurationMinutes' config_key, '60' config_value, 'NUMBER' value_type, 'EXAM' category,
                         '发布考试时的默认考试时长（分钟）' description, 1 editable
                  UNION ALL SELECT 'exam.maxAttemptsLimit', '20', 'NUMBER', 'EXAM', '单场考试允许配置的最大作答次数上限', 1
                  UNION ALL SELECT 'exam.draftSaveIntervalSeconds', '30', 'NUMBER', 'EXAM', '学生端自动保存草稿建议间隔（秒）', 1
                  UNION ALL SELECT 'exam.draftRedisWriteBackEnabled', 'false', 'BOOLEAN', 'EXAM', '是否启用 Redis 草稿写回模式（关闭时为写穿 DB）', 1
                  UNION ALL SELECT 'exam.draftRedisFlushBatchSize', '200', 'NUMBER', 'EXAM', 'Redis 草稿刷盘任务每批处理数量', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyWarningThreshold', '100', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyHighThreshold', '500', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数高风险阈值', 1
                  UNION ALL SELECT 'exam.draftCacheErrorWarningThreshold', '5', 'NUMBER', 'EXAM', 'Redis 草稿缓存错误计数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheStaleFlushWarningSeconds', '300', 'NUMBER', 'EXAM', 'Redis 草稿存在 dirty 时最近刷盘陈旧阈值（秒）', 1
                  UNION ALL SELECT 'monitor.eventReportIntervalSeconds', '10', 'NUMBER', 'MONITOR', '学生端监考事件批量上报建议间隔（秒）', 1
                  UNION ALL SELECT 'monitor.riskWarningThreshold', '8', 'NUMBER', 'MONITOR', '监考风险提示阈值', 1
                  UNION ALL SELECT 'monitor.riskHighThreshold', '20', 'NUMBER', 'MONITOR', '监考高风险阈值', 1
                  UNION ALL SELECT 'score.appealEnabled', 'true', 'BOOLEAN', 'SCORE', '是否允许学生对已发布成绩提交申诉', 1
                  UNION ALL SELECT 'score.appealWindowDays', '7', 'NUMBER', 'SCORE', '成绩发布后允许申诉的天数', 1
                  UNION ALL SELECT 'approval.slaOverdueHours', '24', 'NUMBER', 'APPROVAL', '考试发布审批超时阈值（小时）', 1
                  UNION ALL SELECT 'approval.reminderEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否允许发送考试审批超时提醒', 1
                  UNION ALL SELECT 'approval.reminderCooldownHours', '6', 'NUMBER', 'APPROVAL', '审批超时提醒冷却间隔（小时）', 1
                  UNION ALL SELECT 'approval.reminderScheduleEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否启用审批超时自动提醒任务', 1
                  UNION ALL SELECT 'approval.reminderScheduleIntervalMinutes', '60', 'NUMBER', 'APPROVAL', '审批超时自动提醒任务最小执行间隔（分钟）', 1
                  UNION ALL SELECT 'system.maintenanceMode', 'false', 'BOOLEAN', 'SYSTEM', '系统维护模式开关（预留）', 1
                  UNION ALL SELECT 'system.testFixtureEnabled', 'false', 'BOOLEAN', 'SYSTEM', '是否启用测试夹具造数接口（仅测试环境打开）', 1
                ) seed
                """);
    }

    private void backfillSystemConfigs(JdbcTemplate jdbc) {
        executeQuietly(jdbc, "backfill system configs", """
                INSERT IGNORE INTO system_config (config_key, config_value, value_type, category, description, editable)
                SELECT config_key, config_value, value_type, category, description, editable
                FROM (
                  SELECT 'exam.defaultDurationMinutes' config_key, '60' config_value, 'NUMBER' value_type, 'EXAM' category,
                         '发布考试时的默认考试时长（分钟）' description, 1 editable
                  UNION ALL SELECT 'exam.maxAttemptsLimit', '20', 'NUMBER', 'EXAM', '单场考试允许配置的最大作答次数上限', 1
                  UNION ALL SELECT 'exam.draftSaveIntervalSeconds', '30', 'NUMBER', 'EXAM', '学生端自动保存草稿建议间隔（秒）', 1
                  UNION ALL SELECT 'exam.draftRedisWriteBackEnabled', 'false', 'BOOLEAN', 'EXAM', '是否启用 Redis 草稿写回模式（关闭时为写穿 DB）', 1
                  UNION ALL SELECT 'exam.draftRedisFlushBatchSize', '200', 'NUMBER', 'EXAM', 'Redis 草稿刷盘任务每批处理数量', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyWarningThreshold', '100', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheDirtyHighThreshold', '500', 'NUMBER', 'EXAM', 'Redis 草稿 dirty 数高风险阈值', 1
                  UNION ALL SELECT 'exam.draftCacheErrorWarningThreshold', '5', 'NUMBER', 'EXAM', 'Redis 草稿缓存错误计数预警阈值', 1
                  UNION ALL SELECT 'exam.draftCacheStaleFlushWarningSeconds', '300', 'NUMBER', 'EXAM', 'Redis 草稿存在 dirty 时最近刷盘陈旧阈值（秒）', 1
                  UNION ALL SELECT 'monitor.eventReportIntervalSeconds', '10', 'NUMBER', 'MONITOR', '学生端监考事件批量上报建议间隔（秒）', 1
                  UNION ALL SELECT 'monitor.riskWarningThreshold', '8', 'NUMBER', 'MONITOR', '监考风险提示阈值', 1
                  UNION ALL SELECT 'monitor.riskHighThreshold', '20', 'NUMBER', 'MONITOR', '监考高风险阈值', 1
                  UNION ALL SELECT 'score.appealEnabled', 'true', 'BOOLEAN', 'SCORE', '是否允许学生对已发布成绩提交申诉', 1
                  UNION ALL SELECT 'score.appealWindowDays', '7', 'NUMBER', 'SCORE', '成绩发布后允许申诉的天数', 1
                  UNION ALL SELECT 'approval.slaOverdueHours', '24', 'NUMBER', 'APPROVAL', '考试发布审批超时阈值（小时）', 1
                  UNION ALL SELECT 'approval.reminderEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否允许发送考试审批超时提醒', 1
                  UNION ALL SELECT 'approval.reminderCooldownHours', '6', 'NUMBER', 'APPROVAL', '审批超时提醒冷却间隔（小时）', 1
                  UNION ALL SELECT 'approval.reminderScheduleEnabled', 'true', 'BOOLEAN', 'APPROVAL', '是否启用审批超时自动提醒任务', 1
                  UNION ALL SELECT 'approval.reminderScheduleIntervalMinutes', '60', 'NUMBER', 'APPROVAL', '审批超时自动提醒任务最小执行间隔（分钟）', 1
                  UNION ALL SELECT 'system.maintenanceMode', 'false', 'BOOLEAN', 'SYSTEM', '系统维护模式开关（预留）', 1
                  UNION ALL SELECT 'system.testFixtureEnabled', 'false', 'BOOLEAN', 'SYSTEM', '是否启用测试夹具造数接口（仅测试环境打开）', 1
                ) seed
                """);
    }

    private boolean tableExists(JdbcTemplate jdbc, String tableName) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception ex) {
            log.error("Database migration: failed to check whether table {} exists (defaulting to false), reason: {}",
                    tableName, ex.getMessage());
            return false;
        }
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
