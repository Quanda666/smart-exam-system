package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.system.CreateUserRequest;
import com.smartexam.dto.system.UpdateUserRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.util.PasswordHashUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class UserService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;
    private final TeachingScopeService teachingScopeService;

    public UserService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                       NotificationService notificationService,
                       TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
        this.teachingScopeService = teachingScopeService;
    }

    public PageResult<Map<String, Object>> listUsers(String keyword, String role, Integer status, int page, int size) {
        JdbcTemplate jt = requireJdbcTemplate();
        String kw = blankToNull(keyword);
        String roleCode = blankToNull(role);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                WHERE u.deleted = 0
                  AND (? IS NULL OR u.username LIKE CONCAT('%', ?, '%') OR u.real_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR u.status = ?)
                  AND (? IS NULL OR EXISTS (
                        SELECT 1
                        FROM sys_user_role ur2
                        JOIN sys_role r2 ON r2.id = ur2.role_id
                        WHERE ur2.user_id = u.id AND r2.role_code = ?))
                """, Long.class, kw, kw, kw, status, status, roleCode, roleCode);
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email, u.status,
                       u.created_at AS createdAt, u.updated_at AS updatedAt,
                       (SELECT GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',')
                        FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id) AS roleCodes,
                       sp.student_no AS studentNo, COALESCE(sp.primary_class_id, sp.class_id) AS classId,
                       sp.enrollment_year AS enrollmentYear, sp.college AS studentCollege, sp.major AS studentMajor,
                       c.class_name AS className, c.class_type AS classType,
                       (SELECT GROUP_CONCAT(CONCAT(ec.id, ':', ec.class_name, ':', scm.membership_type) ORDER BY scm.membership_type, ec.id SEPARATOR ',')
                        FROM student_class_membership scm
                        JOIN edu_class ec ON ec.id = scm.class_id AND ec.deleted = 0
                        WHERE scm.student_user_id = u.id AND scm.deleted = 0 AND scm.status = 1) AS classMemberships,
                       tp.teacher_no AS teacherNo, tp.hire_date AS hireDate, tp.title AS teacherTitle,
                       tp.college AS teacherCollege, tp.introduction,
                       (SELECT GROUP_CONCAT(CONCAT(cc.id, ':', ec.class_name, '/', co.course_name, ':', tcc.teacher_role) ORDER BY cc.id SEPARATOR ',')
                        FROM teacher_class_course tcc
                        JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0
                        JOIN edu_class ec ON ec.id = cc.class_id AND ec.deleted = 0
                        JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                        WHERE tcc.teacher_user_id = u.id AND tcc.deleted = 0 AND tcc.status = 1) AS teachingAssignments
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id) AND c.deleted = 0
                LEFT JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                WHERE u.deleted = 0
                  AND (? IS NULL OR u.username LIKE CONCAT('%', ?, '%') OR u.real_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR u.status = ?)
                  AND (? IS NULL OR EXISTS (
                        SELECT 1
                        FROM sys_user_role ur2
                        JOIN sys_role r2 ON r2.id = ur2.role_id
                        WHERE ur2.user_id = u.id AND r2.role_code = ?))
                ORDER BY u.id DESC
                LIMIT ? OFFSET ?
                """, kw, kw, kw, status, status, roleCode, roleCode, safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public Map<String, Object> summary() {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForMap("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS active,
                       COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS disabled
                FROM sys_user
                WHERE deleted = 0
                """);
    }

    public List<Map<String, Object>> listRoles() {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT r.id, r.role_code AS roleCode, r.role_name AS roleName, r.status,
                       (SELECT COUNT(*)
                        FROM sys_user_role ur
                        JOIN sys_user u ON u.id = ur.user_id
                        WHERE ur.role_id = r.id AND u.deleted = 0) AS userCount
                FROM sys_role r
                WHERE r.deleted = 0
                ORDER BY r.id
                """);
    }

    public void updateStatus(Long id, Integer status, Long currentUserId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("User status must be 0 or 1");
        }
        if (Objects.equals(id, currentUserId)) {
            throw new IllegalArgumentException("Cannot change current account status");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE sys_user SET status = ? WHERE id = ? AND deleted = 0", status, id);
        if (rows == 0) {
            throw new IllegalArgumentException("User not found");
        }
        if (status == 1) {
            notificationService.send(id, "Account enabled", "Your account has been enabled.", "APPROVAL", null);
        }
    }

    public void resetPassword(Long id, String newPassword) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE sys_user SET password_hash = ? WHERE id = ? AND deleted = 0",
                PasswordHashUtil.encode(newPassword), id);
        if (rows == 0) {
            throw new IllegalArgumentException("User not found");
        }
    }

    @Transactional
    public void deleteUser(Long id, Long currentUserId) {
        if (Objects.equals(id, currentUserId)) {
            throw new IllegalArgumentException("Cannot delete current account");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE sys_user SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("User not found");
        }
        teachingScopeService.clearStudentScope(id);
        jt.update("UPDATE teacher_class_course SET deleted = 1, status = 0 WHERE teacher_user_id = ? AND deleted = 0", id);
        jt.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
    }

    @Transactional
    public Map<String, Object> createUser(CreateUserRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        String username = trim(request.getUsername());
        String realName = trim(request.getRealName());
        String roleType = request.getRoleType().toUpperCase();

        Integer exists = jt.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0",
                Integer.class, username);
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        Long roleId = findRoleId(roleType);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sys_user (username, password_hash, real_name, phone, email, status)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, PasswordHashUtil.encode(request.getPassword()));
            statement.setString(3, realName);
            statement.setString(4, trim(request.getPhone()));
            statement.setString(5, trim(request.getEmail()));
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Failed to create user");
        }
        Long userId = generatedId.longValue();
        jt.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);

        if ("STUDENT".equals(roleType)) {
            upsertStudentProfile(userId, request.getStudentNo(), request.getClassId(), request.getEnrollmentYear(),
                    request.getCollege(), request.getMajor());
            if (request.getClassId() != null) {
                teachingScopeService.syncStudentPrimaryClass(userId, request.getClassId());
            }
            replaceElectiveMemberships(userId, request.getElectiveClassIds());
        } else if ("TEACHER".equals(roleType)) {
            upsertTeacherProfile(userId, request.getTeacherNo(), request.getHireDate(), request.getTitle(),
                    request.getCollege(), request.getIntroduction());
        }
        return findUserById(userId);
    }

    @Transactional
    public Map<String, Object> updateUser(Long id, UpdateUserRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (!userExists(id)) {
            throw new IllegalArgumentException("User not found");
        }
        String roleType = request.getRoleType().toUpperCase();
        Long roleId = findRoleId(roleType);

        jt.update("""
                UPDATE sys_user
                SET real_name = ?, phone = ?, email = ?
                WHERE id = ? AND deleted = 0
                """, trim(request.getRealName()), trim(request.getPhone()), trim(request.getEmail()), id);
        jt.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        jt.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);

        if ("STUDENT".equals(roleType)) {
            jt.update("DELETE FROM teacher_profile WHERE user_id = ?", id);
            jt.update("UPDATE teacher_class_course SET deleted = 1, status = 0 WHERE teacher_user_id = ? AND deleted = 0", id);
            upsertStudentProfile(id, request.getStudentNo(), request.getClassId(), request.getEnrollmentYear(),
                    request.getCollege(), request.getMajor());
            if (request.getClassId() != null) {
                teachingScopeService.syncStudentPrimaryClass(id, request.getClassId());
            }
            replaceElectiveMemberships(id, request.getElectiveClassIds());
        } else if ("TEACHER".equals(roleType)) {
            teachingScopeService.clearStudentScope(id);
            jt.update("DELETE FROM student_profile WHERE user_id = ?", id);
            upsertTeacherProfile(id, request.getTeacherNo(), request.getHireDate(), request.getTitle(),
                    request.getCollege(), request.getIntroduction());
        } else {
            teachingScopeService.clearStudentScope(id);
            jt.update("DELETE FROM student_profile WHERE user_id = ?", id);
            jt.update("DELETE FROM teacher_profile WHERE user_id = ?", id);
            jt.update("UPDATE teacher_class_course SET deleted = 1, status = 0 WHERE teacher_user_id = ? AND deleted = 0", id);
        }
        return findUserById(id);
    }

    private void upsertStudentProfile(Long userId, String studentNo, Long primaryClassId,
                                      String enrollmentYear, String college, String major) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("""
                UPDATE student_profile
                SET student_no = ?, class_id = ?, primary_class_id = ?, enrollment_year = ?, college = ?, major = ?,
                    status = 1, deleted = 0
                WHERE user_id = ?
                """, trim(studentNo), primaryClassId, primaryClassId, trim(enrollmentYear),
                trim(college), trim(major), userId);
        if (rows == 0) {
            jt.update("""
                    INSERT INTO student_profile
                    (user_id, student_no, class_id, primary_class_id, enrollment_year, college, major, status, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0)
                    """, userId, trim(studentNo), primaryClassId, primaryClassId,
                    trim(enrollmentYear), trim(college), trim(major));
        }
    }

    private void upsertTeacherProfile(Long userId, String teacherNo, java.time.LocalDate hireDate,
                                      String title, String college, String introduction) {
        JdbcTemplate jt = requireJdbcTemplate();
        Date sqlHireDate = hireDate == null ? null : Date.valueOf(hireDate);
        int rows = jt.update("""
                UPDATE teacher_profile
                SET teacher_no = ?, hire_date = ?, title = ?, college = ?, introduction = ?,
                    status = 1, deleted = 0
                WHERE user_id = ?
                """, trim(teacherNo), sqlHireDate, trim(title), trim(college), trim(introduction), userId);
        if (rows == 0) {
            jt.update("""
                    INSERT INTO teacher_profile
                    (user_id, teacher_no, hire_date, title, college, introduction, status, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, 1, 0)
                    """, userId, trim(teacherNo), sqlHireDate, trim(title), trim(college), trim(introduction));
        }
    }

    private void replaceElectiveMemberships(Long studentUserId, List<Long> electiveClassIds) {
        JdbcTemplate jt = requireJdbcTemplate();
        Set<Long> requested = new LinkedHashSet<>();
        if (electiveClassIds != null) {
            for (Long classId : electiveClassIds) {
                if (classId != null) {
                    requested.add(classId);
                }
            }
        }
        List<Long> current = jt.queryForList("""
                SELECT class_id
                FROM student_class_membership
                WHERE student_user_id = ?
                  AND membership_type = 'ELECTIVE'
                  AND deleted = 0
                """, Long.class, studentUserId);
        for (Long classId : current) {
            if (!requested.contains(classId)) {
                jt.update("""
                        UPDATE student_class_membership
                        SET status = 0, deleted = 1, left_at = COALESCE(left_at, NOW())
                        WHERE student_user_id = ? AND class_id = ? AND membership_type = 'ELECTIVE' AND deleted = 0
                        """, studentUserId, classId);
                jt.update("""
                        UPDATE student_course_enrollment sce
                        JOIN class_course cc ON cc.id = sce.class_course_id
                        SET sce.status = 0, sce.deleted = 1, sce.dropped_at = COALESCE(sce.dropped_at, NOW())
                        WHERE sce.student_user_id = ?
                          AND cc.class_id = ?
                          AND sce.enrollment_type = 'ELECTIVE'
                          AND sce.deleted = 0
                        """, studentUserId, classId);
            }
        }
        for (Long classId : requested) {
            jt.update("""
                    INSERT INTO student_class_membership (student_user_id, class_id, membership_type, source, status, deleted)
                    VALUES (?, ?, 'ELECTIVE', 'ADMIN', 1, 0)
                    ON DUPLICATE KEY UPDATE status = 1, deleted = 0, left_at = NULL, updated_at = CURRENT_TIMESTAMP
                    """, studentUserId, classId);
            jt.update("""
                    INSERT INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
                    SELECT ?, cc.id, 'ELECTIVE', 1, 0
                    FROM class_course cc
                    WHERE cc.class_id = ?
                      AND cc.deleted = 0
                      AND cc.status = 1
                    ON DUPLICATE KEY UPDATE status = 1, deleted = 0, dropped_at = NULL, updated_at = CURRENT_TIMESTAMP
                    """, studentUserId, classId);
        }
    }

    private Map<String, Object> findUserById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email,
                       u.status, u.created_at AS createdAt, u.updated_at AS updatedAt,
                       sp.student_no AS studentNo, COALESCE(sp.primary_class_id, sp.class_id) AS classId,
                       sp.enrollment_year AS enrollmentYear, sp.college AS studentCollege, sp.major AS studentMajor,
                       c.class_name AS className, c.class_type AS classType,
                       (SELECT GROUP_CONCAT(CONCAT(ec.id, ':', ec.class_name, ':', scm.membership_type) ORDER BY scm.membership_type, ec.id SEPARATOR ',')
                        FROM student_class_membership scm
                        JOIN edu_class ec ON ec.id = scm.class_id AND ec.deleted = 0
                        WHERE scm.student_user_id = u.id AND scm.deleted = 0 AND scm.status = 1) AS classMemberships,
                       tp.teacher_no AS teacherNo, tp.hire_date AS hireDate, tp.title AS teacherTitle,
                       tp.college AS teacherCollege, tp.introduction,
                       (SELECT GROUP_CONCAT(CONCAT(cc.id, ':', ec.class_name, '/', co.course_name, ':', tcc.teacher_role) ORDER BY cc.id SEPARATOR ',')
                        FROM teacher_class_course tcc
                        JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0
                        JOIN edu_class ec ON ec.id = cc.class_id AND ec.deleted = 0
                        JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                        WHERE tcc.teacher_user_id = u.id AND tcc.deleted = 0 AND tcc.status = 1) AS teachingAssignments,
                       (SELECT GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',')
                        FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id) AS roleCodes
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id) AND c.deleted = 0
                LEFT JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                WHERE u.id = ? AND u.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        return rows.get(0);
    }

    private Long findRoleId(String roleType) {
        List<Map<String, Object>> roles = requireJdbcTemplate().queryForList(
                "SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0", roleType);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Role not found: " + roleType);
        }
        return ((Number) roles.get(0).get("id")).longValue();
    }

    private boolean userExists(Long id) {
        Integer exists = requireJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0", Integer.class, id);
        return exists != null && exists > 0;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
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
