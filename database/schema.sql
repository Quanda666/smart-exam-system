CREATE DATABASE IF NOT EXISTS smart_exam_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_exam_system;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码摘要',
  real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
  phone VARCHAR(32) NULL COMMENT '手机号',
  email VARCHAR(128) NULL COMMENT '邮箱',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_sys_user_role (user_id, role_id),
  KEY idx_sys_user_role_user_id (user_id),
  KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS edu_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '班级ID',
  class_name VARCHAR(128) NOT NULL COMMENT '班级名称',
  major VARCHAR(128) NULL COMMENT '专业',
  grade VARCHAR(32) NULL COMMENT '年级',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_edu_class_name (class_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

CREATE TABLE IF NOT EXISTS student_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学生档案ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  student_no VARCHAR(64) NOT NULL COMMENT '学号',
  class_id BIGINT NULL COMMENT '班级ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_student_profile_user_id (user_id),
  UNIQUE KEY uk_student_profile_student_no (student_no),
  KEY idx_student_profile_class_id (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生档案表';

CREATE TABLE IF NOT EXISTS teacher_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '教师档案ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  teacher_no VARCHAR(64) NOT NULL COMMENT '工号',
  title VARCHAR(64) NULL COMMENT '职称',
  introduction VARCHAR(512) NULL COMMENT '简介',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_teacher_profile_user_id (user_id),
  UNIQUE KEY uk_teacher_profile_teacher_no (teacher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师档案表';

CREATE TABLE IF NOT EXISTS edu_subject (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '科目ID',
  subject_name VARCHAR(128) NOT NULL COMMENT '科目名称',
  description VARCHAR(512) NULL COMMENT '科目描述',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_edu_subject_name (subject_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科目表';

CREATE TABLE IF NOT EXISTS edu_knowledge_point (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识点ID',
  subject_id BIGINT NOT NULL COMMENT '科目ID',
  parent_id BIGINT NULL COMMENT '父级知识点ID',
  point_name VARCHAR(128) NOT NULL COMMENT '知识点名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  KEY idx_edu_knowledge_point_subject_id (subject_id),
  KEY idx_edu_knowledge_point_parent_id (parent_id),
  UNIQUE KEY uk_edu_knowledge_point_subject_name (subject_id, point_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

CREATE TABLE IF NOT EXISTS question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
  subject_id BIGINT NOT NULL COMMENT '科目ID',
  knowledge_point_id BIGINT NULL COMMENT '知识点ID',
  question_type VARCHAR(32) NOT NULL COMMENT '题型：SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/FILL_BLANK/SUBJECTIVE',
  difficulty VARCHAR(32) NOT NULL COMMENT '难度：EASY/MEDIUM/HARD',
  stem TEXT NOT NULL COMMENT '题干',
  correct_answer TEXT NULL COMMENT '参考答案',
  analysis TEXT NULL COMMENT '题目解析',
  default_score DECIMAL(8,2) NOT NULL DEFAULT 5.00 COMMENT '默认分值',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：1发布，0草稿',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  KEY idx_question_subject_id (subject_id),
  KEY idx_question_knowledge_point_id (knowledge_point_id),
  KEY idx_question_type (question_type),
  KEY idx_question_difficulty (difficulty),
  KEY idx_question_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

CREATE TABLE IF NOT EXISTS question_option (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目选项ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  option_label VARCHAR(16) NOT NULL COMMENT '选项标识，如A/B/C/D',
  option_content TEXT NOT NULL COMMENT '选项内容',
  is_correct TINYINT NOT NULL DEFAULT 0 COMMENT '是否正确：1是，0否',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_question_option_label (question_id, option_label),
  KEY idx_question_option_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目选项表';

CREATE TABLE IF NOT EXISTS paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷ID',
  subject_id BIGINT NOT NULL COMMENT '科目ID',
  paper_name VARCHAR(128) NOT NULL COMMENT '试卷名称',
  description VARCHAR(512) NULL COMMENT '试卷说明',
  total_score DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT '试卷总分',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：1发布，0草稿',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_paper_name (paper_name),
  KEY idx_paper_subject_id (subject_id),
  KEY idx_paper_status (status),
  KEY idx_paper_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';

CREATE TABLE IF NOT EXISTS paper_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷题目ID',
  paper_id BIGINT NOT NULL COMMENT '试卷ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  score DECIMAL(8,2) NOT NULL DEFAULT 5.00 COMMENT '本卷题目分值',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '题目顺序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_paper_question (paper_id, question_id),
  KEY idx_paper_question_paper_id (paper_id),
  KEY idx_paper_question_question_id (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷题目关联表';

CREATE TABLE IF NOT EXISTS notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '公告ID',
  title VARCHAR(128) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT '公告内容',
  publisher_id BIGINT NULL COMMENT '发布人ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1发布，0草稿或停用',
  publish_time DATETIME NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_notice_title (title),
  KEY idx_notice_status (status),
  KEY idx_notice_publish_time (publish_time),
  KEY idx_notice_publisher_id (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

CREATE TABLE IF NOT EXISTS ai_provider_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI配置ID',
  provider_name VARCHAR(128) NOT NULL COMMENT '提供方名称',
  base_url VARCHAR(512) NOT NULL COMMENT 'OpenAI兼容Base URL',
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  api_key_ref VARCHAR(128) NULL COMMENT '密钥引用名称，不存储明文密钥',
  mock_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用模拟响应：1是，0否',
  timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '超时时间，单位秒',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI提供方配置表';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '提示词模板ID',
  scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
  template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
  template_content TEXT NOT NULL COMMENT '模板内容',
  version_no VARCHAR(32) NOT NULL DEFAULT 'v1' COMMENT '版本号',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_ai_prompt_template_scene_version (scene_code, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI提示词模板表';

CREATE TABLE IF NOT EXISTS ai_usage_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI调用日志ID',
  user_id BIGINT NULL COMMENT '调用用户ID',
  scene_code VARCHAR(64) NOT NULL COMMENT '调用场景',
  provider_name VARCHAR(128) NULL COMMENT '提供方名称',
  model_name VARCHAR(128) NULL COMMENT '模型名称',
  request_summary VARCHAR(1024) NULL COMMENT '请求摘要，避免存储敏感原文',
  response_summary VARCHAR(1024) NULL COMMENT '响应摘要，避免存储过多原文',
  duration_ms INT NULL COMMENT '耗时毫秒',
  success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1成功，0失败',
  error_message VARCHAR(1024) NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_ai_usage_log_scene_code (scene_code),
  KEY idx_ai_usage_log_user_id (user_id),
  KEY idx_ai_usage_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';
