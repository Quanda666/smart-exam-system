package com.smartexam.auth;

import com.smartexam.dto.auth.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Token 持久化存储（数据库）。
 * 替代原先的内存 ConcurrentHashMap，支持服务重启后会话恢复。
 */
@Component
public class TokenStore {

    private static final int TOKEN_TTL_HOURS = 8;

    private final JdbcTemplate jdbcTemplate;

    public TokenStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建新的会话令牌并持久化到数据库。
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
     * 查找有效的会话（从数据库读取 + 重建 AuthUser）。
     */
    public Optional<TokenSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            // 查询 token 记录
            Map<String, Object> tokenRecord = jdbcTemplate.queryForMap(
                "SELECT user_id, expires_at FROM user_token WHERE token = ?",
                token
            );

            LocalDateTime expiresAt = ((java.sql.Timestamp) tokenRecord.get("expires_at")).toLocalDateTime();
            if (expiresAt.isBefore(LocalDateTime.now())) {
                // 已过期，删除记录
                jdbcTemplate.update("DELETE FROM user_token WHERE token = ?", token);
                return Optional.empty();
            }

            // 重建 AuthUser（从 sys_user + user_roles 查询）
            Long userId = ((Number) tokenRecord.get("user_id")).longValue();
            Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT id, username, real_name AS realName, profile FROM sys_user WHERE id = ? AND enabled = 1",
                userId
            );

            List<String> roles = jdbcTemplate.queryForList(
                "SELECT r.code FROM user_roles ur JOIN sys_role r ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class,
                userId
            );

            if (roles.isEmpty()) {
                return Optional.empty();
            }

            // profile 字段可能为空
            Map<String, Object> profile = new LinkedHashMap<>();
            Object profileObj = userRow.get("profile");
            if (profileObj instanceof String profileJson && profileJson != null && !profileJson.isBlank()) {
                // 如果需要解析 JSON profile，这里简化处理（或使用 Jackson）
                // 当前直接传空 Map，不影响核心功能
            }

            AuthUser user = new AuthUser(
                ((Number) userRow.get("id")).longValue(),
                (String) userRow.get("username"),
                (String) userRow.get("realName"),
                roles,
                profile
            );

            return Optional.of(new TokenSession(token, user, expiresAt));

        } catch (Exception e) {
            // token 不存在或查询失败，返回空
            return Optional.empty();
        }
    }

    /**
     * 撤销会话（登出时删除数据库记录）。
     */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            jdbcTemplate.update("DELETE FROM user_token WHERE token = ?", token);
        }
    }

    /**
     * 清理过期的 token（定期调用，避免表膨胀）。
     */
    private void clearExpiredTokens() {
        jdbcTemplate.update("DELETE FROM user_token WHERE expires_at < NOW()");
    }
}
