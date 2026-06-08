package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.system.CreateUserRequest;
import com.smartexam.dto.system.UpdateUserRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.util.PasswordHashUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;
    private final TeachingScopeService teachingScopeService;

    public UserService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, NotificationService notificationService,
                       TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
        this.teachingScopeService = teachingScopeService;
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
                       sp.student_no AS studentNo, COALESCE(sp.primary_class_id, sp.class_id) AS classId,
                       c.class_name AS className, c.class_type AS classType,
                       tp.teacher_no AS teacherNo, tp.hire_date AS hireDate, tp.title AS teacherTitle, tp.college AS teacherCollege
               FROM sys_user u
               LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
               LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id)
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
        teachingScopeService.clearStudentScope(id);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
    }

    @Transactional
    public Map<String, Object> createUser(CreateUserRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String username = trim(request.getUsername());
        String realName = trim(request.getRealName());
        String roleType = request.getRoleType().toUpperCase();

        // Check username availability
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0", Integer.class, username);
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        // Find role id
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0", roleType);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("角色不存在: " + roleType);
        }
        Long roleId = ((Number) roles.get(0).get("id")).longValue();

        // Insert user
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sys_user (username, password_hash, real_name, phone, email, status) VALUES (?, ?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, PasswordHashUtil.encode(request.getPassword()));
            statement.setString(3, realName);
            statement.setString(4, trim(request.getPhone()));
            statement.setString(5, trim(request.getEmail()));
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("创建用户失败，无法获取用户ID");
        }
        Long userId = generatedId.longValue();

        // Assign role
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);

        // Create profile
        if ("STUDENT".equals(roleType)) {
            jdbcTemplate.update(
                    "INSERT INTO student_profile (user_id, student_no, class_id, primary_class_id, status) VALUES (?, ?, ?, ?, 1)",
                    userId, trim(request.getStudentNo()), request.getClassId(), request.getClassId());
            teachingScopeService.syncStudentPrimaryClass(userId, request.getClassId());
        } else if ("TEACHER".equals(roleType)) {
            jdbcTemplate.update(
                    "INSERT INTO teacher_profile (user_id, teacher_no, title, status) VALUES (?, ?, ?, 1)",
                    userId, trim(request.getTeacherNo()), trim(request.getTitle()));
        }

        return findUserById(userId);
    }

    @Transactional
    public Map<String, Object> updateUser(Long id, UpdateUserRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String realName = trim(request.getRealName());
        String roleType = request.getRoleType().toUpperCase();

        // Check user exists
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0", Integer.class, id);
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("用户不存在");
        }

        // Update sys_user
        jdbcTemplate.update(
                "UPDATE sys_user SET real_name = ?, phone = ?, email = ? WHERE id = ? AND deleted = 0",
                realName, trim(request.getPhone()), trim(request.getEmail()), id);

        // Update role: remove old, assign new
        Long newRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0", Long.class, roleType);
        if (newRoleId == null) {
            throw new IllegalArgumentException("角色不存在: " + roleType);
        }

        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, newRoleId);

        // Update or remove profiles based on new role
        if ("STUDENT".equals(roleType)) {
            jdbcTemplate.update("DELETE FROM teacher_profile WHERE user_id = ?", id);
            int profileRows = jdbcTemplate.update(
                    "UPDATE student_profile SET student_no = ?, class_id = ?, primary_class_id = ?, deleted = 0 WHERE user_id = ?",
                    trim(request.getStudentNo()), request.getClassId(), request.getClassId(), id);
            if (profileRows == 0) {
                jdbcTemplate.update(
                        "INSERT INTO student_profile (user_id, student_no, class_id, primary_class_id, status) VALUES (?, ?, ?, ?, 1)",
                        id, trim(request.getStudentNo()), request.getClassId(), request.getClassId());
            }
            teachingScopeService.syncStudentPrimaryClass(id, request.getClassId());
        } else if ("TEACHER".equals(roleType)) {
            teachingScopeService.clearStudentScope(id);
            jdbcTemplate.update("DELETE FROM student_profile WHERE user_id = ?", id);
            int profileRows = jdbcTemplate.update(
                    "UPDATE teacher_profile SET teacher_no = ?, title = ? WHERE user_id = ?",
                    trim(request.getTeacherNo()), trim(request.getTitle()), id);
            if (profileRows == 0) {
                jdbcTemplate.update(
                        "INSERT INTO teacher_profile (user_id, teacher_no, title, status) VALUES (?, ?, ?, 1)",
                        id, trim(request.getTeacherNo()), trim(request.getTitle()));
            }
        } else {
            // ADMIN: remove both profiles
            teachingScopeService.clearStudentScope(id);
            jdbcTemplate.update("DELETE FROM student_profile WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM teacher_profile WHERE user_id = ?", id);
        }

        return findUserById(id);
    }

    private Map<String, Object> findUserById(Long id) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email,
                       u.status, u.created_at AS createdAt, u.updated_at AS updatedAt,
                       s.student_no AS studentNo, c.class_name AS className, COALESCE(s.primary_class_id, s.class_id) AS classId,
                       c.class_type AS classType,
                       t.teacher_no AS teacherNo, t.hire_date AS hireDate, t.title, t.college AS teacherCollege,
                       (SELECT GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',')
                        FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id) AS roleCodes
                FROM sys_user u
                LEFT JOIN student_profile s ON s.user_id = u.id AND s.deleted = 0
                LEFT JOIN edu_class c ON c.id = COALESCE(s.primary_class_id, s.class_id) AND c.deleted = 0
                LEFT JOIN teacher_profile t ON t.user_id = u.id AND t.deleted = 0
                WHERE u.id = ? AND u.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        return rows.get(0);
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
