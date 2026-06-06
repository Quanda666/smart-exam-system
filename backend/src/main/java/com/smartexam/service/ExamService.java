package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.ExamRequest;
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

    public ExamService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
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
            WHERE a.user_id = ?
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
        
        List<Long> studentIds = jdbcTemplate.queryForList("SELECT user_id FROM student_profile WHERE class_id IN (?)", Long.class, request.getClassIds());
        for (Long studentId : studentIds) {
            jdbcTemplate.update("INSERT INTO exam_attempt (exam_id, user_id, status) VALUES (?, ?, 0)", examId, studentId);
        }

        return getExamById(examId);
    }

    private void validateExamRequest(ExamRequest request) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        // Other validations can be added here
    }

    public Map<String, Object> getExamById(Long examId) {
         JdbcTemplate jdbcTemplate = requireJdbcTemplate();
         return jdbcTemplate.queryForMap("SELECT * FROM exam WHERE id = ?", examId);
    }

    @Transactional
    public Map<String, Object> startExam(Long attemptId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> attempt = jdbcTemplate.queryForMap("SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?", attemptId, user.getId());
        
        if ((Integer)attempt.get("status") != 0) {
            throw new IllegalStateException("考试已开始或已结束");
        }

        jdbcTemplate.update("UPDATE exam_attempt SET status = 1, start_time = NOW() WHERE id = ?", attemptId);
        
        // Return exam details including questions
        Long examId = (Long)attempt.get("exam_id");
        Map<String,Object> exam = jdbcTemplate.queryForMap("SELECT e.*, p.total_score, p.paper_name FROM exam e JOIN paper p ON e.paper_id = p.id WHERE e.id = ?", examId);
        List<Map<String,Object>> questions = jdbcTemplate.queryForList("""
            SELECT q.id, q.question_type, q.stem, q.difficulty, pq.score FROM paper_question pq 
            JOIN question q ON pq.question_id = q.id WHERE pq.paper_id = ? ORDER BY pq.sort_order
            """, exam.get("paper_id"));
        
        for(Map<String,Object> q : questions) {
            if(List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(q.get("question_type"))) {
                q.put("options", jdbcTemplate.queryForList("SELECT option_label, option_content FROM question_option WHERE question_id = ? ORDER BY sort_order", q.get("id")));
            }
        }
        exam.put("questions", questions);
        return exam;
    }

    @Transactional
    public Map<String, Object> submitExam(Long attemptId, Map<Long, String> answers, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> attempt = jdbcTemplate.queryForMap("SELECT * FROM exam_attempt WHERE id = ? AND user_id = ?", attemptId, user.getId());

        if ((Integer)attempt.get("status") != 1) {
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
                isCorrect = answer != null && answer.equalsIgnoreCase(correctAnswer);
                if(isCorrect) {
                    Map<String,Object> pq = jdbcTemplate.queryForMap("SELECT score FROM paper_question pq JOIN exam_attempt ea ON ea.exam_id=pq.paper_id WHERE ea.id=? AND pq.question_id = ?", attemptId, questionId);
                    score = (BigDecimal)pq.get("score");
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
        
        return Map.of("success", true, "message", "交卷成功", "score", totalScore, "status", finalStatus);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
