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
 * Token 持久化存储（数据库）。
 * 替代原先的内存 ConcurrentHashMap，支持服务重启后会话恢复。
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
     * <p>SQL 表名/列名以真实 schema 为准，并与 {@link com.smartexam.service.AuthService} 登录时的查询保持一致：
     * 角色关联表为 sys_user_role、角色编码列为 role_code、用户启用状态列为 status（无 enabled / profile 列）。
     */
    public Optional<TokenSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            // token 记录：用 queryForList 判空处理"未登录"这一正常路径，避免用异常充当控制流
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

            // 重建用户基础信息（启用且未删除）
            List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                    "SELECT id, username, real_name FROM sys_user WHERE id = ? AND status = 1 AND deleted = 0", userId);
            if (userRows.isEmpty()) {
                // 用户被禁用或删除，会话失效
                return Optional.empty();
            }
            Map<String, Object> userRow = userRows.get(0);

            // 重建角色（与 AuthService#findRoles 完全一致）
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

            // profile 仅用于登录响应展示，鉴权链路不依赖（无业务代码调用 AuthUser#getProfile），重建时留空安全
            AuthUser user = new AuthUser(
                    ((Number) userRow.get("id")).longValue(),
                    (String) userRow.get("username"),
                    (String) userRow.get("real_name"),
                    roles,
                    new LinkedHashMap<>());

            return Optional.of(new TokenSession(token, user, expiresAt));
        } catch (Exception e) {
            // 校验过程发生异常（如数据库故障）：记录日志以便排查，按未登录处理，避免请求 500 风暴。
            // 注意：不要在此静默吞掉异常而不记录，否则 SQL 错误会被伪装成"全站未登录"，极难定位。
            log.error("Token 校验失败，按未登录处理：{}", e.getMessage(), e);
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

    /**
     * 兼容 JDBC 对 DATETIME 列的不同返回类型（Timestamp / LocalDateTime / 字符串）。
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
