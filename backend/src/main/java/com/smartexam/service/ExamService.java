package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.ExamRequest;
import com.smartexam.dto.exam.ExamUpdateRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExamService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;
    private final TeachingScopeService teachingScopeService;

    public ExamService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                       NotificationService notificationService,
                       TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
        this.teachingScopeService = teachingScopeService;
    }

    public List<Map<String, Object>> listTeacherExams(String keyword, Integer status, AuthUser user) {
        return listTeacherExams(keyword, status, user, 1, 200).getList();
    }

    public PageResult<Map<String, Object>> listTeacherExams(String keyword, Integer status, AuthUser user,
                                                            int page, int size) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        String kw = blankToNull(keyword);

        List<Object> countParams = new ArrayList<>();
        countParams.add(status);
        countParams.add(status);
        countParams.add(kw);
        countParams.add(kw);
        countParams.add(kw);
        String scopeSql = appendExamScope(user, countParams);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE e.deleted = 0
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                """ + scopeSql, Long.class, countParams.toArray());

        List<Object> listParams = new ArrayList<>();
        listParams.add(status);
        listParams.add(status);
        listParams.add(kw);
        listParams.add(kw);
        listParams.add(kw);
        String listScopeSql = appendExamScope(user, listParams);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT e.id, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.status, e.created_by AS createdBy, p.paper_name AS paperName, s.subject_name AS subjectName,
                       (SELECT GROUP_CONCAT(CONCAT(et.target_type, ':', et.target_id, ':', et.target_code) ORDER BY et.id SEPARATOR ',')
                        FROM exam_target et WHERE et.exam_id = e.id) AS targetSummary,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE e.deleted = 0
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                """ + listScopeSql + """
                ORDER BY e.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> listStudentExams(AuthUser user, int page, int size) {
        JdbcTemplate jt = requireJdbcTemplate();
        syncStudentAttempts(user);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.user_id = ? AND e.deleted = 0
                """, Long.class, user.getId());

        List<Map<String, Object>> list = jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       a.status, p.paper_name AS paperName, s.subject_name AS subjectName, a.score,
                       a.submit_time AS submitTime
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE a.user_id = ? AND e.deleted = 0
                ORDER BY e.start_time DESC, e.id DESC
                LIMIT ? OFFSET ?
                """, user.getId(), safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public List<Map<String, Object>> listStudentExams(AuthUser user) {
        return listStudentExams(user, 1, 200).getList();
    }

    @Transactional
    public Map<String, Object> createExam(ExamRequest request, AuthUser creator) {
        validateExamRequest(request);
        JdbcTemplate jt = requireJdbcTemplate();
        List<TargetSpec> targets = normalizeExamTargets(request, creator);
        jt.update("""
                INSERT INTO exam (paper_id, exam_name, description, start_time, end_time, duration_minutes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, request.getPaperId(), trim(request.getExamName()), trim(request.getDescription()),
                request.getStartTime(), request.getEndTime(), request.getDurationMinutes(), creator.getId());
        Long examId = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (TargetSpec target : targets) {
            jt.update("""
                    INSERT INTO exam_target (exam_id, target_type, target_id, target_code)
                    VALUES (?, ?, ?, ?)
                    """, examId, target.targetType, target.targetId, target.targetCode);
            if ("CLASS".equals(target.targetType)) {
                jt.update("""
                        INSERT IGNORE INTO exam_class (exam_id, class_id)
                        VALUES (?, ?)
                        """, examId, target.targetId);
            }
        }

        Set<Long> studentIds = resolveExamStudents(jt, targets);
        for (Long studentId : studentIds) {
            insertAttemptIfMissing(jt, examId, studentId);
        }
        if (!studentIds.isEmpty()) {
            notificationService.sendBatch(new ArrayList<>(studentIds), "New exam: " + trim(request.getExamName()),
                    "A new exam has been published. Please check the exam center.", "EXAM", "/student/exams");
        }
        return getExamById(examId);
    }

    public Map<String, Object> getExamById(Long examId) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> exam = jt.queryForMap("""
                SELECT e.*, p.paper_name AS paperName,
                       (SELECT GROUP_CONCAT(CONCAT(et.target_type, ':', et.target_id, ':', et.target_code) ORDER BY et.id SEPARATOR ',')
                        FROM exam_target et WHERE et.exam_id = e.id) AS targetSummary
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                WHERE e.id = ? AND e.deleted = 0
                """, examId);
        exam.put("targets", jt.queryForList("""
                SELECT id, target_type AS targetType, target_id AS targetId, target_code AS targetCode
                FROM exam_target
                WHERE exam_id = ?
                ORDER BY id
                """, examId));
        return exam;
    }

    @Transactional
    public Map<String, Object> startExam(Long attemptId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> attempt = jt.queryForMap(
                "SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?", attemptId, user.getId());

        int status = ((Number) attempt.get("status")).intValue();
        if (status >= 2) {
            throw new IllegalStateException("This exam attempt has already been submitted");
        }
        if (status == 0) {
            jt.update("UPDATE exam_attempt SET status = 1, start_time = NOW() WHERE id = ?", attemptId);
        }

        Long examId = ((Number) attempt.get("exam_id")).longValue();
        Map<String, Object> exam = jt.queryForMap("""
                SELECT e.*, p.total_score, p.paper_name
                FROM exam e
                JOIN paper p ON e.paper_id = p.id
                WHERE e.id = ?
                """, examId);
        Object paperId = exam.get("paper_id");
        List<Map<String, Object>> questions = jt.queryForList("""
                SELECT q.id, q.question_type, q.stem, q.difficulty, pq.score
                FROM paper_question pq
                JOIN question q ON pq.question_id = q.id
                WHERE pq.paper_id = ?
                ORDER BY pq.sort_order
                """, paperId);

        for (Map<String, Object> q : questions) {
            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(q.get("question_type"))) {
                q.put("options", jt.queryForList("""
                        SELECT option_label, option_content
                        FROM question_option
                        WHERE question_id = ?
                        ORDER BY sort_order
                        """, q.get("id")));
            }
        }
        exam.put("questions", questions);

        Integer durationMinutes = exam.get("duration_minutes") == null ? null : ((Number) exam.get("duration_minutes")).intValue();
        if (durationMinutes != null) {
            Long elapsed = jt.queryForObject("""
                    SELECT TIMESTAMPDIFF(SECOND, start_time, NOW())
                    FROM exam_attempt
                    WHERE id = ?
                    """, Long.class, attemptId);
            long remaining = durationMinutes * 60L - (elapsed == null ? 0L : elapsed);
            exam.put("remainingSeconds", Math.max(0L, remaining));
        }

        exam.put("draftAnswers", jt.query(
                "SELECT answers FROM exam_answer_draft WHERE attempt_id = ?",
                rs -> rs.next() ? rs.getString("answers") : null, attemptId));
        return exam;
    }

    public void saveDraft(Long attemptId, String answersJson, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE id = ? AND user_id = ? AND status = 1
                """, Integer.class, attemptId, user.getId());
        if (count == null || count == 0) {
            return;
        }
        jt.update("""
                INSERT INTO exam_answer_draft (attempt_id, answers)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE answers = VALUES(answers)
                """, attemptId, answersJson);
    }

    @Transactional
    public Map<String, Object> submitExam(Long attemptId, Map<Long, String> answers, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> attempt = jt.queryForMap("""
                SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?
                """, attemptId, user.getId());

        if (((Number) attempt.get("status")).intValue() != 1) {
            throw new IllegalStateException("Exam is not in progress");
        }

        jt.update("UPDATE exam_attempt SET status = 2, submit_time = NOW() WHERE id = ?", attemptId);
        BigDecimal totalScore = BigDecimal.ZERO;
        boolean hasSubjective = false;

        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            Long questionId = entry.getKey();
            String answer = entry.getValue();
            Map<String, Object> question = jt.queryForMap("""
                    SELECT question_type, correct_answer
                    FROM question
                    WHERE id = ?
                    """, questionId);
            String questionType = (String) question.get("question_type");
            String correctAnswer = (String) question.get("correct_answer");

            boolean isCorrect = false;
            BigDecimal score = BigDecimal.ZERO;
            int reviewStatus = 0;
            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(questionType)) {
                isCorrect = answer != null && correctAnswer != null
                        && normalizeObjective(answer).equalsIgnoreCase(normalizeObjective(correctAnswer));
                if (isCorrect) {
                    Map<String, Object> pq = jt.queryForMap("""
                            SELECT pq.score
                            FROM paper_question pq
                            JOIN exam e ON e.paper_id = pq.paper_id
                            JOIN exam_attempt ea ON ea.exam_id = e.id
                            WHERE ea.id = ? AND pq.question_id = ?
                            """, attemptId, questionId);
                    score = (BigDecimal) pq.get("score");
                }
                reviewStatus = 1;
            } else {
                hasSubjective = true;
            }

            jt.update("""
                    INSERT INTO answer_record (attempt_id, question_id, answer_content, score, is_correct, review_status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, attemptId, questionId, answer, score, isCorrect, reviewStatus);
            totalScore = totalScore.add(score);
        }

        int finalStatus = hasSubjective ? 4 : 5;
        jt.update("UPDATE exam_attempt SET score = ?, status = ? WHERE id = ?", totalScore, finalStatus, attemptId);
        jt.update("DELETE FROM exam_answer_draft WHERE attempt_id = ?", attemptId);
        return Map.of("success", true, "message", "Submitted", "score", totalScore, "status", finalStatus);
    }

    public Map<String, Object> updateExam(Long id, ExamUpdateRequest request, AuthUser user) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                UPDATE exam
                SET exam_name = ?, description = ?, start_time = ?, end_time = ?, duration_minutes = ?
                WHERE id = ? AND deleted = 0
                """, trim(request.getExamName()), trim(request.getDescription()), request.getStartTime(),
                request.getEndTime(), request.getDurationMinutes(), id);
        return getExamById(id);
    }

    public void deleteExam(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("UPDATE exam SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        jt.update("DELETE FROM exam_attempt WHERE exam_id = ? AND status = 0", id);
        jt.update("DELETE FROM exam_target WHERE exam_id = ?", id);
    }

    public void closeExam(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        requireJdbcTemplate().update("UPDATE exam SET end_time = NOW() WHERE id = ? AND deleted = 0", id);
    }

    private void validateExamRequest(ExamRequest request) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if ((request.getClassIds() == null || request.getClassIds().isEmpty())
                && (request.getClassCourseIds() == null || request.getClassCourseIds().isEmpty())
                && (request.getStudentUserIds() == null || request.getStudentUserIds().isEmpty())) {
            throw new IllegalArgumentException("At least one exam target is required");
        }
    }

    private List<TargetSpec> normalizeExamTargets(ExamRequest request, AuthUser creator) {
        List<TargetSpec> targets = new ArrayList<>();
        if (request.getClassIds() != null) {
            for (Long id : request.getClassIds()) {
                if (id != null) {
                    targets.add(new TargetSpec("CLASS", id, ""));
                }
            }
        }
        if (request.getClassCourseIds() != null) {
            for (Long id : request.getClassCourseIds()) {
                if (id != null) {
                    targets.add(new TargetSpec("CLASS_COURSE", id, ""));
                }
            }
        }
        if (request.getStudentUserIds() != null) {
            for (Long id : request.getStudentUserIds()) {
                if (id != null) {
                    targets.add(new TargetSpec("USER", id, ""));
                }
            }
        }
        MapKeySet unique = new MapKeySet();
        List<TargetSpec> result = new ArrayList<>();
        for (TargetSpec target : targets) {
            validateExamTarget(target, creator);
            if (unique.add(target.targetType + ":" + target.targetId)) {
                result.add(target);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("At least one valid exam target is required");
        }
        return result;
    }

    private void validateExamTarget(TargetSpec target, AuthUser creator) {
        JdbcTemplate jt = requireJdbcTemplate();
        Integer exists = switch (target.targetType) {
            case "CLASS" -> jt.queryForObject("""
                    SELECT COUNT(*) FROM edu_class WHERE id = ? AND deleted = 0 AND status = 1
                    """, Integer.class, target.targetId);
            case "CLASS_COURSE" -> jt.queryForObject("""
                    SELECT COUNT(*) FROM class_course WHERE id = ? AND deleted = 0 AND status = 1
                    """, Integer.class, target.targetId);
            case "USER" -> jt.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.id
                    JOIN sys_role r ON r.id = ur.role_id
                    WHERE u.id = ? AND u.deleted = 0 AND u.status = 1 AND r.role_code = 'STUDENT'
                    """, Integer.class, target.targetId);
            default -> 0;
        };
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("Invalid exam target: " + target.targetType + "#" + target.targetId);
        }
        if (creator != null && creator.hasRole("TEACHER")) {
            if ("CLASS".equals(target.targetType)
                    && !teachingScopeService.visibleClassIds(creator).contains(target.targetId)) {
                throw new IllegalArgumentException("Teacher cannot target a class outside teaching scope");
            }
            if ("CLASS_COURSE".equals(target.targetType)
                    && !teachingScopeService.visibleClassCourseIds(creator).contains(target.targetId)) {
                throw new IllegalArgumentException("Teacher cannot target a class course outside teaching scope");
            }
            if ("USER".equals(target.targetType)
                    && !teachingScopeService.visibleStudentUserIds(creator).contains(target.targetId)) {
                throw new IllegalArgumentException("Teacher cannot target a student outside teaching scope");
            }
        }
    }

    private Set<Long> resolveExamStudents(JdbcTemplate jt, List<TargetSpec> targets) {
        Set<Long> studentIds = new LinkedHashSet<>();
        for (TargetSpec target : targets) {
            switch (target.targetType) {
                case "CLASS" -> studentIds.addAll(jt.queryForList("""
                        SELECT DISTINCT scm.student_user_id
                        FROM student_class_membership scm
                        JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0 AND u.status = 1
                        WHERE scm.class_id = ? AND scm.deleted = 0 AND scm.status = 1
                        """, Long.class, target.targetId));
                case "CLASS_COURSE" -> studentIds.addAll(jt.queryForList("""
                        SELECT DISTINCT sce.student_user_id
                        FROM student_course_enrollment sce
                        JOIN sys_user u ON u.id = sce.student_user_id AND u.deleted = 0 AND u.status = 1
                        WHERE sce.class_course_id = ? AND sce.deleted = 0 AND sce.status = 1
                        """, Long.class, target.targetId));
                case "USER" -> studentIds.add(target.targetId);
                default -> {
                    // No-op.
                }
            }
        }
        return studentIds;
    }

    private void syncStudentAttempts(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Set<Long> examIds = new LinkedHashSet<>();
        examIds.addAll(jt.queryForList("""
                SELECT DISTINCT e.id
                FROM exam e
                JOIN exam_target et ON et.exam_id = e.id
                WHERE e.deleted = 0 AND et.target_type = 'USER' AND et.target_id = ?
                """, Long.class, user.getId()));
        addEligibleExamsByTargets(jt, examIds, "CLASS", teachingScopeService.visibleClassIds(user));
        addEligibleExamsByTargets(jt, examIds, "CLASS_COURSE", teachingScopeService.visibleClassCourseIds(user));
        for (Long examId : examIds) {
            insertAttemptIfMissing(jt, examId, user.getId());
        }
    }

    private void addEligibleExamsByTargets(JdbcTemplate jt, Set<Long> examIds, String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", targetIds.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>();
        params.add(targetType);
        params.addAll(targetIds);
        examIds.addAll(jt.queryForList("""
                SELECT DISTINCT e.id
                FROM exam e
                JOIN exam_target et ON et.exam_id = e.id
                WHERE e.deleted = 0 AND et.target_type = ? AND et.target_id IN (
                """ + placeholders + ")", Long.class, params.toArray()));
    }

    private void insertAttemptIfMissing(JdbcTemplate jt, Long examId, Long studentId) {
        jt.update("""
                INSERT INTO exam_attempt (exam_id, user_id, status)
                SELECT ?, ?, 0
                FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM exam_attempt WHERE exam_id = ? AND user_id = ?
                )
                """, examId, studentId, examId, studentId);
    }

    private String appendExamScope(AuthUser user, List<Object> params) {
        if (teachingScopeService.hasGlobalScope(user)) {
            return "";
        }
        StringBuilder sql = new StringBuilder(" AND (e.created_by = ?");
        params.add(user.getId());
        appendTargetScope(sql, params, "CLASS", teachingScopeService.visibleClassIds(user));
        appendTargetScope(sql, params, "CLASS_COURSE", teachingScopeService.visibleClassCourseIds(user));
        appendTargetScope(sql, params, "USER", teachingScopeService.visibleStudentUserIds(user));
        sql.append(")");
        return sql.toString();
    }

    private void appendTargetScope(StringBuilder sql, List<Object> params, String targetType, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sql.append(" OR EXISTS (SELECT 1 FROM exam_target et WHERE et.exam_id = e.id AND et.target_type = ? AND et.target_id IN (");
        params.add(targetType);
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(ids.get(i));
        }
        sql.append("))");
    }

    private void requireOwnedExam(Long id, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList(
                "SELECT created_by FROM exam WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        if (user.hasRole("ADMIN")) {
            return;
        }
        Object createdBy = rows.get(0).get("created_by");
        if (createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("Only the creator can manage this exam");
        }
    }

    private String normalizeObjective(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s,，;；]", "");
        char[] chars = cleaned.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
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

    private static class TargetSpec {
        private final String targetType;
        private final Long targetId;
        private final String targetCode;

        private TargetSpec(String targetType, Long targetId, String targetCode) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetCode = targetCode == null ? "" : targetCode;
        }
    }

    private static class MapKeySet {
        private final Set<String> data = new LinkedHashSet<>();

        private boolean add(String key) {
            return data.add(key);
        }
    }
}
