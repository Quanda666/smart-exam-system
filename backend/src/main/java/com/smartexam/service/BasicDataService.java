package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.basic.ClassCourseRequest;
import com.smartexam.dto.basic.ClassInfoRequest;
import com.smartexam.dto.basic.CourseRequest;
import com.smartexam.dto.basic.KnowledgePointRequest;
import com.smartexam.dto.basic.NoticeRequest;
import com.smartexam.dto.basic.NoticeTargetRequest;
import com.smartexam.dto.basic.StudentClassMembershipRequest;
import com.smartexam.dto.basic.SubjectRequest;
import com.smartexam.dto.basic.TeachingAssignmentRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BasicDataService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;
    private final TeachingScopeService teachingScopeService;

    public BasicDataService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                            NotificationService notificationService,
                            TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
        this.teachingScopeService = teachingScopeService;
    }

    public List<Map<String, Object>> listClasses(String keyword, Integer status) {
        return listClasses(keyword, status, null);
    }

    public List<Map<String, Object>> listClasses(String keyword, Integer status, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT id, class_name AS className, class_code AS classCode, class_type AS classType,
                       major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_class
                WHERE deleted = 0
                  AND (? IS NULL OR class_name LIKE CONCAT('%', ?, '%')
                       OR class_code LIKE CONCAT('%', ?, '%')
                       OR major LIKE CONCAT('%', ?, '%')
                       OR grade LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR status = ?)
                """);
        List<Object> params = new ArrayList<>();
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(status);
        params.add(status);
        if (user != null && !teachingScopeService.hasGlobalScope(user)) {
            List<Long> ids = teachingScopeService.visibleClassIds(user);
            if (ids.isEmpty()) {
                return List.of();
            }
            appendIn(sql, params, "id", ids);
        }
        sql.append(" ORDER BY id DESC");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> createClass(ClassInfoRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO edu_class (class_name, class_code, class_type, major, grade, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, trim(request.getClassName()), blankToNull(request.getClassCode()),
                    upperOrDefault(request.getClassType(), "MAJOR"), trim(request.getMajor()),
                    trim(request.getGrade()), request.getStatus());
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getClassById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Class name or code already exists");
        }
    }

    public Map<String, Object> updateClass(Long id, ClassInfoRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            int rows = jt.update("""
                    UPDATE edu_class
                    SET class_name = ?, class_code = ?, class_type = ?, major = ?, grade = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getClassName()), blankToNull(request.getClassCode()),
                    upperOrDefault(request.getClassType(), "MAJOR"), trim(request.getMajor()),
                    trim(request.getGrade()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Class not found");
            }
            return getClassById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Class name or code already exists");
        }
    }

    public Map<String, Object> deleteClass(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE edu_class SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Class not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listCourses(String keyword, Integer status, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT co.id, co.course_code AS courseCode, co.course_name AS courseName,
                       co.subject_id AS subjectId, s.subject_name AS subjectName, co.credit,
                       co.description, co.status, co.created_at AS createdAt, co.updated_at AS updatedAt
                FROM edu_course co
                LEFT JOIN edu_subject s ON s.id = co.subject_id AND s.deleted = 0
                WHERE co.deleted = 0
                  AND (? IS NULL OR co.course_code LIKE CONCAT('%', ?, '%')
                       OR co.course_name LIKE CONCAT('%', ?, '%')
                       OR s.subject_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR co.status = ?)
                """);
        List<Object> params = new ArrayList<>();
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(blankToNull(keyword));
        params.add(status);
        params.add(status);
        if (user != null && !teachingScopeService.hasGlobalScope(user)) {
            List<Long> ids = teachingScopeService.visibleCourseIds(user);
            if (ids.isEmpty()) {
                return List.of();
            }
            appendIn(sql, params, "co.id", ids);
        }
        sql.append(" ORDER BY co.id DESC");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> createCourse(CourseRequest request) {
        if (request.getSubjectId() != null) {
            validateSubjectExists(request.getSubjectId());
        }
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO edu_course (course_code, course_name, subject_id, credit, description, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, trim(request.getCourseCode()), trim(request.getCourseName()), request.getSubjectId(),
                    request.getCredit(), trim(request.getDescription()), request.getStatus());
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getCourseById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Course code already exists");
        }
    }

    public Map<String, Object> updateCourse(Long id, CourseRequest request) {
        if (request.getSubjectId() != null) {
            validateSubjectExists(request.getSubjectId());
        }
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            int rows = jt.update("""
                    UPDATE edu_course
                    SET course_code = ?, course_name = ?, subject_id = ?, credit = ?, description = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getCourseCode()), trim(request.getCourseName()), request.getSubjectId(),
                    request.getCredit(), trim(request.getDescription()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Course not found");
            }
            return getCourseById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Course code already exists");
        }
    }

    public Map<String, Object> deleteCourse(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE edu_course SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Course not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listClassCourses(String keyword, Integer status, AuthUser user) {
        List<Map<String, Object>> rows = new ArrayList<>(teachingScopeService.visibleClassCourses(user));
        String kw = blankToNull(keyword);
        return rows.stream()
                .filter(row -> status == null || numberValue(row.get("status")) == status.longValue())
                .filter(row -> kw == null
                        || contains(row.get("className"), kw)
                        || contains(row.get("classCode"), kw)
                        || contains(row.get("courseName"), kw)
                        || contains(row.get("courseCode"), kw)
                        || contains(row.get("termName"), kw))
                .toList();
    }

    @Transactional
    public Map<String, Object> createClassCourse(ClassCourseRequest request) {
        validateClassExists(request.getClassId());
        validateCourseExists(request.getCourseId());
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO class_course (class_id, course_id, term_name, status)
                    VALUES (?, ?, ?, ?)
                    """, request.getClassId(), request.getCourseId(), trim(request.getTermName()), request.getStatus());
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            syncClassMembersToClassCourse(id, request.getClassId());
            return getClassCourseById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Class course already exists for this term");
        }
    }

    @Transactional
    public Map<String, Object> updateClassCourse(Long id, ClassCourseRequest request) {
        validateClassExists(request.getClassId());
        validateCourseExists(request.getCourseId());
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            int rows = jt.update("""
                    UPDATE class_course
                    SET class_id = ?, course_id = ?, term_name = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, request.getClassId(), request.getCourseId(), trim(request.getTermName()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Class course not found");
            }
            if (request.getStatus() != null && request.getStatus() == 1) {
                syncClassMembersToClassCourse(id, request.getClassId());
            }
            return getClassCourseById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Class course already exists for this term");
        }
    }

    public Map<String, Object> deleteClassCourse(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE class_course SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Class course not found");
        }
        jt.update("""
                UPDATE teacher_class_course SET deleted = 1, status = 0 WHERE class_course_id = ? AND deleted = 0
                """, id);
        jt.update("""
                UPDATE student_course_enrollment
                SET deleted = 1, status = 0, dropped_at = COALESCE(dropped_at, NOW())
                WHERE class_course_id = ? AND deleted = 0
                """, id);
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listTeachingAssignments(Long teacherUserId, Long classCourseId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT tcc.id, tcc.teacher_user_id AS teacherUserId, u.real_name AS teacherName,
                       tp.teacher_no AS teacherNo, tp.title AS teacherTitle,
                       tcc.class_course_id AS classCourseId, c.id AS classId, c.class_name AS className,
                       co.id AS courseId, co.course_name AS courseName, cc.term_name AS termName,
                       tcc.teacher_role AS teacherRole, tcc.status, tcc.created_at AS createdAt
                FROM teacher_class_course tcc
                JOIN sys_user u ON u.id = tcc.teacher_user_id AND u.deleted = 0
                LEFT JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                JOIN class_course cc ON cc.id = tcc.class_course_id AND cc.deleted = 0
                JOIN edu_class c ON c.id = cc.class_id AND c.deleted = 0
                JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                WHERE tcc.deleted = 0
                  AND (? IS NULL OR tcc.teacher_user_id = ?)
                  AND (? IS NULL OR tcc.class_course_id = ?)
                """);
        List<Object> params = new ArrayList<>();
        params.add(teacherUserId);
        params.add(teacherUserId);
        params.add(classCourseId);
        params.add(classCourseId);
        if (user != null && !teachingScopeService.hasGlobalScope(user)) {
            if (!user.hasRole("TEACHER")) {
                return List.of();
            }
            sql.append(" AND tcc.teacher_user_id = ?");
            params.add(user.getId());
        }
        sql.append(" ORDER BY tcc.id DESC");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> createTeachingAssignment(TeachingAssignmentRequest request) {
        validateTeacherExists(request.getTeacherUserId());
        validateClassCourseExists(request.getClassCourseId());
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO teacher_class_course (teacher_user_id, class_course_id, teacher_role, status, deleted)
                    VALUES (?, ?, ?, 1, 0)
                    ON DUPLICATE KEY UPDATE status = 1, deleted = 0, updated_at = CURRENT_TIMESTAMP
                    """, request.getTeacherUserId(), request.getClassCourseId(), upperOrDefault(request.getTeacherRole(), "LECTURER"));
            Long id = jt.queryForObject("""
                    SELECT id FROM teacher_class_course
                    WHERE teacher_user_id = ? AND class_course_id = ? AND teacher_role = ?
                    """, Long.class, request.getTeacherUserId(), request.getClassCourseId(),
                    upperOrDefault(request.getTeacherRole(), "LECTURER"));
            return getTeachingAssignmentById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Teaching assignment already exists");
        }
    }

    public Map<String, Object> deleteTeachingAssignment(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE teacher_class_course SET deleted = 1, status = 0 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Teaching assignment not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listStudentMemberships(Long studentUserId, Long classId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT scm.id, scm.student_user_id AS studentUserId, u.real_name AS studentName,
                       sp.student_no AS studentNo, scm.class_id AS classId, c.class_name AS className,
                       c.class_type AS classType, scm.membership_type AS membershipType,
                       scm.source, scm.status, scm.joined_at AS joinedAt, scm.left_at AS leftAt
                FROM student_class_membership scm
                JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                JOIN edu_class c ON c.id = scm.class_id AND c.deleted = 0
                WHERE scm.deleted = 0
                  AND (? IS NULL OR scm.student_user_id = ?)
                  AND (? IS NULL OR scm.class_id = ?)
                """);
        List<Object> params = new ArrayList<>();
        params.add(studentUserId);
        params.add(studentUserId);
        params.add(classId);
        params.add(classId);
        if (user != null && !teachingScopeService.hasGlobalScope(user)) {
            List<Long> visibleStudents = teachingScopeService.visibleStudentUserIds(user);
            if (visibleStudents.isEmpty()) {
                return List.of();
            }
            appendIn(sql, params, "scm.student_user_id", visibleStudents);
        }
        sql.append(" ORDER BY scm.student_user_id, scm.membership_type, scm.id");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    @Transactional
    public Map<String, Object> createStudentMembership(StudentClassMembershipRequest request) {
        validateStudentExists(request.getStudentUserId());
        validateClassExists(request.getClassId());
        String membershipType = upperOrDefault(request.getMembershipType(), "ELECTIVE");
        if ("PRIMARY".equals(membershipType)) {
            JdbcTemplate jt = requireJdbcTemplate();
            jt.update("""
                    UPDATE student_profile
                    SET class_id = ?, primary_class_id = ?
                    WHERE user_id = ? AND deleted = 0
                    """, request.getClassId(), request.getClassId(), request.getStudentUserId());
            teachingScopeService.syncStudentPrimaryClass(request.getStudentUserId(), request.getClassId());
            return activeMembershipByNaturalKey(request.getStudentUserId(), request.getClassId(), "PRIMARY");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                INSERT INTO student_class_membership (student_user_id, class_id, membership_type, source, status, deleted)
                VALUES (?, ?, ?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, left_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, request.getStudentUserId(), request.getClassId(), membershipType, upperOrDefault(request.getSource(), "ADMIN"));
        syncClassMembersToClassCoursesForStudent(request.getStudentUserId(), request.getClassId(), membershipType);
        return activeMembershipByNaturalKey(request.getStudentUserId(), request.getClassId(), membershipType);
    }

    @Transactional
    public Map<String, Object> deleteStudentMembership(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT student_user_id, class_id, membership_type
                FROM student_class_membership
                WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Student membership not found");
        }
        Map<String, Object> row = rows.get(0);
        Long studentUserId = numberValue(row.get("student_user_id"));
        Long classId = numberValue(row.get("class_id"));
        String membershipType = String.valueOf(row.get("membership_type"));
        jt.update("""
                UPDATE student_class_membership
                SET deleted = 1, status = 0, left_at = COALESCE(left_at, NOW())
                WHERE id = ? AND deleted = 0
                """, id);
        if (!"PRIMARY".equalsIgnoreCase(membershipType)) {
            jt.update("""
                    UPDATE student_course_enrollment sce
                    JOIN class_course cc ON cc.id = sce.class_course_id
                    SET sce.status = 0, sce.deleted = 1, sce.dropped_at = COALESCE(sce.dropped_at, NOW())
                    WHERE sce.student_user_id = ?
                      AND cc.class_id = ?
                      AND sce.enrollment_type IN ('ELECTIVE', 'TEMPORARY')
                      AND sce.deleted = 0
                    """, studentUserId, classId);
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listSubjects(String keyword, Integer status) {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_subject
                WHERE deleted = 0
                  AND (? IS NULL OR subject_name LIKE CONCAT('%', ?, '%') OR description LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR status = ?)
                ORDER BY id DESC
                """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createSubject(SubjectRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO edu_subject (subject_name, description, status)
                    VALUES (?, ?, ?)
                    """, trim(request.getSubjectName()), trim(request.getDescription()), request.getStatus());
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getSubjectById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Subject name already exists");
        }
    }

    public Map<String, Object> updateSubject(Long id, SubjectRequest request) {
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            int rows = jt.update("""
                    UPDATE edu_subject
                    SET subject_name = ?, description = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getSubjectName()), trim(request.getDescription()), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Subject not found");
            }
            return getSubjectById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Subject name already exists");
        }
    }

    public Map<String, Object> deleteSubject(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE edu_subject SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Subject not found");
        }
        jt.update("UPDATE edu_knowledge_point SET deleted = 1 WHERE subject_id = ? AND deleted = 0", id);
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listKnowledgePoints(Long subjectId, String keyword, Integer status) {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT kp.id, kp.subject_id AS subjectId, s.subject_name AS subjectName, kp.parent_id AS parentId,
                       kp.point_name AS pointName, kp.sort_order AS sortOrder, kp.status,
                       kp.created_at AS createdAt, kp.updated_at AS updatedAt
                FROM edu_knowledge_point kp
                JOIN edu_subject s ON s.id = kp.subject_id
                WHERE kp.deleted = 0
                  AND (? IS NULL OR kp.subject_id = ?)
                  AND (? IS NULL OR kp.point_name LIKE CONCAT('%', ?, '%') OR s.subject_name LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR kp.status = ?)
                ORDER BY kp.subject_id, kp.sort_order, kp.id
                """, subjectId, subjectId, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
    }

    public Map<String, Object> createKnowledgePoint(KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            jt.update("""
                    INSERT INTO edu_knowledge_point (subject_id, parent_id, point_name, sort_order, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, request.getSubjectId(), request.getParentId(), trim(request.getPointName()), request.getSortOrder(), request.getStatus());
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return getKnowledgePointById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Knowledge point already exists in this subject");
        }
    }

    public Map<String, Object> updateKnowledgePoint(Long id, KnowledgePointRequest request) {
        validateSubjectExists(request.getSubjectId());
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            int rows = jt.update("""
                    UPDATE edu_knowledge_point
                    SET subject_id = ?, parent_id = ?, point_name = ?, sort_order = ?, status = ?
                    WHERE id = ? AND deleted = 0
                    """, request.getSubjectId(), request.getParentId(), trim(request.getPointName()), request.getSortOrder(), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Knowledge point not found");
            }
            return getKnowledgePointById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Knowledge point already exists in this subject");
        }
    }

    public Map<String, Object> deleteKnowledgePoint(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        int rows = jt.update("UPDATE edu_knowledge_point SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Knowledge point not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    public List<Map<String, Object>> listNotices(String keyword, Integer status, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT n.id, n.title, n.content, n.status, n.publish_time AS publishTime,
                       n.publisher_id AS publisherId, u.real_name AS publisherName,
                       n.created_at AS createdAt, n.updated_at AS updatedAt
                FROM notice n
                LEFT JOIN sys_user u ON u.id = n.publisher_id
                WHERE n.deleted = 0
                  AND (? IS NULL OR n.title LIKE CONCAT('%', ?, '%') OR n.content LIKE CONCAT('%', ?, '%'))
                  AND (? IS NULL OR n.status = ?)
                ORDER BY n.id DESC
                """, blankToNull(keyword), blankToNull(keyword), blankToNull(keyword), status, status);
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Map<String, Object>> targets = getNoticeTargets(numberValue(row.get("id")));
            if (canSeeNotice(row, targets, user)) {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                copy.put("targets", targets);
                copy.put("targetSummary", targetSummary(targets));
                visible.add(copy);
            }
        }
        return visible;
    }

    @Transactional
    public Map<String, Object> createNotice(NoticeRequest request, AuthUser publisher) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<TargetSpec> targets = normalizeNoticeTargets(request, publisher);
        try {
            jt.update("""
                    INSERT INTO notice (title, content, publisher_id, status, publish_time)
                    VALUES (?, ?, ?, ?, ?)
                    """, trim(request.getTitle()), trim(request.getContent()), publisher.getId(), request.getStatus(),
                    request.getStatus() != null && request.getStatus() == 1 ? LocalDateTime.now() : null);
            Long id = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            replaceNoticeTargets(id, targets);
            if (request.getStatus() != null && request.getStatus() == 1) {
                pushNoticeNotification(jt, id, request, targets);
            }
            return getNoticeById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Notice title already exists");
        }
    }

    @Transactional
    public Map<String, Object> updateNotice(Long id, NoticeRequest request, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        requireNoticeOwner(jt, id, user);
        List<TargetSpec> targets = normalizeNoticeTargets(request, user);
        try {
            int rows = jt.update("""
                    UPDATE notice
                    SET title = ?, content = ?, status = ?, publish_time = CASE WHEN ? = 1 THEN COALESCE(publish_time, NOW()) ELSE publish_time END
                    WHERE id = ? AND deleted = 0
                    """, trim(request.getTitle()), trim(request.getContent()), request.getStatus(), request.getStatus(), id);
            if (rows == 0) {
                throw new IllegalArgumentException("Notice not found");
            }
            replaceNoticeTargets(id, targets);
            if (request.getStatus() != null && request.getStatus() == 1) {
                pushNoticeNotification(jt, id, request, targets);
            }
            return getNoticeById(id);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Notice title already exists");
        }
    }

    public Map<String, Object> deleteNotice(Long id, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        requireNoticeOwner(jt, id, user);
        int rows = jt.update("UPDATE notice SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Notice not found");
        }
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> summary(AuthUser user) {
        return Map.of(
                "classes", listClasses(null, null, user).size(),
                "courses", listCourses(null, null, user).size(),
                "classCourses", listClassCourses(null, null, user).size(),
                "subjects", listSubjects(null, null).size(),
                "knowledgePoints", listKnowledgePoints(null, null, null).size(),
                "notices", listNotices(null, null, user).size()
        );
    }

    private void replaceNoticeTargets(Long noticeId, List<TargetSpec> targets) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("DELETE FROM notice_target WHERE notice_id = ?", noticeId);
        for (TargetSpec target : targets) {
            jt.update("""
                    INSERT INTO notice_target (notice_id, target_type, target_id, target_code)
                    VALUES (?, ?, ?, ?)
                    """, noticeId, target.targetType, target.targetId, target.targetCode);
        }
    }

    private List<TargetSpec> normalizeNoticeTargets(NoticeRequest request, AuthUser publisher) {
        List<NoticeTargetRequest> requestTargets = request.getTargets();
        if (requestTargets == null || requestTargets.isEmpty()) {
            if (publisher != null && publisher.hasRole("ADMIN")) {
                return List.of(new TargetSpec("SYSTEM", 0L, ""));
            }
            if (publisher != null && publisher.hasRole("TEACHER")) {
                List<Long> classCourseIds = teachingScopeService.visibleClassCourseIds(publisher);
                if (classCourseIds.isEmpty()) {
                    throw new IllegalArgumentException("Teacher has no teaching class courses to target");
                }
                return classCourseIds.stream()
                        .map(id -> new TargetSpec("CLASS_COURSE", id, ""))
                        .toList();
            }
        }
        List<TargetSpec> targets = new ArrayList<>();
        for (NoticeTargetRequest item : requestTargets) {
            TargetSpec target = normalizeTarget(item.getTargetType(), item.getTargetId(), item.getTargetCode());
            validateNoticeTarget(target, publisher);
            targets.add(target);
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("At least one notice target is required");
        }
        return dedupeTargets(targets);
    }

    private void validateNoticeTarget(TargetSpec target, AuthUser publisher) {
        switch (target.targetType) {
            case "SYSTEM" -> {
                if (publisher == null || !publisher.hasRole("ADMIN")) {
                    throw new IllegalArgumentException("Only admins can publish system notices");
                }
            }
            case "ROLE" -> {
                if (publisher == null || !publisher.hasRole("ADMIN")) {
                    throw new IllegalArgumentException("Only admins can publish role notices");
                }
                if (!Set.of("ADMIN", "TEACHER", "STUDENT").contains(target.targetCode)) {
                    throw new IllegalArgumentException("Invalid role notice target");
                }
            }
            case "CLASS" -> {
                if (target.targetId <= 0) {
                    throw new IllegalArgumentException("Class target id is required");
                }
                if (publisher != null && publisher.hasRole("TEACHER")
                        && !teachingScopeService.visibleClassIds(publisher).contains(target.targetId)) {
                    throw new IllegalArgumentException("Teacher cannot target a class outside teaching scope");
                }
            }
            case "CLASS_COURSE" -> {
                if (target.targetId <= 0) {
                    throw new IllegalArgumentException("Class course target id is required");
                }
                if (publisher != null && publisher.hasRole("TEACHER")
                        && !teachingScopeService.visibleClassCourseIds(publisher).contains(target.targetId)) {
                    throw new IllegalArgumentException("Teacher cannot target a class course outside teaching scope");
                }
            }
            case "USER" -> {
                if (target.targetId <= 0) {
                    throw new IllegalArgumentException("User target id is required");
                }
                if (publisher != null && publisher.hasRole("TEACHER")
                        && !teachingScopeService.visibleStudentUserIds(publisher).contains(target.targetId)) {
                    throw new IllegalArgumentException("Teacher cannot target a student outside teaching scope");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported notice target type: " + target.targetType);
        }
    }

    private TargetSpec normalizeTarget(String targetType, Long targetId, String targetCode) {
        String type = upperOrDefault(targetType, "");
        String code = upperOrDefault(targetCode, "");
        Long id = targetId == null ? 0L : targetId;
        if ("SYSTEM".equals(type)) {
            return new TargetSpec(type, 0L, "");
        }
        if ("ROLE".equals(type)) {
            return new TargetSpec(type, 0L, code);
        }
        return new TargetSpec(type, id, code);
    }

    private List<TargetSpec> dedupeTargets(List<TargetSpec> targets) {
        Map<String, TargetSpec> unique = new LinkedHashMap<>();
        for (TargetSpec target : targets) {
            unique.put(target.targetType + ":" + target.targetId + ":" + target.targetCode, target);
        }
        return new ArrayList<>(unique.values());
    }

    private boolean canSeeNotice(Map<String, Object> notice, List<Map<String, Object>> targets, AuthUser user) {
        if (user == null) {
            return false;
        }
        if (teachingScopeService.hasGlobalScope(user)) {
            return true;
        }
        Long publisherId = numberValue(notice.get("publisherId"));
        if (publisherId != null && publisherId.equals(user.getId())) {
            return true;
        }
        if (numberValue(notice.get("status")) != 1L) {
            return false;
        }
        Set<Long> classIds = null;
        Set<Long> classCourseIds = null;
        for (Map<String, Object> target : targets) {
            String type = String.valueOf(target.get("targetType"));
            Long targetId = numberValue(target.get("targetId"));
            String targetCode = String.valueOf(target.getOrDefault("targetCode", ""));
            if ("SYSTEM".equals(type)) {
                return true;
            }
            if ("ROLE".equals(type) && user.hasRole(targetCode)) {
                return true;
            }
            if ("USER".equals(type) && targetId != null && targetId.equals(user.getId())) {
                return true;
            }
            if ("CLASS".equals(type)) {
                if (classIds == null) {
                    classIds = new LinkedHashSet<>(teachingScopeService.visibleClassIds(user));
                }
                if (targetId != null && classIds.contains(targetId)) {
                    return true;
                }
            }
            if ("CLASS_COURSE".equals(type)) {
                if (classCourseIds == null) {
                    classCourseIds = new LinkedHashSet<>(teachingScopeService.visibleClassCourseIds(user));
                }
                if (targetId != null && classCourseIds.contains(targetId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void pushNoticeNotification(JdbcTemplate jt, Long noticeId, NoticeRequest request, List<TargetSpec> targets) {
        try {
            Set<Long> recipients = resolveNoticeRecipients(jt, targets);
            if (recipients.isEmpty()) {
                return;
            }
            notificationService.sendBatch(new ArrayList<>(recipients), "New notice: " + trim(request.getTitle()),
                    trim(request.getContent()), "NOTICE", "/basic/data");
        } catch (Exception ex) {
            // Notifications are secondary. Notice publishing must not fail because of push errors.
        }
    }

    private Set<Long> resolveNoticeRecipients(JdbcTemplate jt, List<TargetSpec> targets) {
        Set<Long> recipients = new LinkedHashSet<>();
        for (TargetSpec target : targets) {
            switch (target.targetType) {
                case "SYSTEM" -> recipients.addAll(jt.queryForList(
                        "SELECT id FROM sys_user WHERE deleted = 0 AND status = 1", Long.class));
                case "ROLE" -> recipients.addAll(jt.queryForList("""
                        SELECT DISTINCT u.id
                        FROM sys_user u
                        JOIN sys_user_role ur ON ur.user_id = u.id
                        JOIN sys_role r ON r.id = ur.role_id
                        WHERE u.deleted = 0 AND u.status = 1 AND r.role_code = ?
                        """, Long.class, target.targetCode));
                case "CLASS" -> recipients.addAll(jt.queryForList("""
                        SELECT DISTINCT scm.student_user_id
                        FROM student_class_membership scm
                        JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0 AND u.status = 1
                        WHERE scm.class_id = ? AND scm.deleted = 0 AND scm.status = 1
                        """, Long.class, target.targetId));
                case "CLASS_COURSE" -> recipients.addAll(jt.queryForList("""
                        SELECT DISTINCT sce.student_user_id
                        FROM student_course_enrollment sce
                        JOIN sys_user u ON u.id = sce.student_user_id AND u.deleted = 0 AND u.status = 1
                        WHERE sce.class_course_id = ? AND sce.deleted = 0 AND sce.status = 1
                        """, Long.class, target.targetId));
                case "USER" -> {
                    Integer count = jt.queryForObject("""
                            SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0 AND status = 1
                            """, Integer.class, target.targetId);
                    if (count != null && count > 0) {
                        recipients.add(target.targetId);
                    }
                }
                default -> {
                    // Ignore invalid rows defensively.
                }
            }
        }
        return recipients;
    }

    private void syncClassMembersToClassCourse(Long classCourseId, Long classId) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                INSERT INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
                SELECT scm.student_user_id, ?, CASE WHEN scm.membership_type = 'PRIMARY' THEN 'CLASS' ELSE scm.membership_type END, 1, 0
                FROM student_class_membership scm
                WHERE scm.class_id = ?
                  AND scm.deleted = 0
                  AND scm.status = 1
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, dropped_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, classCourseId, classId);
    }

    private void syncClassMembersToClassCoursesForStudent(Long studentUserId, Long classId, String membershipType) {
        JdbcTemplate jt = requireJdbcTemplate();
        String enrollmentType = "PRIMARY".equalsIgnoreCase(membershipType) ? "CLASS" : membershipType.toUpperCase(Locale.ROOT);
        jt.update("""
                INSERT INTO student_course_enrollment (student_user_id, class_course_id, enrollment_type, status, deleted)
                SELECT ?, cc.id, ?, 1, 0
                FROM class_course cc
                WHERE cc.class_id = ?
                  AND cc.deleted = 0
                  AND cc.status = 1
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, dropped_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, studentUserId, enrollmentType, classId);
    }

    private void requireNoticeOwner(JdbcTemplate jt, Long id, AuthUser user) {
        List<Map<String, Object>> rows = jt.queryForList(
                "SELECT publisher_id FROM notice WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Notice not found");
        }
        if (user != null && user.hasRole("ADMIN")) {
            return;
        }
        Long publisherId = numberValue(rows.get(0).get("publisher_id"));
        if (user == null || publisherId == null || !publisherId.equals(user.getId())) {
            throw new IllegalArgumentException("Only the publisher or admin can manage this notice");
        }
    }

    private Map<String, Object> getClassById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT id, class_name AS className, class_code AS classCode, class_type AS classType,
                       major, grade, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_class WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Class not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getCourseById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT co.id, co.course_code AS courseCode, co.course_name AS courseName,
                       co.subject_id AS subjectId, s.subject_name AS subjectName, co.credit,
                       co.description, co.status, co.created_at AS createdAt, co.updated_at AS updatedAt
                FROM edu_course co
                LEFT JOIN edu_subject s ON s.id = co.subject_id AND s.deleted = 0
                WHERE co.id = ? AND co.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Course not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getClassCourseById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT cc.id AS classCourseId, cc.class_id AS classId, c.class_name AS className,
                       c.class_code AS classCode, c.class_type AS classType, c.major, c.grade,
                       cc.course_id AS courseId, co.course_code AS courseCode, co.course_name AS courseName,
                       co.subject_id AS subjectId, s.subject_name AS subjectName,
                       cc.term_name AS termName, cc.status
                FROM class_course cc
                JOIN edu_class c ON c.id = cc.class_id AND c.deleted = 0
                JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                LEFT JOIN edu_subject s ON s.id = co.subject_id AND s.deleted = 0
                WHERE cc.id = ? AND cc.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Class course not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getTeachingAssignmentById(Long id) {
        List<Map<String, Object>> rows = listTeachingAssignments(null, null, null).stream()
                .filter(row -> id.equals(numberValue(row.get("id"))))
                .toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Teaching assignment not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> activeMembershipByNaturalKey(Long studentUserId, Long classId, String membershipType) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT scm.id, scm.student_user_id AS studentUserId, u.real_name AS studentName,
                       sp.student_no AS studentNo, scm.class_id AS classId, c.class_name AS className,
                       c.class_type AS classType, scm.membership_type AS membershipType,
                       scm.source, scm.status, scm.joined_at AS joinedAt, scm.left_at AS leftAt
                FROM student_class_membership scm
                JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                JOIN edu_class c ON c.id = scm.class_id AND c.deleted = 0
                WHERE scm.student_user_id = ?
                  AND scm.class_id = ?
                  AND scm.membership_type = ?
                  AND scm.deleted = 0
                """, studentUserId, classId, membershipType);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Student membership not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getSubjectById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT id, subject_name AS subjectName, description, status, created_at AS createdAt, updated_at AS updatedAt
                FROM edu_subject WHERE id = ? AND deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Subject not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getKnowledgePointById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT kp.id, kp.subject_id AS subjectId, s.subject_name AS subjectName, kp.parent_id AS parentId,
                       kp.point_name AS pointName, kp.sort_order AS sortOrder, kp.status,
                       kp.created_at AS createdAt, kp.updated_at AS updatedAt
                FROM edu_knowledge_point kp
                JOIN edu_subject s ON s.id = kp.subject_id
                WHERE kp.id = ? AND kp.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Knowledge point not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> getNoticeById(Long id) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT n.id, n.title, n.content, n.status, n.publish_time AS publishTime,
                       n.publisher_id AS publisherId, u.real_name AS publisherName,
                       n.created_at AS createdAt, n.updated_at AS updatedAt
                FROM notice n
                LEFT JOIN sys_user u ON u.id = n.publisher_id
                WHERE n.id = ? AND n.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Notice not found");
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        List<Map<String, Object>> targets = getNoticeTargets(id);
        row.put("targets", targets);
        row.put("targetSummary", targetSummary(targets));
        return row;
    }

    private List<Map<String, Object>> getNoticeTargets(Long noticeId) {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT id, notice_id AS noticeId, target_type AS targetType,
                       target_id AS targetId, target_code AS targetCode
                FROM notice_target
                WHERE notice_id = ?
                ORDER BY id
                """, noticeId);
    }

    private String targetSummary(List<Map<String, Object>> targets) {
        if (targets == null || targets.isEmpty()) {
            return "No target";
        }
        List<String> values = new ArrayList<>();
        for (Map<String, Object> target : targets) {
            String type = String.valueOf(target.get("targetType"));
            String code = String.valueOf(target.getOrDefault("targetCode", ""));
            Long id = numberValue(target.get("targetId"));
            values.add(switch (type) {
                case "SYSTEM" -> "System";
                case "ROLE" -> "Role:" + code;
                case "CLASS" -> "Class#" + id;
                case "CLASS_COURSE" -> "ClassCourse#" + id;
                case "USER" -> "User#" + id;
                default -> type;
            });
        }
        return String.join(", ", values);
    }

    private void validateSubjectExists(Long subjectId) {
        if (!exists("edu_subject", subjectId)) {
            throw new IllegalArgumentException("Subject not found");
        }
    }

    private void validateClassExists(Long classId) {
        if (!exists("edu_class", classId)) {
            throw new IllegalArgumentException("Class not found");
        }
    }

    private void validateCourseExists(Long courseId) {
        if (!exists("edu_course", courseId)) {
            throw new IllegalArgumentException("Course not found");
        }
    }

    private void validateClassCourseExists(Long classCourseId) {
        if (!exists("class_course", classCourseId)) {
            throw new IllegalArgumentException("Class course not found");
        }
    }

    private void validateTeacherExists(Long teacherUserId) {
        Integer count = requireJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.id = ? AND u.deleted = 0 AND r.role_code = 'TEACHER'
                """, Integer.class, teacherUserId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Teacher not found");
        }
    }

    private void validateStudentExists(Long studentUserId) {
        Integer count = requireJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.id = ? AND u.deleted = 0 AND r.role_code = 'STUDENT'
                """, Integer.class, studentUserId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Student not found");
        }
    }

    private boolean exists(String table, Long id) {
        if (id == null) {
            return false;
        }
        Integer count = requireJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ? AND deleted = 0", Integer.class, id);
        return count != null && count > 0;
    }

    private void appendIn(StringBuilder sql, List<Object> params, String column, List<Long> ids) {
        sql.append(" AND ").append(column).append(" IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(ids.get(i));
        }
        sql.append(")");
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upperOrDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed.toUpperCase(Locale.ROOT);
    }

    private boolean contains(Object value, String keyword) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private Long numberValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static class TargetSpec {
        private final String targetType;
        private final Long targetId;
        private final String targetCode;

        private TargetSpec(String targetType, Long targetId, String targetCode) {
            this.targetType = targetType;
            this.targetId = targetId == null ? 0L : targetId;
            this.targetCode = targetCode == null ? "" : targetCode;
        }
    }
}
