-- V2.0 数据库迁移脚本：邮箱系统
-- 执行方式：mysql -u root -p smart_exam < migration-v2.sql

-- 1. sys_user 新增邮箱验证状态字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证 0未验证 1已验证';

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
