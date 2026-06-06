package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ReviewRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public ReviewService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<Map<String, Object>> getPendingReviews(AuthUser user) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        return jdbcTemplate.queryForList("""
            SELECT a.id as attemptId, e.exam_name AS examName, u.real_name AS studentName, COUNT(ar.id) AS pendingCount
            FROM exam_attempt a
            JOIN exam e ON e.id = a.exam_id
            JOIN sys_user u ON u.id = a.user_id
            JOIN answer_record ar ON ar.attempt_id = a.id
            WHERE e.created_by = ? AND a.status = 4 AND ar.review_status = 0
            GROUP BY a.id, e.exam_name, u.real_name
            ORDER BY a.id DESC
            """, user.getId());
    }

    public Map<String, Object> getReviewDetails(Long attemptId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        Map<String, Object> attemptDetails = jdbcTemplate.queryForMap("""
            SELECT a.id as attemptId, e.exam_name AS examName, u.real_name AS studentName, a.status
            FROM exam_attempt a
            JOIN exam e ON e.id = a.exam_id
            JOIN sys_user u ON u.id = a.user_id
            WHERE a.id = ?
            """, attemptId);

        List<Map<String, Object>> answers = jdbcTemplate.queryForList("""
            SELECT ar.id as answerRecordId, q.stem, q.correct_answer AS correctAnswer, ar.answer_content AS studentAnswer,
                   ar.score, ar.is_correct AS isCorrect
            FROM answer_record ar
            JOIN question q ON q.id = ar.question_id
            WHERE ar.attempt_id = ? AND q.question_type = 'SUBJECTIVE'
            """, attemptId);

        attemptDetails.put("answers", answers);
        return attemptDetails;
    }

    @Transactional
    public Map<String, Object> submitReview(Long attemptId, List<ReviewRequest> reviews, AuthUser reviewer) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        for (ReviewRequest review : reviews) {
            jdbcTemplate.update("INSERT INTO review_record (answer_record_id, reviewer_id, score, comment) VALUES (?, ?, ?, ?)",
                    review.getAnswerRecordId(), reviewer.getId(), review.getScore(), review.getComment());
            jdbcTemplate.update("UPDATE answer_record SET score = ?, review_status = 1 WHERE id = ?",
                    review.getScore(), review.getAnswerRecordId());
        }

        BigDecimal totalScore = jdbcTemplate.queryForObject("SELECT SUM(score) FROM answer_record WHERE attempt_id = ?", BigDecimal.class, attemptId);
        jdbcTemplate.update("UPDATE exam_attempt SET score = ?, status = 5 WHERE id = ?", totalScore, attemptId);

        return Map.of("success", true, "message", "批阅完成");
    }
}
