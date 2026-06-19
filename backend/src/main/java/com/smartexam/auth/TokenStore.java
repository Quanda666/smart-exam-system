package com.smartexam.auth;

import com.smartexam.dto.auth.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 持久化会话令牌，支持服务重启后的登录态恢复。
 */
@Component
public class TokenStore {

    private static final Logger log = LoggerFactory.getLogger(TokenStore.class);

    private static final int TOKEN_TTL_HOURS = 8;

    private final JdbcTemplate jdbcTemplate;

    public TokenStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建新的会话令牌并写入数据库。
     */
    public TokenSession create(AuthUser user) {
        clearExpiredTokens();
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_TTL_HOURS);

        jdbcTemplate.update(
            "INSERT INTO user_token (token, user_id, expires_at) VALUES (?, ?, ?)",
            token, user.getId(), expiresAt
        );

        return new TokenSession(token, user, expiresAt);
    }

    /**
     * 查询有效会话，并按当前用户、角色状态重建 AuthUser。
     */
    public Optional<TokenSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            List<Map<String, Object>> tokenRows = jdbcTemplate.queryForList(
                    "SELECT user_id, expires_at FROM user_token WHERE token = ?", token);
            if (tokenRows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> tokenRecord = tokenRows.get(0);

            LocalDateTime expiresAt = toLocalDateTime(tokenRecord.get("expires_at"));
            if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
                jdbcTemplate.update("DELETE FROM user_token WHERE token = ?", token);
                return Optional.empty();
            }

            Long userId = ((Number) tokenRecord.get("user_id")).longValue();

            List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                    "SELECT id, username, real_name FROM sys_user WHERE id = ? AND status = 1 AND deleted = 0", userId);
            if (userRows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> userRow = userRows.get(0);

            List<String> roles = jdbcTemplate.queryForList("""
                    SELECT r.role_code
                    FROM sys_role r
                    JOIN sys_user_role ur ON ur.role_id = r.id
                    WHERE ur.user_id = ? AND r.status = 1 AND r.deleted = 0
                    ORDER BY r.id
                    """, String.class, userId);
            if (roles.isEmpty()) {
                return Optional.empty();
            }

            AuthUser user = new AuthUser(
                    ((Number) userRow.get("id")).longValue(),
                    (String) userRow.get("username"),
                    (String) userRow.get("real_name"),
                    roles,
                    new LinkedHashMap<>());

            return Optional.of(new TokenSession(token, user, expiresAt));
        } catch (Exception e) {
            log.error("Token 校验失败，按未登录处理: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 撤销会话令牌，通常用于登出。
     */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            jdbcTemplate.update("DELETE FROM user_token WHERE token = ?", token);
        }
    }

    /**
     * 撤销某个用户的全部会话令牌，用于密码变更、账号禁用或删除。
     */
    public void revokeUserTokens(Long userId) {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM user_token WHERE user_id = ?", userId);
        }
    }

    /**
     * 清理过期 token，避免表持续膨胀。
     */
    private void clearExpiredTokens() {
        jdbcTemplate.update("DELETE FROM user_token WHERE expires_at < NOW()");
    }

    /**
     * 兼容 JDBC 对 DATETIME 的常见返回类型。
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof String s && !s.isBlank()) {
            return java.sql.Timestamp.valueOf(s.replace('T', ' ').substring(0, Math.min(19, s.length()))).toLocalDateTime();
        }
        return null;
    }
}
