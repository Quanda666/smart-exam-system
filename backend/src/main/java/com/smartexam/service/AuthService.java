package com.smartexam.service;

import com.smartexam.auth.TokenSession;
import com.smartexam.auth.TokenStore;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.dto.auth.RegisterRequest;
import com.smartexam.util.PasswordHashUtil;
import org.springframework.beans.factory.ObjectProvider;
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

@Service
public class AuthService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TokenStore tokenStore;
    private final MenuService menuService;

    public AuthService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, TokenStore tokenStore, MenuService menuService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.tokenStore = tokenStore;
        this.menuService = menuService;
    }

    public LoginResponse login(LoginRequest request) {
        Map<String, Object> userRow = findUserRow(request.getUsername());
        if (userRow == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        String passwordHash = stringValue(userRow.get("password_hash"));
        if (!PasswordHashUtil.matches(request.getPassword(), passwordHash)) {
            throw new IllegalArgumentException("账号或密码错误");
        }

        AuthUser user = buildAuthUser(userRow);
        TokenSession session = tokenStore.create(user);
        List<MenuItem> menus = menuService.menusFor(user);
        return new LoginResponse(session.getToken(), session.getExpiresAt(), user, menus, user.getDefaultPath());
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String username = trim(request.getUsername());
        String realName = trim(request.getRealName());
        String roleType = normalizeRole(request.getRoleType());

        validateRegisterRequest(request, username, realName, roleType);
        ensureUsernameAvailable(jdbcTemplate, username);
        Long roleId = findRoleId(jdbcTemplate, roleType);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sys_user (username, password_hash, real_name, phone, email, status)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, PasswordHashUtil.encode(request.getPassword()));
            statement.setString(3, realName);
            statement.setString(4, blankToNull(request.getPhone()));
            statement.setString(5, blankToNull(request.getEmail()));
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("注册失败，无法获取用户ID");
        }
        Long userId = generatedId.longValue();

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        if ("STUDENT".equals(roleType)) {
            validateClassExists(jdbcTemplate, request.getClassId());
            jdbcTemplate.update("""
                    INSERT INTO student_profile (user_id, student_no, class_id, status)
                    VALUES (?, ?, ?, 1)
                    """, userId, trim(request.getStudentNo()), request.getClassId());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO teacher_profile (user_id, teacher_no, title, introduction, status)
                    VALUES (?, ?, ?, ?, 1)
                    """, userId, trim(request.getTeacherNo()), blankToNull(request.getTitle()), blankToNull(request.getIntroduction()));
        }

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    public Map<String, Object> registerOptions() {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> classes = jdbcTemplate.queryForList("""
                SELECT id, class_name AS className, major, grade
                FROM edu_class
                WHERE status = 1 AND deleted = 0
                ORDER BY grade DESC, class_name ASC
                """);
        return Map.of(
                "roles", List.of(
                        Map.of("value", "STUDENT", "label", "学生"),
                        Map.of("value", "TEACHER", "label", "教师")
                ),
                "classes", classes
        );
    }

    public LoginResponse buildCurrentResponse(AuthUser user) {
        List<MenuItem> menus = menuService.menusFor(user);
        return new LoginResponse(null, null, user, menus, user.getDefaultPath());
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }

    private AuthUser buildAuthUser(Map<String, Object> userRow) {
        Long userId = longValue(userRow.get("id"));
        String username = stringValue(userRow.get("username"));
        String realName = stringValue(userRow.get("real_name"));
        List<String> roles = findRoles(userId);
        Map<String, Object> profile = findProfile(userId, roles);
        return new AuthUser(userId, username, realName, roles, profile);
    }

    private Map<String, Object> findUserRow(String username) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, username, password_hash, real_name, status
                FROM sys_user
                WHERE username = ? AND deleted = 0
                LIMIT 1
                """, username);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Integer status = intValue(row.get("status"));
        if (status == null || status != 1) {
            throw new IllegalArgumentException("账号已被禁用，请联系管理员");
        }
        return row;
    }

    private List<String> findRoles(Long userId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.status = 1 AND r.deleted = 0
                ORDER BY r.id
                """, String.class, userId);
        if (roles.isEmpty()) {
            throw new IllegalStateException("当前账号未分配有效角色，请联系管理员");
        }
        return roles;
    }

    private Map<String, Object> findProfile(Long userId, List<String> roles) {
        Map<String, Object> profile = new LinkedHashMap<>();
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();

        if (roles.stream().anyMatch("STUDENT"::equalsIgnoreCase)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT sp.student_no, c.class_name, c.major, c.grade
                    FROM student_profile sp
                    LEFT JOIN edu_class c ON c.id = sp.class_id
                    WHERE sp.user_id = ? AND sp.deleted = 0
                    LIMIT 1
                    """, userId);
            if (!rows.isEmpty()) {
                profile.putAll(rows.get(0));
            }
        }
        if (roles.stream().anyMatch("TEACHER"::equalsIgnoreCase)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT teacher_no, title, introduction
                    FROM teacher_profile
                    WHERE user_id = ? AND deleted = 0
                    LIMIT 1
                    """, userId);
            if (!rows.isEmpty()) {
                profile.putAll(rows.get(0));
            }
        }
        if (roles.stream().anyMatch("ADMIN"::equalsIgnoreCase)) {
            profile.put("scope", "全局管理");
        }
        return profile;
    }

    private void validateRegisterRequest(RegisterRequest request, String username, String realName, String roleType) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (realName == null || realName.isBlank()) {
            throw new IllegalArgumentException("真实姓名不能为空");
        }
        if ("STUDENT".equals(roleType)) {
            if (blankToNull(request.getStudentNo()) == null) {
                throw new IllegalArgumentException("学生注册必须填写学号");
            }
            if (request.getClassId() == null) {
                throw new IllegalArgumentException("学生注册必须选择所属班级");
            }
        } else if ("TEACHER".equals(roleType)) {
            if (blankToNull(request.getTeacherNo()) == null) {
                throw new IllegalArgumentException("教师注册必须填写工号");
            }
        } else {
            throw new IllegalArgumentException("仅支持学生或教师注册");
        }
    }

    private void ensureUsernameAvailable(JdbcTemplate jdbcTemplate, String username) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM sys_user
                WHERE username = ? AND deleted = 0
                """, Integer.class, username);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private Long findRoleId(JdbcTemplate jdbcTemplate, String roleType) {
        List<Long> roleIds = jdbcTemplate.queryForList("""
                SELECT id
                FROM sys_role
                WHERE role_code = ? AND status = 1 AND deleted = 0
                LIMIT 1
                """, Long.class, roleType);
        if (roleIds.isEmpty()) {
            throw new IllegalStateException("系统角色未初始化，请先执行数据库初始化脚本");
        }
        return roleIds.get(0);
    }

    private void validateClassExists(JdbcTemplate jdbcTemplate, Long classId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM edu_class
                WHERE id = ? AND status = 1 AND deleted = 0
                """, Integer.class, classId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("所选班级不存在或已停用");
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }

    private String normalizeRole(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
