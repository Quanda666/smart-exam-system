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
 * 应用启动后，幂等地把 V1.0 时期建立的旧数据库补齐到 V2.0 邮箱功能所需结构。
 *
 * <p>背景：{@code spring.sql.init} 的 schema.sql 用的是 {@code CREATE TABLE IF NOT EXISTS}，
 * 对「已存在的 sys_user 表」不会补列；而 MySQL 又不支持 {@code ADD COLUMN IF NOT EXISTS}
 * （那是 MariaDB 语法）。因此线上持久库（如 Railway）从 V1.0 升级后会缺少
 * {@code sys_user.email_verified} 列，导致发送验证码接口抛 BadSqlGrammarException。
 * 这里在应用启动后做一次轻量自愈，省去人工登库改表（Railway 托管库尤其不便手工执行）。
 *
 * <p>这一步在 {@code spring.sql.init} 之后执行（ApplicationRunner 晚于数据源初始化），
 * 因此与 schema.sql/data.sql 不冲突，且对全新库为无操作（列与表都已存在）。
 *
 * <p><b>关键约束</b>：任何异常只记录日志、绝不向外抛出——迁移失败也不能阻止应用启动，
 * 避免一次性表结构问题把整个线上服务拖垮。
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
    }

    /** sys_user.email_verified 缺失则补列（V1.0 旧库升级场景）。 */
    private void ensureSysUserEmailVerifiedColumn(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' "
                            + "AND COLUMN_NAME = 'email_verified'",
                    Integer.class);
            if (count != null && count > 0) {
                return; // 列已存在，无需处理
            }
            jdbc.execute("ALTER TABLE sys_user ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 "
                    + "COMMENT '邮箱是否已验证 0未验证 1已验证'");
            log.info("数据库自愈迁移：已为 sys_user 补充 email_verified 列");
        } catch (Exception ex) {
            log.error("数据库自愈迁移：为 sys_user 补列 email_verified 失败（不影响应用启动），原因：{}", ex.getMessage());
        }
    }

    /** sys_user.avatar 缺失则补列（V2.0 头像功能升级场景；缺此列会导致登录时 findProfile 抛 BadSqlGrammarException）。 */
    private void ensureSysUserAvatarColumn(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' "
                            + "AND COLUMN_NAME = 'avatar'",
                    Integer.class);
            if (count != null && count > 0) {
                return; // 列已存在，无需处理
            }
            jdbc.execute("ALTER TABLE sys_user ADD COLUMN avatar LONGTEXT DEFAULT NULL "
                    + "COMMENT '头像 base64 dataURL（前端压缩后存储）'");
            log.info("数据库自愈迁移：已为 sys_user 补充 avatar 列");
        } catch (Exception ex) {
            log.error("数据库自愈迁移：为 sys_user 补列 avatar 失败（不影响应用启动），原因：{}", ex.getMessage());
        }
    }

    /** email_verification 表缺失则创建（DB_INIT_MODE=never 等 schema.sql 未执行的场景下兜底）。 */
    private void ensureEmailVerificationTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute("""
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
        } catch (Exception ex) {
            log.error("数据库自愈迁移：创建 email_verification 表失败（不影响应用启动），原因：{}", ex.getMessage());
        }
    }
}
