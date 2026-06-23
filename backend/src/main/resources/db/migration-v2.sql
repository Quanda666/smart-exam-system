-- V2.0 数据库迁移脚本：邮箱系统（MySQL 8.x 兼容，可重复执行）
-- 用途：把 V1.0 时期建的旧库升级到支持「邮箱验证码登录 / 绑定邮箱」。
-- 执行方式：mysql -u root -p smart_exam_system < migration-v2.sql
--
-- 注意：MySQL 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（那是 MariaDB 语法），
--       因此这里用 information_schema 判断列是否存在 + 动态 SQL 实现幂等加列，
--       可安全重复执行，不会因列已存在而报错。

-- 1. sys_user 新增邮箱验证状态字段（已存在则跳过）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'email_verified');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''邮箱是否已验证 0未验证 1已验证''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1b. sys_user 新增头像字段（base64 dataURL，已存在则跳过）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'sys_user'
                      AND COLUMN_NAME = 'avatar');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar LONGTEXT DEFAULT NULL COMMENT ''头像 base64 dataURL（前端压缩后存储）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 邮箱验证码表（如已有则跳过）
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
