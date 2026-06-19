package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ReviewRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewService {

    private static final int MAX_REVIEW_COMMENT_LENGTH = 1000;

    private static final String EXPECTED_ANSWER_SCOPE_CONDITION = """
                AND (
                  EXISTS (
                    SELECT 1
                    FROM exam_question_snapshot eqs_scope
                    WHERE eqs_scope.exam_id = e.id
                      AND eqs_scope.question_id = ar.question_id
                  )
                  OR (
                    NOT EXISTS (
                      SELECT 1
                      FROM exam_question_snapshot eqs_scope_exists
                      WHERE eqs_scope_exists.exam_id = e.id
                    )
                    AND EXISTS (
                      SELECT 1
                      FROM paper_question pq_scope
                      WHERE pq_scope.paper_id = e.paper_id
                        AND pq_scope.question_id = ar.question_id
                    )
                  )
                )
                """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;

    public ReviewService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                         TeachingScopeService teachingScopeService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
    }

    public List<Map<String, Object>> getPendingReviews(Long examId, String reviewType, AuthUser user) {
        Long safeExamId = examId == null ? null : requirePositiveExamId(examId);
        String normalizedReviewType = normalizeReviewType(reviewType);
        JdbcTemplate jt = requireJdbcTemplate();
        String openRecheckCondition = openRecheckAnswerCondition();
        String sqlTemplate = """
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName, u.real_name AS studentName,
                       COUNT(ar.id) AS pendingCount,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS recheckTaskCount,
                       CASE WHEN COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) > 0
                            THEN 1 ELSE 0 END AS recheckRequired,
                       (SELECT COUNT(DISTINCT sa_count.id)
                        FROM score_appeal sa_count
                        WHERE sa_count.attempt_id = a.id
                          AND sa_count.status = 1
                          AND sa_count.handling_result = 'RECHECK_REQUIRED') AS recheckAppealCount,
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
                         SELECT COUNT(DISTINCT answered.question_id)
                         FROM answer_record answered
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = answered.question_id
                         WHERE answered.attempt_id = a.id
                           AND answered.answer_content IS NOT NULL
                           AND TRIM(answered.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT answered.question_id)
                         FROM answer_record answered
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = answered.question_id
                         WHERE answered.attempt_id = a.id
                           AND answered.answer_content IS NOT NULL
                           AND TRIM(answered.answer_content) <> ''
                       ) END AS answeredCount,
                       GREATEST(0,
                         COALESCE(NULLIF((SELECT COUNT(*)
                                          FROM exam_question_snapshot eqs_total
                                          WHERE eqs_total.exam_id = e.id), 0),
                                  (SELECT COUNT(*)
                                   FROM paper_question pq_total
                                   WHERE pq_total.paper_id = e.paper_id))
                         -
                         CASE WHEN EXISTS (
                           SELECT 1
                           FROM exam_question_snapshot eqs_exists
                           WHERE eqs_exists.exam_id = e.id
                         ) THEN (
                           SELECT COUNT(DISTINCT answered.question_id)
                           FROM answer_record answered
                           JOIN exam_question_snapshot eqs_answered
                             ON eqs_answered.exam_id = e.id
                            AND eqs_answered.question_id = answered.question_id
                           WHERE answered.attempt_id = a.id
                             AND answered.answer_content IS NOT NULL
                             AND TRIM(answered.answer_content) <> ''
                         ) ELSE (
                           SELECT COUNT(DISTINCT answered.question_id)
                           FROM answer_record answered
                           JOIN paper_question pq_answered
                             ON pq_answered.paper_id = e.paper_id
                            AND pq_answered.question_id = answered.question_id
                           WHERE answered.attempt_id = a.id
                             AND answered.answer_content IS NOT NULL
                             AND TRIM(answered.answer_content) <> ''
                         ) END
                       ) AS unansweredCount
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                JOIN answer_record ar ON ar.attempt_id = a.id
                WHERE a.status = 4 AND ar.review_status = 0
                """;
        StringBuilder sql = new StringBuilder(sqlTemplate.formatted(openRecheckCondition, openRecheckCondition));
        sql.append(EXPECTED_ANSWER_SCOPE_CONDITION);
        List<Object> params = new ArrayList<>();
        if (safeExamId != null) {
            sql.append(" AND e.id = ?");
            params.add(safeExamId);
        }
        if ("RECHECK".equals(normalizedReviewType)) {
            sql.append(" AND ").append(openRecheckCondition);
        } else if ("STANDARD".equals(normalizedReviewType)) {
            sql.append(" AND NOT ").append(openRecheckCondition);
        }
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
                GROUP BY a.id, e.id, e.exam_name, e.paper_id, u.real_name
                ORDER BY a.id DESC
                """);
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listReviewProgress(Long examId, AuthUser user) {
        Long safeExamId = examId == null ? null : requirePositiveExamId(examId);
        JdbcTemplate jt = requireJdbcTemplate();
        String openRecheckCondition = openRecheckAnswerCondition();
        StringBuilder sql = new StringBuilder("""
                SELECT e.id AS examId,
                       e.exam_name AS examName,
                       e.status AS examStatus,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       COUNT(DISTINCT a.id) AS attemptCount,
                       COUNT(DISTINCT CASE WHEN a.status = 4 THEN a.id END) AS pendingAttemptCount,
                       COUNT(DISTINCT CASE WHEN a.status = 5 THEN a.id END) AS completedAttemptCount,
                       COUNT(DISTINCT CASE WHEN a.status = 1 THEN a.id END) AS activeAttemptCount,
                       COALESCE(SUM(CASE WHEN ar.review_status = 0 THEN 1 ELSE 0 END), 0) AS pendingAnswerCount,
                       COALESCE(SUM(CASE WHEN ar.review_status = 0 AND """ + openRecheckCondition + """ THEN 1 ELSE 0 END), 0) AS pendingRecheckAnswerCount,
                       COALESCE(SUM(CASE WHEN ar.review_status = 1 THEN 1 ELSE 0 END), 0) AS reviewedAnswerCount,
                       COUNT(ar.id) AS reviewableAnswerCount,
                       ROUND(
                         CASE WHEN COUNT(ar.id) = 0 THEN 100
                              ELSE COALESCE(SUM(CASE WHEN ar.review_status = 1 THEN 1 ELSE 0 END), 0) * 100 / COUNT(ar.id)
                         END,
                         2
                       ) AS progressPercent,
                       MIN(CASE WHEN ar.review_status = 0 AND a.status = 4 THEN a.id END) AS firstPendingAttemptId,
                       MIN(CASE WHEN ar.review_status = 0 AND """ + openRecheckCondition + """ THEN a.id END) AS firstRecheckAttemptId,
                       MIN(CASE WHEN ar.review_status = 0 THEN a.submit_time END) AS oldestPendingSubmitAt,
                       (SELECT COUNT(DISTINCT sa_count.id)
                        FROM score_appeal sa_count
                        LEFT JOIN exam_attempt sa_attempt ON sa_attempt.id = sa_count.attempt_id
                        WHERE ((sa_attempt.id IS NOT NULL AND sa_attempt.exam_id = e.id)
                            OR (sa_attempt.id IS NULL AND sa_count.exam_id = e.id))
                          AND sa_count.status = 1
                          AND sa_count.handling_result = 'RECHECK_REQUIRED') AS recheckAppealCount,
                       (SELECT MAX(rr.created_at)
                        FROM review_record rr
                        JOIN answer_record rr_ar ON rr_ar.id = rr.answer_record_id
                        JOIN exam_attempt rr_a ON rr_a.id = rr_ar.attempt_id
                        WHERE rr_a.exam_id = e.id) AS latestReviewedAt,
                       CASE
                         WHEN COALESCE(SUM(CASE WHEN ar.review_status = 0 THEN 1 ELSE 0 END), 0) > 0
                              OR COUNT(DISTINCT CASE WHEN a.status = 4 THEN a.id END) > 0 THEN 'PENDING'
                         ELSE 'COMPLETE'
                       END AS reviewState,
                       CASE
                         WHEN COALESCE(SUM(CASE WHEN ar.review_status = 0 THEN 1 ELSE 0 END), 0) > 0
                              OR COUNT(DISTINCT CASE WHEN a.status = 4 THEN a.id END) > 0 THEN 1
                         ELSE 0
                       END AS blocksScoreRelease
                FROM exam e
                JOIN exam_attempt a ON a.exam_id = e.id
                JOIN answer_record ar ON ar.attempt_id = a.id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE e.deleted = 0
                """);
        sql.append(EXPECTED_ANSWER_SCOPE_CONDITION);
        List<Object> params = new ArrayList<>();
        if (safeExamId != null) {
            sql.append(" AND e.id = ?");
            params.add(safeExamId);
        }
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
                GROUP BY e.id, e.exam_name, e.status, sr.status
                ORDER BY blocksScoreRelease DESC, pendingAnswerCount DESC, e.id DESC
                """);
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> getReviewDetails(Long attemptId, AuthUser user) {
        requireReviewAccess(attemptId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        requireAttemptPendingReview(jt, attemptId);
        Map<String, Object> attemptDetails = jt.queryForMap("""
                SELECT a.id AS attemptId, e.exam_name AS examName, u.real_name AS studentName, a.status,
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
                         SELECT COUNT(DISTINCT answered.question_id)
                         FROM answer_record answered
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = answered.question_id
                         WHERE answered.attempt_id = a.id
                           AND answered.answer_content IS NOT NULL
                           AND TRIM(answered.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT answered.question_id)
                         FROM answer_record answered
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = answered.question_id
                         WHERE answered.attempt_id = a.id
                           AND answered.answer_content IS NOT NULL
                           AND TRIM(answered.answer_content) <> ''
                       ) END AS answeredCount,
                       GREATEST(0,
                         COALESCE(NULLIF((SELECT COUNT(*)
                                          FROM exam_question_snapshot eqs_total
                                          WHERE eqs_total.exam_id = e.id), 0),
                                  (SELECT COUNT(*)
                                   FROM paper_question pq_total
                                   WHERE pq_total.paper_id = e.paper_id))
                         -
                         CASE WHEN EXISTS (
                           SELECT 1
                           FROM exam_question_snapshot eqs_exists
                           WHERE eqs_exists.exam_id = e.id
                         ) THEN (
                           SELECT COUNT(DISTINCT answered.question_id)
                           FROM answer_record answered
                           JOIN exam_question_snapshot eqs_answered
                             ON eqs_answered.exam_id = e.id
                            AND eqs_answered.question_id = answered.question_id
                           WHERE answered.attempt_id = a.id
                             AND answered.answer_content IS NOT NULL
                             AND TRIM(answered.answer_content) <> ''
                         ) ELSE (
                           SELECT COUNT(DISTINCT answered.question_id)
                           FROM answer_record answered
                           JOIN paper_question pq_answered
                             ON pq_answered.paper_id = e.paper_id
                            AND pq_answered.question_id = answered.question_id
                           WHERE answered.attempt_id = a.id
                             AND answered.answer_content IS NOT NULL
                             AND TRIM(answered.answer_content) <> ''
                         ) END
                       ) AS unansweredCount
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                WHERE a.id = ?
                """, attemptId);

        List<Map<String, Object>> answers = jt.queryForList("""
                SELECT ar.id AS answerRecordId, q.id AS questionId,
                       COALESCE(eqs.question_type, q.question_type) AS questionType,
                       COALESCE(eqs.stem, q.stem) AS stem,
                       COALESCE(eqs.correct_answer, q.correct_answer) AS correctAnswer,
                       COALESCE(eqs.score, pq.score) AS maxScore,
                       ar.answer_content AS studentAnswer, ar.score, ar.is_correct AS isCorrect
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                JOIN exam e ON e.id = a.exam_id
                LEFT JOIN paper_question pq ON pq.paper_id = e.paper_id AND pq.question_id = ar.question_id
                JOIN question q ON q.id = ar.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                WHERE ar.attempt_id = ? AND ar.review_status = 0
                  AND (
                    eqs.question_id IS NOT NULL
                    OR (
                      NOT EXISTS (
                        SELECT 1
                        FROM exam_question_snapshot eqs_scope_exists
                        WHERE eqs_scope_exists.exam_id = e.id
                      )
                      AND pq.question_id IS NOT NULL
                    )
                  )
                ORDER BY COALESCE(eqs.sort_order, pq.sort_order), ar.id
                """, attemptId);
        attemptDetails.put("answers", answers);
        return attemptDetails;
    }

    @Transactional
    public Map<String, Object> submitReview(Long attemptId, List<ReviewRequest> reviews, AuthUser reviewer) {
        requireReviewAccess(attemptId, reviewer);
        JdbcTemplate jt = requireJdbcTemplate();
        requireAttemptPendingReviewForUpdate(jt, attemptId);
        if (reviews == null || reviews.isEmpty()) {
            throw new IllegalArgumentException("Review items cannot be empty");
        }
        Map<Long, ReviewAnswerAuditContext> pendingAnswers = loadPendingAnswerScores(jt, attemptId);
        if (pendingAnswers.isEmpty()) {
            throw new IllegalStateException("No pending answers are available for review");
        }
        Set<Long> requestedAnswerIds = new HashSet<>();
        Map<Long, String> normalizedComments = new HashMap<>();
        for (ReviewRequest review : reviews) {
            if (review == null || review.getAnswerRecordId() == null || review.getScore() == null) {
                throw new IllegalArgumentException("Review item answerRecordId and score are required");
            }
            Long answerRecordId = review.getAnswerRecordId();
            if (answerRecordId <= 0) {
                throw new IllegalArgumentException("answerRecordId must be positive");
            }
            if (!requestedAnswerIds.add(answerRecordId)) {
                throw new IllegalArgumentException("Duplicate review item for the same answer record");
            }
            ReviewAnswerAuditContext answer = pendingAnswers.get(answerRecordId);
            if (answer == null) {
                throw new IllegalArgumentException("Answer record is not pending review for this attempt");
            }
            if (review.getScore().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Review score cannot be negative");
            }
            if (review.getScore().compareTo(answer.maxScore()) > 0) {
                throw new IllegalArgumentException("Review score cannot exceed question score");
            }
            normalizedComments.put(answerRecordId, normalizeReviewComment(review.getComment()));
        }
        if (!requestedAnswerIds.containsAll(pendingAnswers.keySet())
                || requestedAnswerIds.size() != pendingAnswers.size()) {
            throw new IllegalArgumentException("Review submission must cover all pending answers for this attempt");
        }

        List<Long> reviewScoreLogIds = new ArrayList<>();
        for (ReviewRequest review : reviews) {
            ReviewAnswerAuditContext answer = pendingAnswers.get(review.getAnswerRecordId());
            String comment = normalizedComments.get(review.getAnswerRecordId());
            boolean reviewedCorrect = review.getScore().compareTo(answer.maxScore()) >= 0;
            jt.update("""
                    INSERT INTO review_record (answer_record_id, reviewer_id, score, comment)
                    VALUES (?, ?, ?, ?)
                    """, review.getAnswerRecordId(), reviewer.getId(), review.getScore(), comment);
            Long reviewScoreLogId = recordReviewScoreLog(jt, answer, review, comment, reviewer);
            if (reviewScoreLogId != null) {
                reviewScoreLogIds.add(reviewScoreLogId);
            }
            jt.update("UPDATE answer_record SET score = ?, is_correct = ?, review_status = 1 WHERE id = ?",
                    review.getScore(), reviewedCorrect, review.getAnswerRecordId());
        }

        BigDecimal totalScore = jt.queryForObject("""
                SELECT COALESCE(SUM(ar.score), 0)
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                JOIN exam e ON e.id = a.exam_id
                WHERE ar.attempt_id = ?
                  AND (
                    EXISTS (
                      SELECT 1
                      FROM exam_question_snapshot eqs_scope
                      WHERE eqs_scope.exam_id = e.id
                        AND eqs_scope.question_id = ar.question_id
                    )
                    OR (
                      NOT EXISTS (
                        SELECT 1
                        FROM exam_question_snapshot eqs_scope_exists
                        WHERE eqs_scope_exists.exam_id = e.id
                      )
                      AND EXISTS (
                        SELECT 1
                        FROM paper_question pq_scope
                        WHERE pq_scope.paper_id = e.paper_id
                          AND pq_scope.question_id = ar.question_id
                      )
                    )
                  )
                """, BigDecimal.class, attemptId);
        Integer pendingCount = countPendingExpectedReviewAnswers(jt, attemptId);
        jt.update("UPDATE exam_attempt SET score = ?, status = ? WHERE id = ?",
                totalScore, pendingCount != null && pendingCount > 0 ? 4 : 5, attemptId);
        ReviewAnswerAuditContext firstAnswer = pendingAnswers.values().iterator().next();
        Long examId = firstAnswer.examId();
        Map<String, Object> handoff = reviewReleaseHandoff(jt, examId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Reviewed");
        result.put("attemptId", attemptId);
        result.put("examId", examId);
        result.put("examName", handoff.get("examName"));
        result.put("reviewedCount", reviews.size());
        result.put("pendingCount", pendingCount == null ? 0 : pendingCount);
        result.put("status", pendingCount != null && pendingCount > 0 ? 4 : 5);
        result.put("score", totalScore);
        result.put("reviewScoreLogIds", reviewScoreLogIds);
        result.putAll(handoff);
        return result;
    }

    public List<Map<String, Object>> listReviewScoreLogs(Long attemptId, AuthUser user) {
        requireReviewAccess(attemptId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT l.id, l.attempt_id AS attemptId, l.answer_record_id AS answerRecordId,
                       l.question_id AS questionId, l.exam_id AS examId, e.exam_name AS examName,
                       l.user_id AS userId, stu.real_name AS studentName,
                       l.old_score AS oldScore, l.new_score AS newScore, l.max_score AS maxScore,
                       l.comment, l.reviewer_id AS reviewerId, reviewer.real_name AS reviewerName,
                       l.created_at AS createdAt
                FROM review_score_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user stu ON stu.id = l.user_id
                LEFT JOIN sys_user reviewer ON reviewer.id = l.reviewer_id
                WHERE l.attempt_id = ?
                ORDER BY l.created_at DESC, l.id DESC
                """, attemptId);
    }

    public ExportFile exportReviewScoreLogs(Long attemptId, AuthUser user) {
        List<Map<String, Object>> logs = listReviewScoreLogs(attemptId, user);
        String examName = logs.isEmpty() ? "attempt-" + attemptId : String.valueOf(logs.get(0).get("examName"));
        List<String> headers = List.of(
                "Log ID", "Time", "Exam", "Student", "Attempt ID", "Answer Record ID", "Question ID",
                "Old Score", "New Score", "Max Score", "Reviewer", "Comment"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    nullable(log.get("id")),
                    nullable(log.get("createdAt")),
                    nullable(log.get("examName")),
                    nullable(log.get("studentName")),
                    nullable(log.get("attemptId")),
                    nullable(log.get("answerRecordId")),
                    nullable(log.get("questionId")),
                    nullable(log.get("oldScore")),
                    nullable(log.get("newScore")),
                    nullable(log.get("maxScore")),
                    nullable(log.get("reviewerName")),
                    nullable(log.get("comment"))
            ));
        }
        return new ExportFile(safeExportName(examName) + "-review-score-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private Map<Long, ReviewAnswerAuditContext> loadPendingAnswerScores(JdbcTemplate jt, Long attemptId) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT ar.id AS answerRecordId, ar.question_id AS questionId, a.exam_id AS examId,
                       a.user_id AS userId, ar.score AS oldScore, COALESCE(eqs.score, pq.score, 0) AS maxScore
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                JOIN exam e ON e.id = a.exam_id
                LEFT JOIN paper_question pq ON pq.paper_id = e.paper_id AND pq.question_id = ar.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                WHERE ar.attempt_id = ? AND ar.review_status = 0
                  AND (
                    eqs.question_id IS NOT NULL
                    OR (
                      NOT EXISTS (
                        SELECT 1
                        FROM exam_question_snapshot eqs_scope_exists
                        WHERE eqs_scope_exists.exam_id = e.id
                      )
                      AND pq.question_id IS NOT NULL
                    )
                  )
                """, attemptId);
        Map<Long, ReviewAnswerAuditContext> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long answerRecordId = numberValue(row.get("answerRecordId"));
            result.put(answerRecordId, new ReviewAnswerAuditContext(
                    attemptId,
                    answerRecordId,
                    numberValue(row.get("questionId")),
                    numberValue(row.get("examId")),
                    numberValue(row.get("userId")),
                    decimalValue(row.get("oldScore")),
                    decimalValue(row.get("maxScore"))
            ));
        }
        return result;
    }

    private Integer countPendingExpectedReviewAnswers(JdbcTemplate jt, Long attemptId) {
        return jt.queryForObject("""
                SELECT COUNT(*)
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                JOIN exam e ON e.id = a.exam_id
                WHERE ar.attempt_id = ? AND ar.review_status = 0
                """ + EXPECTED_ANSWER_SCOPE_CONDITION, Integer.class, attemptId);
    }

    private Map<String, Object> reviewReleaseHandoff(JdbcTemplate jt, Long examId) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.exam_name AS examName,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS examPendingReviewAttemptCount,
                       (SELECT COUNT(*)
                        FROM answer_record ar
                        JOIN exam_attempt a ON a.id = ar.attempt_id
                        WHERE a.exam_id = e.id AND ar.review_status = 0) AS examPendingReviewAnswerCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS examActiveAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS examUnscoredCompletedAttemptCount,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus
                FROM exam e
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE e.id = ? AND e.deleted = 0
                """, examId);
        if (rows.isEmpty()) {
            return Map.of(
                    "examPendingReviewAttemptCount", 0,
                    "examPendingReviewAnswerCount", 0,
                    "examActiveAttemptCount", 0,
                    "examUnscoredCompletedAttemptCount", 0,
                    "scoreReleaseStatus", 0,
                    "examReviewComplete", false
            );
        }
        Map<String, Object> row = rows.get(0);
        long pendingAttempts = longValue(row.get("examPendingReviewAttemptCount"), 0L);
        long pendingAnswers = longValue(row.get("examPendingReviewAnswerCount"), 0L);
        long activeAttempts = longValue(row.get("examActiveAttemptCount"), 0L);
        long unscoredCompleted = longValue(row.get("examUnscoredCompletedAttemptCount"), 0L);
        long releaseStatus = longValue(row.get("scoreReleaseStatus"), 0L);
        Map<String, Object> result = new HashMap<>();
        result.put("examName", row.get("examName"));
        result.put("examPendingReviewAttemptCount", pendingAttempts);
        result.put("examPendingReviewAnswerCount", pendingAnswers);
        result.put("examActiveAttemptCount", activeAttempts);
        result.put("examUnscoredCompletedAttemptCount", unscoredCompleted);
        result.put("scoreReleaseStatus", releaseStatus);
        result.put("examReviewComplete", pendingAttempts == 0L && pendingAnswers == 0L);
        result.put("scoreReleaseHandoffReady", pendingAttempts == 0L
                && pendingAnswers == 0L
                && activeAttempts == 0L
                && unscoredCompleted == 0L
                && releaseStatus != 1L);
        return result;
    }

    private void requireAttemptPendingReview(JdbcTemplate jt, Long attemptId) {
        Integer pending = jt.queryForObject("""
                SELECT CASE WHEN status = 4 THEN 1 ELSE 0 END
                FROM exam_attempt
                WHERE id = ?
                """, Integer.class, attemptId);
        if (pending == null || pending == 0) {
            throw new IllegalStateException("Attempt is not pending review");
        }
    }

    private void requireAttemptPendingReviewForUpdate(JdbcTemplate jt, Long attemptId) {
        Integer pending = jt.queryForObject("""
                SELECT CASE WHEN status = 4 THEN 1 ELSE 0 END
                FROM exam_attempt
                WHERE id = ?
                FOR UPDATE
                """, Integer.class, attemptId);
        if (pending == null || pending == 0) {
            throw new IllegalStateException("Attempt is not pending review");
        }
    }

    private Long recordReviewScoreLog(JdbcTemplate jt, ReviewAnswerAuditContext answer,
                                      ReviewRequest review, String comment, AuthUser reviewer) {
        ReviewAnswerAuditContext resolved = resolveReviewScoreLogContext(jt, answer);
        jt.update("""
                INSERT INTO review_score_log
                    (attempt_id, answer_record_id, question_id, exam_id, user_id,
                     old_score, new_score, max_score, comment, reviewer_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                resolved.attemptId(), resolved.answerRecordId(), resolved.questionId(), resolved.examId(), resolved.userId(),
                resolved.oldScore(), review.getScore(), resolved.maxScore(), comment, reviewer.getId());
        return jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private ReviewAnswerAuditContext resolveReviewScoreLogContext(JdbcTemplate jt, ReviewAnswerAuditContext fallback) {
        if (fallback.answerRecordId() == null) {
            return fallback;
        }
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT ar.attempt_id AS attemptId, ar.question_id AS questionId,
                       a.exam_id AS examId, a.user_id AS userId
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                WHERE ar.id = ?
                """, fallback.answerRecordId());
        if (rows.isEmpty()) {
            return fallback;
        }
        Map<String, Object> row = rows.get(0);
        return new ReviewAnswerAuditContext(
                numberValue(row.get("attemptId")),
                fallback.answerRecordId(),
                numberValue(row.get("questionId")),
                numberValue(row.get("examId")),
                numberValue(row.get("userId")),
                fallback.oldScore(),
                fallback.maxScore()
        );
    }

    private void requireReviewAccess(Long attemptId, AuthUser user) {
        attemptId = requirePositiveAttemptId(attemptId);
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

    private Long requirePositiveAttemptId(Long attemptId) {
        if (attemptId == null || attemptId <= 0) {
            throw new IllegalArgumentException("attemptId must be positive");
        }
        return attemptId;
    }

    private Long requirePositiveExamId(Long examId) {
        if (examId == null || examId <= 0) {
            throw new IllegalArgumentException("examId must be positive");
        }
        return examId;
    }

    private String normalizeReviewType(String reviewType) {
        if (reviewType == null || reviewType.isBlank()) {
            return "";
        }
        String normalized = reviewType.trim().toUpperCase(Locale.ROOT);
        if ("RECHECK".equals(normalized) || "STANDARD".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("reviewType must be RECHECK or STANDARD");
    }

    private String openRecheckAnswerCondition() {
        return """
                EXISTS (
                  SELECT 1
                  FROM score_appeal sa_recheck
                  WHERE sa_recheck.attempt_id = a.id
                    AND sa_recheck.status = 1
                    AND sa_recheck.handling_result = 'RECHECK_REQUIRED'
                    AND (sa_recheck.question_id IS NULL OR sa_recheck.question_id = ar.question_id)
                )
                """;
    }

    private String normalizeReviewComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > MAX_REVIEW_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Review comment must be 1000 characters or less");
        }
        return normalized;
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

    private long longValue(Object value, long fallback) {
        Long parsed = numberValue(value);
        return parsed == null ? fallback : parsed;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String safeExportName(String value) {
        String normalized = value == null ? "review-score-log" : value.trim();
        if (normalized.isEmpty()) {
            normalized = "review-score-log";
        }
        return normalized.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }

    private record ReviewAnswerAuditContext(Long attemptId,
                                            Long answerRecordId,
                                            Long questionId,
                                            Long examId,
                                            Long userId,
                                            BigDecimal oldScore,
                                            BigDecimal maxScore) {
    }
}
