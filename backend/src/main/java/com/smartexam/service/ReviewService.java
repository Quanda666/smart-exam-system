package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ReviewRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;

    public ReviewService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                         TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
    }

    public List<Map<String, Object>> getPendingReviews(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS attemptId, e.exam_name AS examName, u.real_name AS studentName,
                       COUNT(ar.id) AS pendingCount
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                JOIN answer_record ar ON ar.attempt_id = a.id
                WHERE a.status = 4 AND ar.review_status = 0
                """);
        List<Object> params = new ArrayList<>();
        if (!teachingScopeService.hasGlobalScope(user)) {
            sql.append(" AND (e.created_by = ?");
            params.add(user.getId());
            List<Long> studentIds = teachingScopeService.visibleStudentUserIds(user);
            if (!studentIds.isEmpty()) {
                sql.append(" OR a.user_id IN (");
                appendPlaceholders(sql, params, studentIds);
                sql.append(")");
            }
            sql.append(")");
        }
        sql.append("""
                GROUP BY a.id, e.exam_name, u.real_name
                ORDER BY a.id DESC
                """);
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getReviewDetails(Long attemptId, AuthUser user) {
        requireReviewAccess(attemptId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> attemptDetails = jt.queryForMap("""
                SELECT a.id AS attemptId, e.exam_name AS examName, u.real_name AS studentName, a.status
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                WHERE a.id = ?
                """, attemptId);

        List<Map<String, Object>> answers = jt.queryForList("""
                SELECT ar.id AS answerRecordId, q.stem, q.correct_answer AS correctAnswer,
                       ar.answer_content AS studentAnswer, ar.score, ar.is_correct AS isCorrect
                FROM answer_record ar
                JOIN question q ON q.id = ar.question_id
                WHERE ar.attempt_id = ? AND q.question_type = 'SUBJECTIVE'
                """, attemptId);
        attemptDetails.put("answers", answers);
        return attemptDetails;
    }

    @Transactional
    public Map<String, Object> submitReview(Long attemptId, List<ReviewRequest> reviews, AuthUser reviewer) {
        requireReviewAccess(attemptId, reviewer);
        JdbcTemplate jt = requireJdbcTemplate();
        for (ReviewRequest review : reviews) {
            jt.update("""
                    INSERT INTO review_record (answer_record_id, reviewer_id, score, comment)
                    VALUES (?, ?, ?, ?)
                    """, review.getAnswerRecordId(), reviewer.getId(), review.getScore(), review.getComment());
            jt.update("UPDATE answer_record SET score = ?, review_status = 1 WHERE id = ?",
                    review.getScore(), review.getAnswerRecordId());
        }

        BigDecimal totalScore = jt.queryForObject("""
                SELECT SUM(score) FROM answer_record WHERE attempt_id = ?
                """, BigDecimal.class, attemptId);
        jt.update("UPDATE exam_attempt SET score = ?, status = 5 WHERE id = ?", totalScore, attemptId);
        return Map.of("success", true, "message", "Reviewed");
    }

    private void requireReviewAccess(Long attemptId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.created_by, a.user_id
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.id = ? AND e.deleted = 0
                """, attemptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Attempt not found");
        }
        if (teachingScopeService.hasGlobalScope(user)) {
            return;
        }
        Long createdBy = numberValue(rows.get(0).get("created_by"));
        Long studentUserId = numberValue(rows.get(0).get("user_id"));
        if ((createdBy != null && createdBy.equals(user.getId()))
                || teachingScopeService.canAccessStudent(user, studentUserId)) {
            return;
        }
        throw new IllegalArgumentException("Attempt is outside current teaching scope");
    }

    private void appendPlaceholders(StringBuilder sql, List<Object> params, List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(ids.get(i));
        }
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

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }
}
