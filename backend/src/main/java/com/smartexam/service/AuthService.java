package com.smartexam.service;

import com.smartexam.auth.LoginAttemptGuard;
import com.smartexam.auth.TokenSession;
import com.smartexam.auth.TokenStore;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.dto.auth.RegisterRequest;
import com.smartexam.util.PasswordHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TokenStore tokenStore;
    private final MenuService menuService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final EmailServiceV3 emailService;
    private final TeachingScopeService teachingScopeService;

    public AuthService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, TokenStore tokenStore, MenuService menuService,
                       LoginAttemptGuard loginAttemptGuard, EmailServiceV3 emailService,
                       TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.tokenStore = tokenStore;
        this.menuService = menuService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.emailService = emailService;
        this.teachingScopeService = teachingScopeService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        loginAttemptGuard.assertNotLocked(username);

        Map<String, Object> userRow = findUserRow(username);
        if (userRow == null) {
            loginAttemptGuard.recordFailure(username);
            throw new IllegalArgumentException("账号或密码错误");
        }

        String passwordHash = stringValue(userRow.get("password_hash"));
        if (!PasswordHashUtil.matches(request.getPassword(), passwordHash)) {
            loginAttemptGuard.recordFailure(username);
            throw new IllegalArgumentException("账号或密码错误");
        }

        upgradePasswordHashIfNeeded(longValue(userRow.get("id")), request.getPassword(), passwordHash);
        loginAttemptGuard.recordSuccess(username);
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
        int initialStatus = "STUDENT".equals(roleType) ? 1 : 0; // 教师账号需管理员审核后启用

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sys_user (username, password_hash, real_name, phone, email, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, PasswordHashUtil.encode(request.getPassword()));
            statement.setString(3, realName);
            statement.setString(4, blankToNull(request.getPhone()));
            statement.setString(5, blankToNull(request.getEmail()));
            statement.setInt(6, initialStatus);
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
                    INSERT INTO student_profile (user_id, student_no, class_id, primary_class_id, status)
                    VALUES (?, ?, ?, ?, 1)
                    """, userId, trim(request.getStudentNo()), request.getClassId(), request.getClassId());
            teachingScopeService.syncStudentPrimaryClass(userId, request.getClassId());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO teacher_profile (user_id, teacher_no, title, introduction, status)
                    VALUES (?, ?, ?, ?, 1)
                    """, userId, trim(request.getTeacherNo()), blankToNull(request.getTitle()), blankToNull(request.getIntroduction()));
        }

        if ("STUDENT".equals(roleType)) {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername(username);
            loginRequest.setPassword(request.getPassword());
            return login(loginRequest);
        }
        // 教师账号需管理员审核启用后才能登录，此处不自动登录，返回空响应（token 为 null）
        return new LoginResponse();
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

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT password_hash FROM sys_user WHERE id = ? AND deleted = 0", userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        String storedHash = (String) rows.get(0).get("password_hash");
        if (!PasswordHashUtil.matches(oldPassword, storedHash)) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ? WHERE id = ?",
                PasswordHashUtil.encode(newPassword), userId);
    }

    // ===== 邮箱验证码相关 =====

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int DAILY_SEND_LIMIT = 5;
    private static final int RESEND_SECONDS = 60;

    /** 发送登录验证码（无需登录态，但需邮箱已绑定） */
    public void sendLoginCode(String email) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        // 检查邮箱是否已绑定
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = ? AND email_verified = 1 AND deleted = 0",
                Integer.class, email);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("该邮箱未绑定任何账号");
        }
        sendCode(jdbcTemplate, email, "LOGIN");
    }

    /** 发送绑定邮箱验证码（需登录态） */
    public void sendBindCode(String email, Long currentUserId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        // 检查邮箱是否已被其他人绑定
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = ? AND email_verified = 1 AND id != ? AND deleted = 0",
                Integer.class, email, currentUserId);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("该邮箱已被其他账号绑定");
        }
        sendCode(jdbcTemplate, email, "BIND");
    }

    private void sendCode(JdbcTemplate jdbcTemplate, String email, String purpose) {
        // 频率限制：60秒内不可重复发送
        Integer recent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification WHERE email = ? AND purpose = ? AND created_at > DATE_SUB(NOW(), INTERVAL ? SECOND)",
                Integer.class, email, purpose, RESEND_SECONDS);
        if (recent != null && recent > 0) {
            throw new IllegalArgumentException("验证码已发送，请" + RESEND_SECONDS + "秒后再试");
        }

        // 每日上限检查
        Integer todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification WHERE email = ? AND purpose = ? AND created_at > CURDATE()",
                Integer.class, email, purpose);
        if (todayCount != null && todayCount >= DAILY_SEND_LIMIT) {
            throw new IllegalArgumentException("今日发送次数已达上限（" + DAILY_SEND_LIMIT + "次），请明天再试");
        }

        // 生成6位验证码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES);

        // 保存验证码
        jdbcTemplate.update(
                "INSERT INTO email_verification (email, code, purpose, expires_at) VALUES (?, ?, ?, ?)",
                email, code, purpose, expiresAt.toString().replace('T', ' ').substring(0, 19));

        // 异步发送邮件（不阻塞 HTTP 请求，避免 SMTP 超时导致 502）
        if (emailService.isConfigured()) {
            log.info("验证码已存入数据库，准备异步发送邮件至: {}", email);
            emailService.sendVerificationCodeAsync(email, code);
            log.info("异步邮件任务已提交");
        } else {
            log.warn("邮件服务未配置，验证码已生成但仅记录在数据库: {} -> {}", email, code);
        }
    }

    /** 验证码登录 */
    public LoginResponse loginByCode(String email, String code) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        verifyCode(jdbcTemplate, email, code, "LOGIN");

        // 查找用户
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, real_name, status FROM sys_user WHERE email = ? AND email_verified = 1 AND deleted = 0",
                email);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("邮箱未绑定任何账号");
        }
        Map<String, Object> userRow = rows.get(0);
        if (intValue(userRow.get("status")) == 0) {
            throw new IllegalArgumentException("账号已被禁用，请联系管理员");
        }

        AuthUser authUser = buildAuthUser(userRow);
        TokenSession session = tokenStore.create(authUser);
        List<MenuItem> menus = menuService.menusFor(authUser);
        return new LoginResponse(session.getToken(), session.getExpiresAt(), authUser, menus, authUser.getDefaultPath());
    }

    /** 绑定/更换邮箱 */
    public void bindEmail(Long userId, String email, String code) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        verifyCode(jdbcTemplate, email, code, "BIND");

        jdbcTemplate.update(
                "UPDATE sys_user SET email = ?, email_verified = 1 WHERE id = ? AND deleted = 0",
                email, userId);
    }

    /** 更新个人资料 */
    public void updateProfile(Long userId, String realName, String phone) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update(
                "UPDATE sys_user SET real_name = ?, phone = ? WHERE id = ? AND deleted = 0",
                realName, blankToNull(phone), userId);
    }

    /** 查询当前用户最近的登录记录（密码登录 + 验证码登录，二者 target 均为「认证」） */
    public List<Map<String, Object>> listLoginLogs(Long userId, int limit) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return jdbcTemplate.queryForList(
                "SELECT action, ip, detail, created_at FROM operation_log "
                        + "WHERE operator_id = ? AND target = '认证' ORDER BY created_at DESC LIMIT ?",
                userId, safeLimit);
    }

    private void verifyCode(JdbcTemplate jdbcTemplate, String email, String code, String purpose) {
        List<Map<String, Object>> codes = jdbcTemplate.queryForList(
                "SELECT code, used, expires_at FROM email_verification WHERE email = ? AND purpose = ? ORDER BY created_at DESC LIMIT 1",
                email, purpose);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("请先发送验证码");
        }
        Map<String, Object> row = codes.get(0);
        if (intValue(row.get("used")) == 1) {
            throw new IllegalArgumentException("验证码已使用");
        }
        String storedCode = (String) row.get("code");
        if (!storedCode.equals(code)) {
            throw new IllegalArgumentException("验证码不正确");
        }
        // 检查过期
        Object expiresAt = row.get("expires_at");
        if (expiresAt != null) {
            LocalDateTime expireTime = expiresAt instanceof LocalDateTime ? (LocalDateTime) expiresAt
                    : LocalDateTime.parse(expiresAt.toString().replace('T', ' ').substring(0, 19));
            if (LocalDateTime.now().isAfter(expireTime)) {
                throw new IllegalArgumentException("验证码已过期，请重新发送");
            }
        }
        // 标记已使用
        jdbcTemplate.update("UPDATE email_verification SET used = 1 WHERE email = ? AND code = ? AND purpose = ?",
                email, code, purpose);
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

    private void upgradePasswordHashIfNeeded(Long userId, String rawPassword, String storedHash) {
        if (userId == null || !PasswordHashUtil.needsRehash(storedHash)) {
            return;
        }
        try {
            requireJdbcTemplate().update(
                    "UPDATE sys_user SET password_hash = ? WHERE id = ?",
                    PasswordHashUtil.encode(rawPassword), userId);
        } catch (Exception ex) {
            log.warn("密码哈希自动升级失败，用户ID={}，不影响本次登录：{}", userId, ex.getMessage());
        }
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

        // 通用账户信息：邮箱、手机号(avatar 因存 base64 可达几百 KB,频繁 SELECT 会拖垮数据库/内存,改为按需独立接口)
        List<Map<String, Object>> baseRows = jdbcTemplate.queryForList("""
                SELECT email, email_verified, phone
                FROM sys_user
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, userId);
        if (!baseRows.isEmpty()) {
            Map<String, Object> base = baseRows.get(0);
            if (base.get("email") != null) {
                profile.put("email", base.get("email"));
            }
            if (base.get("email_verified") != null) {
                profile.put("emailVerified", intValue(base.get("email_verified")) == 1);
            }
            if (base.get("phone") != null) {
                profile.put("phone", base.get("phone"));
            }
        }

        if (roles.stream().anyMatch("STUDENT"::equalsIgnoreCase)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT sp.student_no, COALESCE(sp.primary_class_id, sp.class_id) AS class_id,
                           c.class_name, c.class_type, c.major, c.grade,
                           sp.enrollment_year, sp.college
                    FROM student_profile sp
                    LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id)
                    WHERE sp.user_id = ? AND sp.deleted = 0
                    LIMIT 1
                    """, userId);
            if (!rows.isEmpty()) {
                profile.putAll(rows.get(0));
            }
        }
        if (roles.stream().anyMatch("TEACHER"::equalsIgnoreCase)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT teacher_no, hire_date, title, college, introduction
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
