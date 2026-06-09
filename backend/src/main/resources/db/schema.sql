-- ============================================================
-- Smart Exam System - 数据库结构脚本 (MySQL 8.x)
-- ============================================================
-- 本脚本由后端各 Service 中实际使用的 SQL 反推整理而成。
-- 所有表均使用 InnoDB + utf8mb4，所有 CREATE 均为幂等（IF NOT EXISTS），
-- 配合 application.yml 的 spring.sql.init.mode=always 可在每次启动时安全执行。
-- 业务上的"删除"为逻辑删除（deleted 字段），关联表（选项/试卷题目）为物理删除。
-- 为降低初始化与逻辑删除带来的约束风险，此处不使用外键约束，仅建立索引。
-- ============================================================

-- ---------- 用户与角色 ----------

CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL COMMENT '登录用户名',
  password_hash VARCHAR(160) NOT NULL COMMENT '密码哈希，优先 pbkdf2$iterations$salt$hash；兼容历史 sha256$salt$hash',
  real_name     VARCHAR(64)  NOT NULL COMMENT '真实姓名',
  phone         VARCHAR(32)  DEFAULT NULL,
  email         VARCHAR(128) DEFAULT NULL,
  email_verified TINYINT     NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证 0未验证 1已验证',
  avatar        LONGTEXT     DEFAULT NULL COMMENT '头像 base64 dataURL（前端压缩后存储）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1已删除',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  role_code  VARCHAR(32) NOT NULL COMMENT '角色编码 ADMIN/TEACHER/STUDENT',
  role_name  VARCHAR(64) NOT NULL COMMENT '角色名称',
  status     TINYINT     NOT NULL DEFAULT 1,
  deleted    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  role_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user_role_user (user_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联';

-- ---------- 学生 / 教师档案 ----------

CREATE TABLE IF NOT EXISTS student_profile (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          BIGINT       NOT NULL,
  student_no       VARCHAR(64)  DEFAULT NULL COMMENT '学号',
  class_id         BIGINT       DEFAULT NULL COMMENT '兼容旧字段：原所属班级，语义等同主班级',
  primary_class_id BIGINT       DEFAULT NULL COMMENT '主专业班级 edu_class.id',
  enrollment_year  VARCHAR(32)  DEFAULT NULL COMMENT '入学年份/年级，如2023',
  college          VARCHAR(128) DEFAULT NULL COMMENT '学院',
  major            VARCHAR(128) DEFAULT NULL COMMENT '专业',
  status           TINYINT      NOT NULL DEFAULT 1,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_user (user_id),
  KEY idx_student_class (class_id),
  KEY idx_student_primary_class (primary_class_id),
  KEY idx_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生档案';

CREATE TABLE IF NOT EXISTS teacher_profile (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  teacher_no   VARCHAR(64)   DEFAULT NULL COMMENT '工号',
  hire_date    DATE          DEFAULT NULL COMMENT '入职时间',
  title        VARCHAR(64)   DEFAULT NULL COMMENT '职称',
  college      VARCHAR(128)  DEFAULT NULL COMMENT '学院/部门',
  introduction VARCHAR(1000) DEFAULT NULL COMMENT '简介',
  status       TINYINT       NOT NULL DEFAULT 1,
  deleted      TINYINT       NOT NULL DEFAULT 0,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_user (user_id),
  KEY idx_teacher_no (teacher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师档案';

-- ---------- 基础资料：班级 / 科目 / 知识点 / 公告 ----------

CREATE TABLE IF NOT EXISTS edu_class (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  class_name VARCHAR(128) NOT NULL COMMENT '班级名称',
  class_code VARCHAR(64)  DEFAULT NULL COMMENT '班级编码',
  class_type VARCHAR(32)  NOT NULL DEFAULT 'MAJOR' COMMENT '班级类型：MAJOR主专业班级/ELECTIVE选修临时班级/TEMPORARY临时班级',
  major      VARCHAR(128) DEFAULT NULL COMMENT '专业',
  grade      VARCHAR(32)  DEFAULT NULL COMMENT '年级',
  status     TINYINT      NOT NULL DEFAULT 1,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_class_name (class_name),
  UNIQUE KEY uk_class_code (class_code),
  KEY idx_class_type (class_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级';

CREATE TABLE IF NOT EXISTS edu_subject (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  subject_name VARCHAR(128) NOT NULL COMMENT '科目名称',
  description  VARCHAR(500) DEFAULT NULL,
  status       TINYINT      NOT NULL DEFAULT 1,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_name (subject_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科目';

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
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  teacher_user_id BIGINT     NOT NULL COMMENT '教师 sys_user.id',
  class_course_id BIGINT     NOT NULL COMMENT '班级课程 class_course.id',
  teacher_role   VARCHAR(32) NOT NULL DEFAULT 'LECTURER' COMMENT '授课角色：LECTURER主讲/ASSISTANT助教',
  status         TINYINT     NOT NULL DEFAULT 1,
  deleted        TINYINT     NOT NULL DEFAULT 0,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS edu_knowledge_point (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  subject_id BIGINT       NOT NULL,
  parent_id  BIGINT       DEFAULT NULL COMMENT '父知识点，支持层级',
  point_name VARCHAR(128) NOT NULL COMMENT '知识点名称',
  sort_order INT          NOT NULL DEFAULT 0,
  status     TINYINT      NOT NULL DEFAULT 1,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kp_subject_name (subject_id, point_name),
  KEY idx_kp_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

CREATE TABLE IF NOT EXISTS notice (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL COMMENT '公告标题',
  content      TEXT         COMMENT '公告内容',
  publisher_id BIGINT       DEFAULT NULL COMMENT '发布人 sys_user.id',
  status       TINYINT      NOT NULL DEFAULT 1,
  publish_time DATETIME     DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notice_title (title),
  KEY idx_notice_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告';

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

-- ---------- 题库 ----------

CREATE TABLE IF NOT EXISTS question (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  subject_id         BIGINT        NOT NULL,
  knowledge_point_id BIGINT        DEFAULT NULL,
  question_type      VARCHAR(32)   NOT NULL COMMENT 'SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/FILL_BLANK/SUBJECTIVE',
  difficulty         VARCHAR(32)   NOT NULL COMMENT 'EASY/MEDIUM/HARD',
  stem               TEXT          NOT NULL COMMENT '题干',
  correct_answer     TEXT          COMMENT '参考答案（客观题可空）',
  analysis           TEXT          COMMENT '解析',
  default_score      DECIMAL(6,2)  NOT NULL DEFAULT 5.00 COMMENT '默认分值',
  status             TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  deleted            TINYINT       NOT NULL DEFAULT 0,
  created_by         BIGINT        DEFAULT NULL,
  source_type        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL手动/AI_GENERATED生成/AI_IMPORTED识别/AI_MATERIAL材料生成',
  source_detail      VARCHAR(255)  DEFAULT NULL COMMENT '来源说明，如上传文件名或生成入口',
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_question_subject (subject_id),
  KEY idx_question_kp (knowledge_point_id),
  KEY idx_question_source (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目';

CREATE TABLE IF NOT EXISTS question_option (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  question_id    BIGINT        NOT NULL,
  option_label   VARCHAR(16)   NOT NULL COMMENT '选项标识 A/B/C/D',
  option_content VARCHAR(1000) NOT NULL COMMENT '选项内容',
  is_correct     TINYINT       NOT NULL DEFAULT 0 COMMENT '1正确 0错误',
  sort_order     INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_option_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目选项（随题目物理删除）';

-- ---------- 试卷 ----------

CREATE TABLE IF NOT EXISTS paper (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  subject_id  BIGINT        NOT NULL,
  paper_name  VARCHAR(200)  NOT NULL COMMENT '试卷名称',
  description VARCHAR(500)  DEFAULT NULL,
  total_score DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  deleted     TINYINT       NOT NULL DEFAULT 0,
  created_by  BIGINT        DEFAULT NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_name (paper_name),
  KEY idx_paper_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷';

CREATE TABLE IF NOT EXISTS paper_question (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  paper_id    BIGINT       NOT NULL,
  question_id BIGINT       NOT NULL,
  score       DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '该题在本卷分值',
  sort_order  INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pq_paper (paper_id),
  KEY idx_pq_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷-题目（随试卷物理删除）';

-- ---------- 考试 / 答卷 / 阅卷 ----------

CREATE TABLE IF NOT EXISTS exam (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  paper_id         BIGINT       NOT NULL,
  exam_name        VARCHAR(200) NOT NULL,
  description      VARCHAR(500) DEFAULT NULL,
  start_time       DATETIME     DEFAULT NULL,
  end_time         DATETIME     DEFAULT NULL,
  duration_minutes INT          DEFAULT NULL COMMENT '时长（分钟）',
  status           TINYINT      NOT NULL DEFAULT 0,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  created_by       BIGINT       DEFAULT NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_exam_paper (paper_id),
  KEY idx_exam_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试任务';

CREATE TABLE IF NOT EXISTS exam_class (
  id       BIGINT NOT NULL AUTO_INCREMENT,
  exam_id  BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_class (exam_id, class_id),
  KEY idx_ec_exam (exam_id),
  KEY idx_ec_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试-班级范围（兼容旧范围模型）';

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

CREATE TABLE IF NOT EXISTS exam_attempt (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  exam_id     BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL COMMENT '考生 sys_user.id',
  status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已交卷 4待批阅 5已完成',
  score       DECIMAL(10,2) DEFAULT NULL,
  start_time  DATETIME      DEFAULT NULL,
  submit_time DATETIME      DEFAULT NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_attempt_exam (exam_id),
  KEY idx_attempt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试参与/答卷';

CREATE TABLE IF NOT EXISTS answer_record (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  attempt_id     BIGINT       NOT NULL,
  question_id    BIGINT       NOT NULL,
  answer_content TEXT         COMMENT '考生作答内容',
  score          DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  is_correct     TINYINT      NOT NULL DEFAULT 0,
  review_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待批阅 1已批阅',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ar_attempt (attempt_id),
  KEY idx_ar_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录';

CREATE TABLE IF NOT EXISTS review_record (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  answer_record_id BIGINT        NOT NULL,
  reviewer_id      BIGINT        NOT NULL,
  score            DECIMAL(6,2)  NOT NULL DEFAULT 0.00,
  comment          VARCHAR(1000) DEFAULT NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rr_answer (answer_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主观题阅卷记录';

CREATE TABLE IF NOT EXISTS wrong_question_book (
  id              BIGINT   NOT NULL AUTO_INCREMENT,
  user_id         BIGINT   NOT NULL,
  question_id     BIGINT   NOT NULL,
  wrong_count     INT      NOT NULL DEFAULT 1,
  last_wrong_time DATETIME DEFAULT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wrong_user_question (user_id, question_id),
  KEY idx_wrong_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题本';

-- ---------- 监控 / 日志 ----------

CREATE TABLE IF NOT EXISTS cheat_event (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  attempt_id BIGINT      NOT NULL,
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型，如切屏/复制等',
  extra_info TEXT        COMMENT '附加信息',
  event_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cheat_attempt (attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='防作弊事件';

CREATE TABLE IF NOT EXISTS operation_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  operator_id   BIGINT       DEFAULT NULL,
  operator_name VARCHAR(64)  DEFAULT NULL,
  action        VARCHAR(128) DEFAULT NULL,
  target        VARCHAR(128) DEFAULT NULL,
  detail        TEXT         DEFAULT NULL,
  ip            VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oplog_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

-- ---------- 站内通知（个人消息，带已读状态） ----------

CREATE TABLE IF NOT EXISTS notification (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL COMMENT '接收人 ID',
  title      VARCHAR(128) NOT NULL COMMENT '通知标题',
  content    TEXT         DEFAULT NULL COMMENT '通知内容',
  type       VARCHAR(32)  DEFAULT 'INFO' COMMENT '通知类型：INFO/EXAM/APPROVAL/SYSTEM',
  link       VARCHAR(255) DEFAULT NULL COMMENT '关联跳转路径（可选）',
  is_read    TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读，1 已读',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notification_user (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知';

-- ---------- 答题草稿（自动暂存，支持刷新/断线后恢复作答） ----------

CREATE TABLE IF NOT EXISTS exam_answer_draft (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  attempt_id BIGINT   NOT NULL,
  answers    TEXT     COMMENT '前端答案 JSON 快照',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_draft_attempt (attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题草稿（自动暂存）';

-- ---------- AI 配置与调用审计 ----------

CREATE TABLE IF NOT EXISTS ai_provider_config (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  provider   VARCHAR(64)  NOT NULL,
  base_url   VARCHAR(255) DEFAULT NULL,
  model      VARCHAR(64)  DEFAULT NULL,
  enabled    TINYINT      NOT NULL DEFAULT 0,
  remark     VARCHAR(255) DEFAULT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 服务配置';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(64)  NOT NULL,
  template_name VARCHAR(128) DEFAULT NULL,
  content       TEXT         COMMENT '提示词模板',
  status        TINYINT      NOT NULL DEFAULT 1,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prompt_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提示词模板';

CREATE TABLE IF NOT EXISTS ai_usage_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       DEFAULT NULL,
  scene         VARCHAR(64)  DEFAULT NULL,
  prompt        TEXT         DEFAULT NULL,
  response      TEXT         DEFAULT NULL,
  success       TINYINT      NOT NULL DEFAULT 1,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志';

-- ---------- V2.0: 邮箱验证 ----------

-- sys_user.email_verified 列：全新库已在上方 CREATE TABLE sys_user 中包含；
-- 对「V1.0 时期已存在 sys_user」的旧库（如线上持久库），CREATE TABLE IF NOT EXISTS 不会补列，
-- 且 MySQL 不支持 ADD COLUMN IF NOT EXISTS，故补列改由应用启动时幂等自愈：
--   com.smartexam.config.DatabaseMigrationRunner（无需人工登库）。
-- 需要手工迁移时，另见 db/migration-v2.sql。

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码';

-- ---------- V3.0: Token 持久化 ----------

-- 替代内存 ConcurrentHashMap，支持服务重启后会话恢复
CREATE TABLE IF NOT EXISTS user_token (
  token      VARCHAR(64) NOT NULL COMMENT '会话令牌（UUID 去横线）',
  user_id    BIGINT      NOT NULL COMMENT '用户ID',
  expires_at DATETIME    NOT NULL COMMENT '过期时间',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (token),
  KEY idx_user_token_user_id (user_id),
  KEY idx_user_token_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话令牌';


