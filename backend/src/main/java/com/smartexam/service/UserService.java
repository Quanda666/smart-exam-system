package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.util.PasswordHashUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;

    public UserService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, NotificationService notificationService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
    }

    public PageResult<Map<String, Object>> listUsers(String keyword, String role, Integer status, int page, int size) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String kw = blankToNull(keyword);
        String roleCode = blankToNull(role);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                WHERE u.deleted = 0
                  AND (? IS NULL OR u.username LIKE CONCAT('%', ?, '%') OR u.real_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR u.status = ?)
                  AND (? IS NULL OR EXISTS (
                        SELECT 1 FROM sys_user_role ur2 JOIN sys_role r2 ON r2.id = ur2.role_id
                         WHERE ur2.user_id = u.id AND r2.role_code = ?))
                """, Long.class, kw, kw, kw, status, status, roleCode, roleCode);
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        // 注意：分页参数用占位符 ? 传入，绝不能对含 LIKE '%' 的 SQL 调用 String.formatted，
        // 否则 '%' 后的字符会被当成格式转换符，抛 UnknownFormatConversionException: Conversion = '''。
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email, u.status,
                       u.created_at AS createdAt, u.updated_at AS updatedAt,
                       (SELECT GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',')
                          FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                         WHERE ur.user_id = u.id) AS roleCodes,
                       sp.student_no AS studentNo, c.class_name AS className,
                       tp.teacher_no AS teacherNo, tp.title AS teacherTitle
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.class_id
                LEFT JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                WHERE u.deleted = 0
                  AND (? IS NULL OR u.username LIKE CONCAT('%', ?, '%') OR u.real_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR u.status = ?)
                  AND (? IS NULL OR EXISTS (
                        SELECT 1 FROM sys_user_role ur2 JOIN sys_role r2 ON r2.id = ur2.role_id
                         WHERE ur2.user_id = u.id AND r2.role_code = ?))
                ORDER BY u.id DESC
                LIMIT ? OFFSET ?
                """, kw, kw, kw, status, status, roleCode, roleCode, safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public Map<String, Object> summary() {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS active,
                       COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS disabled
                FROM sys_user WHERE deleted = 0
                """);
    }

    public List<Map<String, Object>> listRoles() {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT r.id, r.role_code AS roleCode, r.role_name AS roleName, r.status,
                       (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id
                         WHERE ur.role_id = r.id AND u.deleted = 0) AS userCount
                FROM sys_role r WHERE r.deleted = 0 ORDER BY r.id
                """);
    }

    public void updateStatus(Long id, Integer status, Long currentUserId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("用户状态只能为0或1");
        }
        if (Objects.equals(id, currentUserId)) {
            throw new IllegalArgumentException("不能修改当前登录账号自身的状态");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ? AND deleted = 0", status, id);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 账号从禁用改为启用时，通知本人（教师审核通过等场景）
        if (status == 1) {
            notificationService.send(id, "账号已启用", "您的账号已通过审核并启用，现在可以正常登录使用系统。", "APPROVAL", null);
        }
    }

    public void resetPassword(Long id, String newPassword) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE sys_user SET password_hash = ? WHERE id = ? AND deleted = 0",
                PasswordHashUtil.encode(newPassword), id);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Transactional
    public void deleteUser(Long id, Long currentUserId) {
        if (Objects.equals(id, currentUserId)) {
            throw new IllegalArgumentException("不能删除当前登录账号自身");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int rows = jdbcTemplate.update("UPDATE sys_user SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
