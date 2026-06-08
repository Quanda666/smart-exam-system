package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教学数据域服务。
 *
 * <p>阶段一只提供统一的数据域计算底座，后续用户管理、公告、考试、阅卷、学情等模块
 * 均应通过本服务判断当前用户可访问的班级、班级课程、课程和学生集合。</p>
 */
@Service
public class TeachingScopeService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public TeachingScopeService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 当前用户是否具备全局数据域。 */
    public boolean hasGlobalScope(AuthUser user) {
        return user != null && user.hasRole("ADMIN");
    }

    /** 教师可见的班级课程 ID；管理员返回全量启用班级课程。 */
    public List<Long> visibleClassCourseIds(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (hasGlobalScope(user)) {
            return jt.queryForList("""
                    SELECT id
                    FROM class_course
                    WHERE deleted = 0 AND status = 1
                    ORDER BY id
                    """, Long.class);
        }
        if (user != null && user.hasRole("TEACHER")) {
            return jt.queryForList("""
                    SELECT DISTINCT cc.id
                    FROM teacher_class_course tcc
                    JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    WHERE tcc.teacher_user_id = ?
                      AND tcc.deleted = 0
                      AND tcc.status = 1
                    ORDER BY cc.id
                    """, Long.class, user.getId());
        }
        if (user != null && user.hasRole("STUDENT")) {
            return jt.queryForList("""
                    SELECT DISTINCT cc.id
                    FROM student_course_enrollment sce
                    JOIN class_course cc ON cc.id = sce.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    WHERE sce.student_user_id = ?
                      AND sce.deleted = 0
                      AND sce.status = 1
                    ORDER BY cc.id
                    """, Long.class, user.getId());
        }
        return List.of();
    }

    /** 当前用户可见的班级 ID。教师按授课班级课程，学生按主班级/选修班级，管理员全量。 */
    public List<Long> visibleClassIds(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (hasGlobalScope(user)) {
            return jt.queryForList("""
                    SELECT id
                    FROM edu_class
                    WHERE deleted = 0 AND status = 1
                    ORDER BY id
                    """, Long.class);
        }
        if (user != null && user.hasRole("TEACHER")) {
            return jt.queryForList("""
                    SELECT DISTINCT c.id
                    FROM teacher_class_course tcc
                    JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    JOIN edu_class c ON c.id = cc.class_id AND c.deleted = 0 AND c.status = 1
                    WHERE tcc.teacher_user_id = ?
                      AND tcc.deleted = 0
                      AND tcc.status = 1
                    ORDER BY c.id
                    """, Long.class, user.getId());
        }
        if (user != null && user.hasRole("STUDENT")) {
            return jt.queryForList("""
                    SELECT DISTINCT c.id
                    FROM student_class_membership scm
                    JOIN edu_class c ON c.id = scm.class_id AND c.deleted = 0 AND c.status = 1
                    WHERE scm.student_user_id = ?
                      AND scm.deleted = 0
                      AND scm.status = 1
                    ORDER BY c.id
                    """, Long.class, user.getId());
        }
        return List.of();
    }

    /** 当前用户可见的学生 ID。教师按授课班级课程选课名单，管理员全量，学生仅本人。 */
    public List<Long> visibleStudentUserIds(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (hasGlobalScope(user)) {
            return jt.queryForList("""
                    SELECT DISTINCT sp.user_id
                    FROM student_profile sp
                    JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0 AND u.status = 1
                    WHERE sp.deleted = 0
                    ORDER BY sp.user_id
                    """, Long.class);
        }
        if (user != null && user.hasRole("TEACHER")) {
            return jt.queryForList("""
                    SELECT DISTINCT sce.student_user_id
                    FROM teacher_class_course tcc
                    JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    JOIN student_course_enrollment sce ON sce.class_course_id = cc.id AND sce.deleted = 0 AND sce.status = 1
                    JOIN sys_user u ON u.id = sce.student_user_id AND u.deleted = 0 AND u.status = 1
                    WHERE tcc.teacher_user_id = ?
                      AND tcc.deleted = 0
                      AND tcc.status = 1
                    ORDER BY sce.student_user_id
                    """, Long.class, user.getId());
        }
        if (user != null && user.hasRole("STUDENT")) {
            return List.of(user.getId());
        }
        return List.of();
    }

    /** 当前用户可见的课程 ID。 */
    public List<Long> visibleCourseIds(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (hasGlobalScope(user)) {
            return jt.queryForList("""
                    SELECT id
                    FROM edu_course
                    WHERE deleted = 0 AND status = 1
                    ORDER BY id
                    """, Long.class);
        }
        if (user != null && user.hasRole("TEACHER")) {
            return jt.queryForList("""
                    SELECT DISTINCT co.id
                    FROM teacher_class_course tcc
                    JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0 AND co.status = 1
                    WHERE tcc.teacher_user_id = ?
                      AND tcc.deleted = 0
                      AND tcc.status = 1
                    ORDER BY co.id
                    """, Long.class, user.getId());
        }
        if (user != null && user.hasRole("STUDENT")) {
            return jt.queryForList("""
                    SELECT DISTINCT co.id
                    FROM student_course_enrollment sce
                    JOIN class_course cc ON cc.id = sce.class_course_id AND cc.deleted = 0 AND cc.status = 1
                    JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0 AND co.status = 1
                    WHERE sce.student_user_id = ?
                      AND sce.deleted = 0
                      AND sce.status = 1
                    ORDER BY co.id
                    """, Long.class, user.getId());
        }
        return List.of();
    }

    /** 当前用户可见的班级课程详情，供后续公告、考试、教师端“我的授课”直接复用。 */
    public List<Map<String, Object>> visibleClassCourses(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (hasGlobalScope(user)) {
            return jt.queryForList(baseClassCourseQuery() + " ORDER BY cc.id");
        }
        if (user != null && user.hasRole("TEACHER")) {
            return jt.queryForList(baseClassCourseQuery() + """
                    AND EXISTS (
                        SELECT 1 FROM teacher_class_course tcc
                        WHERE tcc.class_course_id = cc.id
                          AND tcc.teacher_user_id = ?
                          AND tcc.deleted = 0
                          AND tcc.status = 1
                    )
                    ORDER BY cc.id
                    """, user.getId());
        }
        if (user != null && user.hasRole("STUDENT")) {
            return jt.queryForList(baseClassCourseQuery() + """
                    AND EXISTS (
                        SELECT 1 FROM student_course_enrollment sce
                        WHERE sce.class_course_id = cc.id
                          AND sce.student_user_id = ?
                          AND sce.deleted = 0
                          AND sce.status = 1
                    )
                    ORDER BY cc.id
                    """, user.getId());
        }
        return List.of();
    }

    /** 教师是否可访问指定班级课程。管理员始终可访问。 */
    public boolean canAccessClassCourse(AuthUser user, Long classCourseId) {
        if (classCourseId == null) {
            return false;
        }
        if (hasGlobalScope(user)) {
            return true;
        }
        return visibleClassCourseIdSet(user).contains(classCourseId);
    }

    /** 教师是否可访问指定学生。管理员始终可访问；学生只能访问本人。 */
    public boolean canAccessStudent(AuthUser user, Long studentUserId) {
        if (studentUserId == null) {
            return false;
        }
        if (hasGlobalScope(user)) {
            return true;
        }
        if (user != null && user.hasRole("STUDENT")) {
            return studentUserId.equals(user.getId());
        }
        return visibleStudentUserIdSet(user).contains(studentUserId);
    }

    /** 阶段一通用范围摘要，便于调试或后续接口直接返回。 */
    public Map<String, Object> scopeSummary(AuthUser user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("global", hasGlobalScope(user));
        summary.put("classIds", visibleClassIds(user));
        summary.put("classCourseIds", visibleClassCourseIds(user));
        summary.put("courseIds", visibleCourseIds(user));
        summary.put("studentUserIds", visibleStudentUserIds(user));
        return summary;
    }

    /**
     * 同步学生主班级归属与由主班级派生的默认选课关系。
     *
     * <p>用于注册、管理员创建学生、管理员修改学生主班级等写路径。该方法只维护
     * {@code enrollment_type = CLASS} 的主班级同步选课，不影响后续选修课关系。</p>
     */
    @Transactional
    public void syncStudentPrimaryClass(Long studentUserId, Long primaryClassId) {
        if (studentUserId == null || primaryClassId == null) {
            return;
        }
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                INSERT INTO student_class_membership (student_user_id, class_id, membership_type, source, status, deleted)
                VALUES (?, ?, 'PRIMARY', 'SYSTEM', 1, 0)
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, left_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, studentUserId, primaryClassId);
        jt.update("""
                UPDATE student_class_membership
                SET status = 0, left_at = COALESCE(left_at, NOW()), updated_at = CURRENT_TIMESTAMP
                WHERE student_user_id = ?
                  AND membership_type = 'PRIMARY'
                  AND class_id <> ?
                  AND deleted = 0
                """, studentUserId, primaryClassId);
        jt.update("""
                INSERT INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
                SELECT ?, cc.id, 'CLASS', 1, 0
                FROM class_course cc
                WHERE cc.class_id = ?
                  AND cc.deleted = 0
                  AND cc.status = 1
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, dropped_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, studentUserId, primaryClassId);
        jt.update("""
                UPDATE student_course_enrollment sce
                JOIN class_course cc ON cc.id = sce.class_course_id
                SET sce.status = 0,
                    sce.dropped_at = COALESCE(sce.dropped_at, NOW()),
                    sce.updated_at = CURRENT_TIMESTAMP
                WHERE sce.student_user_id = ?
                  AND sce.enrollment_type = 'CLASS'
                  AND cc.class_id <> ?
                  AND sce.deleted = 0
                """, studentUserId, primaryClassId);
    }

    /** 用户转出学生角色或删除用户时，停用其教学数据域关系，历史考试成绩不受影响。 */
    @Transactional
    public void clearStudentScope(Long studentUserId) {
        if (studentUserId == null) {
            return;
        }
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                UPDATE student_class_membership
                SET status = 0, deleted = 1, left_at = COALESCE(left_at, NOW()), updated_at = CURRENT_TIMESTAMP
                WHERE student_user_id = ? AND deleted = 0
                """, studentUserId);
        jt.update("""
                UPDATE student_course_enrollment
                SET status = 0, deleted = 1, dropped_at = COALESCE(dropped_at, NOW()), updated_at = CURRENT_TIMESTAMP
                WHERE student_user_id = ? AND deleted = 0
                """, studentUserId);
    }

    private Set<Long> visibleClassCourseIdSet(AuthUser user) {
        return new LinkedHashSet<>(visibleClassCourseIds(user));
    }

    private Set<Long> visibleStudentUserIdSet(AuthUser user) {
        return new LinkedHashSet<>(visibleStudentUserIds(user));
    }

    private String baseClassCourseQuery() {
        return """
                SELECT cc.id AS classCourseId, cc.class_id AS classId, c.class_name AS className,
                       c.class_code AS classCode, c.class_type AS classType, c.major, c.grade,
                       cc.course_id AS courseId, co.course_code AS courseCode, co.course_name AS courseName,
                       co.subject_id AS subjectId, s.subject_name AS subjectName,
                       cc.term_name AS termName, cc.status
                FROM class_course cc
                JOIN edu_class c ON c.id = cc.class_id AND c.deleted = 0
                JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                LEFT JOIN edu_subject s ON s.id = co.subject_id AND s.deleted = 0
                WHERE cc.deleted = 0 AND cc.status = 1
                """;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
