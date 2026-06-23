package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class StudentInsightService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;

    public StudentInsightService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                 TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
    }

    public List<Map<String, Object>> listClassStudents(Long classId, AuthUser user) {
        if (!teachingScopeService.hasGlobalScope(user)
                && !new LinkedHashSet<>(teachingScopeService.visibleClassIds(user)).contains(classId)) {
            throw new IllegalArgumentException("Class is outside current teaching scope");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT u.id AS userId, u.username, u.real_name AS realName, sp.student_no AS studentNo,
                       u.status, c.class_name AS className, scm.membership_type AS membershipType,
                       (SELECT COUNT(*)
                        FROM exam_attempt ea
                        JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                        JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                        WHERE ea.user_id = u.id AND ea.status = 5 AND ea.score IS NOT NULL
                          AND NOT EXISTS (
                            SELECT 1 FROM score_appeal sa
                            WHERE sa.attempt_id = ea.id
                              AND sa.status = 1
                              AND sa.handling_result = 'RECHECK_REQUIRED'
                          )) AS completedCount,
                       (SELECT COALESCE(ROUND(AVG(ea.score), 2), 0)
                        FROM exam_attempt ea
                        JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                        JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                        WHERE ea.user_id = u.id AND ea.status = 5 AND ea.score IS NOT NULL
                          AND NOT EXISTS (
                            SELECT 1 FROM score_appeal sa
                            WHERE sa.attempt_id = ea.id
                              AND sa.status = 1
                              AND sa.handling_result = 'RECHECK_REQUIRED'
                          )) AS avgScore
                FROM student_class_membership scm
                JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                JOIN edu_class c ON c.id = scm.class_id AND c.deleted = 0
                WHERE scm.class_id = ?
                  AND scm.deleted = 0
                  AND scm.status = 1
                ORDER BY sp.student_no, u.id
                """, classId);
    }

    public Map<String, Object> studentInsight(Long userId, AuthUser user) {
        if (!teachingScopeService.canAccessStudent(user, userId)) {
            throw new IllegalArgumentException("Student is outside current teaching scope");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        List<Map<String, Object>> profile = jt.queryForList("""
                SELECT u.real_name AS realName, u.username, sp.student_no AS studentNo,
                       sp.enrollment_year AS enrollmentYear, sp.college, sp.major,
                       pc.class_name AS primaryClassName,
                       (SELECT GROUP_CONCAT(CONCAT(c.class_name, ':', scm.membership_type) ORDER BY scm.membership_type, c.id SEPARATOR ',')
                        FROM student_class_membership scm
                        JOIN edu_class c ON c.id = scm.class_id AND c.deleted = 0
                        WHERE scm.student_user_id = u.id AND scm.deleted = 0 AND scm.status = 1) AS classMemberships
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class pc ON pc.id = COALESCE(sp.primary_class_id, sp.class_id) AND pc.deleted = 0
                WHERE u.id = ? AND u.deleted = 0
                """, userId);
        if (profile.isEmpty()) {
            throw new IllegalArgumentException("Student not found");
        }
        data.put("student", profile.get(0));

        data.put("courses", jt.queryForList("""
                SELECT cc.id AS classCourseId, c.class_name AS className, co.course_name AS courseName,
                       sce.enrollment_type AS enrollmentType, cc.term_name AS termName
                FROM student_course_enrollment sce
                JOIN class_course cc ON cc.id = sce.class_course_id AND cc.deleted = 0
                JOIN edu_class c ON c.id = cc.class_id AND c.deleted = 0
                JOIN edu_course co ON co.id = cc.course_id AND co.deleted = 0
                WHERE sce.student_user_id = ?
                  AND sce.deleted = 0
                  AND sce.status = 1
                ORDER BY cc.term_name, c.id, co.id
                """, userId));

        data.put("exams", jt.queryForList("""
                SELECT e.exam_name AS examName, s.subject_name AS subjectName, ea.score,
                       COALESCE((SELECT SUM(eqs.score)
                                 FROM exam_question_snapshot eqs
                                 WHERE eqs.exam_id = e.id), p.total_score) AS totalScore,
                       COALESCE(NULLIF((SELECT COUNT(*)
                                        FROM exam_question_snapshot eqs_count
                                        WHERE eqs_count.exam_id = e.id), 0),
                                (SELECT COUNT(*)
                                 FROM paper_question pq_count
                                 WHERE pq_count.paper_id = e.paper_id)) AS questionCount,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM exam_question_snapshot eqs_exists
                         WHERE eqs_exists.exam_id = e.id
                       ) THEN (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT ar_answered.question_id)
                         FROM answer_record ar_answered
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = ar_answered.question_id
                         WHERE ar_answered.attempt_id = ea.id
                           AND ar_answered.answer_content IS NOT NULL
                           AND TRIM(ar_answered.answer_content) <> ''
                       ) END AS answeredCount,
                       GREATEST(0,
                           COALESCE(NULLIF((SELECT COUNT(*)
                                            FROM exam_question_snapshot eqs_total
                                            WHERE eqs_total.exam_id = e.id), 0),
                                    (SELECT COUNT(*)
                                     FROM paper_question pq_total
                                     WHERE pq_total.paper_id = e.paper_id))
                           - CASE WHEN EXISTS (
                               SELECT 1
                               FROM exam_question_snapshot eqs_exists
                               WHERE eqs_exists.exam_id = e.id
                             ) THEN (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN exam_question_snapshot eqs_answered
                                 ON eqs_answered.exam_id = e.id
                                AND eqs_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) ELSE (
                               SELECT COUNT(DISTINCT ar_missing.question_id)
                               FROM answer_record ar_missing
                               JOIN paper_question pq_answered
                                 ON pq_answered.paper_id = e.paper_id
                                AND pq_answered.question_id = ar_missing.question_id
                               WHERE ar_missing.attempt_id = ea.id
                                 AND ar_missing.answer_content IS NOT NULL
                                 AND TRIM(ar_missing.answer_content) <> ''
                             ) END) AS unansweredCount,
                       ea.submit_time AS submitTime
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL AND e.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                ORDER BY ea.submit_time
                """, userId));

        data.put("summary", jt.queryForMap("""
                SELECT COUNT(*) AS count,
                       COALESCE(ROUND(AVG(ea.score), 2), 0) AS avgScore,
                       COALESCE(MAX(ea.score), 0) AS maxScore,
                       COALESCE(MIN(ea.score), 0) AS minScore
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """, userId));

        return data;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }
}
