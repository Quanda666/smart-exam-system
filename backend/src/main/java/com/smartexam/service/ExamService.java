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
import java.util.List;
import java.util.Map;

@Service
public class ExamService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;

    public ExamService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, NotificationService notificationService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> listTeacherExams(String keyword, Integer status, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
                SELECT e.id, e.exam_name AS examName, e.description, e.start_time AS startTime, e.end_time AS endTime,
                       e.duration_minutes AS durationMinutes, e.status, p.paper_name AS paperName, s.subject_name AS subjectName
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE e.deleted = 0 AND e.created_by = ?
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                ORDER BY e.id DESC
                """, user.getId(), status, status, keyword, keyword, keyword);
    }

    public PageResult<Map<String, Object>> listTeacherExams(String keyword, Integer status, AuthUser user,
                                                            int page, int size) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE e.deleted = 0 AND e.created_by = ?
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                """, Long.class, user.getId(), status, status, keyword, keyword, keyword);

        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT e.id, e.exam_name AS examName, e.description, e.start_time AS startTime, e.end_time AS endTime,
                       e.duration_minutes AS durationMinutes, e.status, p.paper_name AS paperName, s.subject_name AS subjectName
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE e.deleted = 0 AND e.created_by = ?
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                ORDER BY e.id DESC
                LIMIT ? OFFSET ?
                """, user.getId(), status, status, keyword, keyword, keyword, safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> listStudentExams(AuthUser user, int page, int size) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.user_id = ? AND e.deleted = 0
                """, Long.class, user.getId());

        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
            SELECT a.id as attemptId, e.id AS examId, e.exam_name AS examName, e.description, e.start_time AS startTime,
                   e.end_time AS endTime, e.duration_minutes AS durationMinutes, a.status,
                   p.paper_name AS paperName, s.subject_name as subjectName, a.score
            FROM exam_attempt a
            JOIN exam e ON e.id = a.exam_id
            JOIN paper p ON p.id = e.paper_id
            JOIN edu_subject s ON s.id = p.subject_id
            WHERE a.user_id = ? AND e.deleted = 0
            ORDER BY e.start_time DESC
            LIMIT ? OFFSET ?
            """, user.getId(), safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public List<Map<String, Object>> listStudentExams(AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("""
            SELECT a.id as attemptId, e.id AS examId, e.exam_name AS examName, e.description, e.start_time AS startTime,
                   e.end_time AS endTime, e.duration_minutes AS durationMinutes, a.status,
                   p.paper_name AS paperName, s.subject_name as subjectName, a.score
            FROM exam_attempt a
            JOIN exam e ON e.id = a.exam_id
            JOIN paper p ON p.id = e.paper_id
            JOIN edu_subject s ON s.id = p.subject_id
            WHERE a.user_id = ? AND e.deleted = 0
            ORDER BY e.start_time DESC
            """, user.getId());
    }

    @Transactional
    public Map<String, Object> createExam(ExamRequest request, AuthUser creator) {
        validateExamRequest(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("""
                INSERT INTO exam (paper_id, exam_name, description, start_time, end_time, duration_minutes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, request.getPaperId(), request.getExamName(), request.getDescription(), request.getStartTime(),
                request.getEndTime(), request.getDurationMinutes(), creator.getId());
        Long examId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (Long classId : request.getClassIds()) {
            jdbcTemplate.update("INSERT INTO exam_class (exam_id, class_id) VALUES (?, ?)", examId, classId);
        }

        java.util.Set<Long> studentIds = new java.util.LinkedHashSet<>();
        for (Long classId : request.getClassIds()) {
            studentIds.addAll(jdbcTemplate.queryForList(
                    "SELECT user_id FROM student_profile WHERE class_id = ? AND deleted = 0", Long.class, classId));
        }
        for (Long studentId : studentIds) {
            jdbcTemplate.update("INSERT INTO exam_attempt (exam_id, user_id, status) VALUES (?, ?, 0)", examId, studentId);
        }

        // 给全体参考学生发站内通知
        if (!studentIds.isEmpty()) {
            String title = "新考试：" + request.getExamName();
            String content = "您有一场新考试「" + request.getExamName() + "」，考试时间 " + request.getStartTime() + " 至 "
                    + request.getEndTime() + "，请及时参加。";
            notificationService.sendBatch(studentIds.stream().toList(), title, content, "EXAM", "/student/exams");
        }

        return getExamById(examId);
    }

    private void validateExamRequest(ExamRequest request) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
    }

    public Map<String, Object> getExamById(Long examId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForMap("SELECT * FROM exam WHERE id = ?", examId);
    }

    @Transactional
    public Map<String, Object> startExam(Long attemptId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> attempt = jdbcTemplate.queryForMap(
                "SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?", attemptId, user.getId());

        int status = ((Number) attempt.get("status")).intValue();
        if (status >= 2) {
            throw new IllegalStateException("本场考试已交卷，无法再次进入");
        }
        // status==0 首次进入：标记开始并记录开始时间；status==1 进行中：直接继续，支持刷新/断线重连恢复
        if (status == 0) {
            jdbcTemplate.update("UPDATE exam_attempt SET status = 1, start_time = NOW() WHERE id = ?", attemptId);
        }

        Long examId = ((Number) attempt.get("exam_id")).longValue();
        Map<String, Object> exam = jdbcTemplate.queryForMap(
                "SELECT e.*, p.total_score, p.paper_name FROM exam e JOIN paper p ON e.paper_id = p.id WHERE e.id = ?", examId);
        Object paperId = exam.get("paper_id");
        List<Map<String, Object>> questions = jdbcTemplate.queryForList("""
            SELECT q.id, q.question_type, q.stem, q.difficulty, pq.score FROM paper_question pq
            JOIN question q ON pq.question_id = q.id WHERE pq.paper_id = ? ORDER BY pq.sort_order
            """, paperId);

        for (Map<String, Object> q : questions) {
            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(q.get("question_type"))) {
                q.put("options", jdbcTemplate.queryForList(
                        "SELECT option_label, option_content FROM question_option WHERE question_id = ? ORDER BY sort_order", q.get("id")));
            }
        }
        exam.put("questions", questions);

        // 基于服务端开始时间计算剩余秒数，刷新/重进后倒计时不被重置、也无法通过刷新刷时长
        Integer durationMinutes = exam.get("duration_minutes") == null ? null : ((Number) exam.get("duration_minutes")).intValue();
        if (durationMinutes != null) {
            Long elapsed = jdbcTemplate.queryForObject(
                    "SELECT TIMESTAMPDIFF(SECOND, start_time, NOW()) FROM exam_attempt WHERE id = ?", Long.class, attemptId);
            long remaining = durationMinutes * 60L - (elapsed == null ? 0L : elapsed);
            exam.put("remainingSeconds", Math.max(0L, remaining));
        }

        // 返回已暂存的草稿答案（JSON 字符串），前端据此恢复作答进度
        exam.put("draftAnswers", jdbcTemplate.query(
                "SELECT answers FROM exam_answer_draft WHERE attempt_id = ?",
                rs -> rs.next() ? rs.getString("answers") : null, attemptId));

        return exam;
    }

    /**
     * 暂存答题草稿（前端定时自动保存调用）。仅本人且考试进行中时生效，失败不影响答题。
     */
    public void saveDraft(Long attemptId, String answersJson, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_attempt WHERE id = ? AND user_id = ? AND status = 1",
                Integer.class, attemptId, user.getId());
        if (count == null || count == 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO exam_answer_draft (attempt_id, answers) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE answers = VALUES(answers)
                """, attemptId, answersJson);
    }

    @Transactional
    public Map<String, Object> submitExam(Long attemptId, Map<Long, String> answers, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> attempt = jdbcTemplate.queryForMap("SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?", attemptId, user.getId());

        if (((Number) attempt.get("status")).intValue() != 1) {
            throw new IllegalStateException("考试不在进行中");
        }

        jdbcTemplate.update("UPDATE exam_attempt SET status = 2, submit_time = NOW() WHERE id = ?", attemptId);

        BigDecimal totalScore = BigDecimal.ZERO;
        boolean hasSubjective = false;

        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            Long questionId = entry.getKey();
            String answer = entry.getValue();

            Map<String, Object> question = jdbcTemplate.queryForMap("SELECT question_type, correct_answer FROM question WHERE id = ?", questionId);
            String questionType = (String) question.get("question_type");
            String correctAnswer = (String) question.get("correct_answer");

            boolean isCorrect = false;
            BigDecimal score = BigDecimal.ZERO;
            int reviewStatus = 0;

            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(questionType)) {
                isCorrect = answer != null && correctAnswer != null
                        && normalizeObjective(answer).equalsIgnoreCase(normalizeObjective(correctAnswer));
                if (isCorrect) {
                    Map<String, Object> pq = jdbcTemplate.queryForMap("""
                            SELECT pq.score FROM paper_question pq
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

            jdbcTemplate.update("INSERT INTO answer_record (attempt_id, question_id, answer_content, score, is_correct, review_status) VALUES (?, ?, ?, ?, ?, ?)",
                    attemptId, questionId, answer, score, isCorrect, reviewStatus);

            totalScore = totalScore.add(score);
        }

        int finalStatus = hasSubjective ? 4 : 5; // 4: 待批阅, 5: 已完成
        jdbcTemplate.update("UPDATE exam_attempt SET score = ?, status = ? WHERE id = ?", totalScore, finalStatus, attemptId);
        jdbcTemplate.update("DELETE FROM exam_answer_draft WHERE attempt_id = ?", attemptId);

        return Map.of("success", true, "message", "交卷成功", "score", totalScore, "status", finalStatus);
    }

    /**
     * 客观题答案归一化：去首尾空格；多选题（如 "B,A" / "b a"）按字符排序后比较，
     * 使选项顺序、大小写、分隔符差异不影响判分。
     */
    private String normalizeObjective(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().toUpperCase().replaceAll("[\\s,，;；]", "");
        char[] chars = cleaned.toCharArray();
        java.util.Arrays.sort(chars);
        return new String(chars);
    }

    public Map<String, Object> updateExam(Long id, ExamUpdateRequest request, AuthUser user) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        requireOwnedExam(id, user);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("""
                UPDATE exam SET exam_name = ?, description = ?, start_time = ?, end_time = ?, duration_minutes = ?
                WHERE id = ? AND deleted = 0
                """, request.getExamName(), request.getDescription(), request.getStartTime(),
                request.getEndTime(), request.getDurationMinutes(), id);
        return getExamById(id);
    }

    public void deleteExam(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("UPDATE exam SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update("DELETE FROM exam_attempt WHERE exam_id = ? AND status = 0", id);
    }

    public void closeExam(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("UPDATE exam SET end_time = NOW() WHERE id = ? AND deleted = 0", id);
    }

    private void requireOwnedExam(Long id, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_by FROM exam WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("考试不存在");
        }
        if (user.hasRole("ADMIN")) {
            return;
        }
        Object createdBy = rows.get(0).get("created_by");
        if (createdBy == null || !createdBy.toString().equals(String.valueOf(user.getId()))) {
            throw new IllegalArgumentException("只能操作本人创建的考试");
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
