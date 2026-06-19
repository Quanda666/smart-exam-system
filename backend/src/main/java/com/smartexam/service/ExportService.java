package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;

    public ExportService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                         TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
    }

    public ExportFile examScoreSheet(Long examId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        examId = requirePositiveExamId(examId);
        List<Map<String, Object>> examRows = jt.queryForList(
                "SELECT exam_name, created_by FROM exam WHERE id = ? AND deleted = 0", examId);
        if (examRows.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        if (!user.hasRole("ADMIN")) {
            Object createdBy = examRows.get(0).get("created_by");
            if (createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
                throw new IllegalArgumentException("Only the creator can export this exam");
            }
        }
        String examName = String.valueOf(examRows.get(0).get("exam_name"));
        requireScoresReleased(jt, examId);

        List<Map<String, Object>> records = jt.queryForList("""
                SELECT u.real_name AS realName, u.username, sp.student_no AS studentNo,
                       pc.class_name AS className, ea.score,
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
                JOIN sys_user u ON u.id = ea.user_id AND u.deleted = 0
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class pc ON pc.id = COALESCE(sp.primary_class_id, sp.class_id) AND pc.deleted = 0
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                JOIN paper p ON p.id = e.paper_id
                WHERE ea.exam_id = ? AND ea.status = 5 AND ea.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                ORDER BY ea.score DESC, ea.submit_time
                """, examId);

        List<String> headers = List.of("Rank", "Student No", "Name", "Primary Class", "Score", "Total",
                "Question Count", "Answered Count", "Unanswered Count", "Submitted At");
        List<List<Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> r : records) {
            rows.add(Arrays.asList(rank++, r.get("studentNo"), r.get("realName"), r.get("className"),
                    r.get("score"), r.get("totalScore"), r.get("questionCount"), r.get("answeredCount"),
                    r.get("unansweredCount"), r.get("submitTime")));
        }
        return new ExportFile(safeName(examName) + "-scores.csv", CsvExport.build(headers, rows));
    }

    public ExportFile classRoster(Long classId, AuthUser user) {
        if (!teachingScopeService.hasGlobalScope(user)
                && !new LinkedHashSet<>(teachingScopeService.visibleClassIds(user)).contains(classId)) {
            throw new IllegalArgumentException("Class is outside current teaching scope");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> classRows = jt.queryForList(
                "SELECT class_name FROM edu_class WHERE id = ? AND deleted = 0", classId);
        String className = classRows.isEmpty() ? ("class-" + classId) : String.valueOf(classRows.get(0).get("class_name"));

        List<Map<String, Object>> students = jt.queryForList("""
                SELECT sp.student_no AS studentNo, u.real_name AS realName, u.username,
                       scm.membership_type AS membershipType,
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
                WHERE scm.class_id = ?
                  AND scm.deleted = 0
                  AND scm.status = 1
                ORDER BY sp.student_no, u.id
                """, classId);

        List<String> headers = List.of("Student No", "Name", "Username", "Membership", "Completed Exams", "Average Score");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> s : students) {
            rows.add(Arrays.asList(s.get("studentNo"), s.get("realName"), s.get("username"),
                    s.get("membershipType"), s.get("completedCount"), s.get("avgScore")));
        }
        return new ExportFile(safeName(className) + "-roster.csv", CsvExport.build(headers, rows));
    }

    public ExportFile studentScores(Long userId, AuthUser user) {
        if (!teachingScopeService.canAccessStudent(user, userId)) {
            throw new IllegalArgumentException("Student is outside current teaching scope");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> profile = jt.queryForList(
                "SELECT real_name FROM sys_user WHERE id = ? AND deleted = 0", userId);
        if (profile.isEmpty()) {
            throw new IllegalArgumentException("Student not found");
        }
        String realName = String.valueOf(profile.get(0).get("real_name"));

        List<Map<String, Object>> exams = jt.queryForList("""
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
                """, userId);

        List<String> headers = List.of("Exam", "Subject", "Score", "Total",
                "Question Count", "Answered Count", "Unanswered Count", "Submitted At");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> e : exams) {
            rows.add(Arrays.asList(e.get("examName"), e.get("subjectName"), e.get("score"),
                    e.get("totalScore"), e.get("questionCount"), e.get("answeredCount"),
                    e.get("unansweredCount"), e.get("submitTime")));
        }
        return new ExportFile(safeName(realName) + "-score-history.csv", CsvExport.build(headers, rows));
    }

    private void requireScoresReleased(JdbcTemplate jt, Long examId) {
        examId = requirePositiveExamId(examId);
        Integer released = jt.queryForObject("""
                SELECT COUNT(*)
                FROM score_release
                WHERE exam_id = ? AND status = 1
                """, Integer.class, examId);
        if (released == null || released == 0) {
            throw new IllegalStateException("Scores have not been published");
        }
    }

    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "export";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }

    private Long requirePositiveExamId(Long examId) {
        if (examId == null || examId <= 0) {
            throw new IllegalArgumentException("examId must be positive");
        }
        return examId;
    }
}
