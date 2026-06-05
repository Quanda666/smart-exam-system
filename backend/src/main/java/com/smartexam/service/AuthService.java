package com.smartexam.service;

import com.smartexam.auth.TokenSession;
import com.smartexam.auth.TokenStore;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.util.PasswordHashUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final List<Map<String, Object>> FALLBACK_USERS = List.of(
            demoUser(1L, "admin", "系统管理员", "sha256$admin-stage2-salt$c99c4e073f3b4ad0ac1daeeb6628472f62b74cb82a0538e94fa6253f4f93651d", "ADMIN"),
            demoUser(2L, "teacher1", "演示教师一", "sha256$teacher-stage2-salt$5b4278abf3a831fb9f44c1abe28b3a2638753060fae00bfdbfbf8ee9535abeb8", "TEACHER"),
            demoUser(3L, "student1", "演示学生一", "sha256$student-stage2-salt$eccad4a3297ed7e56b116c71709eb0df8de81a2a9005cec8335f950a2e36273f", "STUDENT")
    );

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

    public LoginResponse buildCurrentResponse(AuthUser user) {
        List<MenuItem> menus = menuService.menusFor(user);
        return new LoginResponse(null, null, user, menus, user.getDefaultPath());
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }

    public List<Map<String, Object>> demoUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        users.add(demoAccount("admin", "admin123", "管理员", "/admin", "管理用户、角色、日志和全局成绩分析"));
        users.add(demoAccount("teacher1", "teacher123", "教师", "/teacher", "维护题库、试卷、考试任务和阅卷"));
        users.add(demoAccount("student1", "student123", "学生", "/student", "查看待考、参加考试、查询成绩和错题"));
        return users;
    }

    private AuthUser buildAuthUser(Map<String, Object> userRow) {
        Long userId = longValue(userRow.get("id"));
        String username = stringValue(userRow.get("username"));
        String realName = stringValue(userRow.get("real_name"));
        List<String> roles = findRoles(userId, stringValue(userRow.get("role_code")));
        Map<String, Object> profile = findProfile(userId, roles);
        return new AuthUser(userId, username, realName, roles, profile);
    }

    private Map<String, Object> findUserRow(String username) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT id, username, password_hash, real_name, status
                        FROM sys_user
                        WHERE username = ? AND deleted = 0
                        LIMIT 1
                        """, username);
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    Integer status = intValue(row.get("status"));
                    if (status == null || status != 1) {
                        throw new IllegalArgumentException("账号已被禁用，请联系管理员");
                    }
                    return row;
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
                return findFallbackUser(username);
            }
        }
        return findFallbackUser(username);
    }

    private List<String> findRoles(Long userId, String fallbackRole) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null && userId != null) {
            try {
                List<String> roles = jdbcTemplate.queryForList("""
                        SELECT r.role_code
                        FROM sys_role r
                        JOIN sys_user_role ur ON ur.role_id = r.id
                        WHERE ur.user_id = ? AND r.status = 1 AND r.deleted = 0
                        ORDER BY r.id
                        """, String.class, userId);
                if (!roles.isEmpty()) {
                    return roles;
                }
            } catch (Exception ignored) {
                // 数据库不可用时回退到演示账号角色。
            }
        }
        if (fallbackRole != null && !fallbackRole.isBlank()) {
            return List.of(fallbackRole);
        }
        return List.of("STUDENT");
    }

    private Map<String, Object> findProfile(Long userId, List<String> roles) {
        Map<String, Object> profile = new LinkedHashMap<>();
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || userId == null) {
            fillFallbackProfile(profile, roles);
            return profile;
        }

        try {
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
        } catch (Exception ignored) {
            fillFallbackProfile(profile, roles);
        }

        if (profile.isEmpty()) {
            fillFallbackProfile(profile, roles);
        }
        return profile;
    }

    private void fillFallbackProfile(Map<String, Object> profile, List<String> roles) {
        if (roles.stream().anyMatch("ADMIN"::equalsIgnoreCase)) {
            profile.put("scope", "全局管理");
        } else if (roles.stream().anyMatch("TEACHER"::equalsIgnoreCase)) {
            profile.put("teacher_no", "T2024001");
            profile.put("title", "讲师");
        } else {
            profile.put("student_no", "S2024001");
            profile.put("class_name", "23本科计科1班");
        }
    }

    private Map<String, Object> findFallbackUser(String username) {
        return FALLBACK_USERS.stream()
                .filter(item -> username != null && username.equals(item.get("username")))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, Object> demoUser(Long id, String username, String realName, String passwordHash, String roleCode) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("username", username);
        user.put("real_name", realName);
        user.put("password_hash", passwordHash);
        user.put("status", 1);
        user.put("role_code", roleCode);
        return user;
    }

    private static Map<String, Object> demoAccount(String username, String password, String roleLabel, String defaultPath, String description) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("username", username);
        account.put("password", password);
        account.put("roleLabel", roleLabel);
        account.put("defaultPath", defaultPath);
        account.put("description", description);
        return account;
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
