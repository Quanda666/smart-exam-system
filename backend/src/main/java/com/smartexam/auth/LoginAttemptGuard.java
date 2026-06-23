package com.smartexam.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 登录失败防护：同一账号连续失败达到上限后临时锁定。
 * 失败计数持久化到数据库，避免服务重启或多实例部署绕过锁定策略。
 */
@Component
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    public LoginAttemptGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void assertNotLocked(String username) {
        String account = normalizeAccount(username);
        if (account == null) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT locked_until FROM login_attempt WHERE account = ?", account);
        if (rows.isEmpty()) {
            return;
        }
        LocalDateTime lockedUntil = toLocalDateTime(rows.get(0).get("locked_until"));
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("登录失败次数过多，账号已临时锁定，请 " + LOCK_MINUTES + " 分钟后再试");
        }
        if (lockedUntil != null) {
            recordSuccess(account);
        }
    }

    public void recordFailure(String username) {
        String account = normalizeAccount(username);
        if (account == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO login_attempt (account, failure_count, locked_until)
                VALUES (?, 1, NULL)
                ON DUPLICATE KEY UPDATE
                  failure_count = CASE
                    WHEN locked_until IS NOT NULL AND locked_until < NOW() THEN 1
                    ELSE failure_count + 1
                  END,
                  locked_until = CASE
                    WHEN (CASE
                      WHEN locked_until IS NOT NULL AND locked_until < NOW() THEN 1
                      ELSE failure_count + 1
                    END) >= ? THEN DATE_ADD(NOW(), INTERVAL ? MINUTE)
                    ELSE locked_until
                  END,
                  updated_at = CURRENT_TIMESTAMP
                """, account, MAX_FAILURES, LOCK_MINUTES);
    }

    public void recordSuccess(String username) {
        String account = normalizeAccount(username);
        if (account != null) {
            jdbcTemplate.update("DELETE FROM login_attempt WHERE account = ?", account);
        }
    }

    private String normalizeAccount(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof String text && !text.isBlank()) {
            String normalized = text.replace('T', ' ');
            return java.sql.Timestamp.valueOf(normalized.substring(0, Math.min(19, normalized.length()))).toLocalDateTime();
        }
        return null;
    }
}
