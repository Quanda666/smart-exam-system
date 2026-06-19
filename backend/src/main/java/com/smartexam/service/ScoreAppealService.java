package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.review.ScoreAppealRecheckCloseRequest;
import com.smartexam.dto.review.ScoreAppealReplyRequest;
import com.smartexam.dto.student.ScoreAppealRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ScoreAppealService {

    private static final int MAX_APPEAL_TEXT_LENGTH = 1000;

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
    private final NotificationService notificationService;

    public ScoreAppealService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                              TeachingScopeService teachingScopeService,
                              NotificationService notificationService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> submitAppeal(ScoreAppealRequest request, AuthUser student) {
        validateAppealRequestIds(request);
        String reason = normalizeRequiredAppealText(request.getReason(), "appeal reason");
        JdbcTemplate jt = requireJdbcTemplate();
        if (!configBoolean(jt, "score.appealEnabled", true)) {
            throw new IllegalStateException("Score appeal is disabled");
        }
        List<Map<String, Object>> attempts = jt.queryForList("""
                SELECT a.id AS attemptId, a.exam_id AS examId, e.exam_name AS examName,
                       e.created_by AS examOwnerId, sr.published_at AS publishedAt
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE a.id = ? AND a.user_id = ? AND a.status = 5 AND a.score IS NOT NULL
                FOR UPDATE
                """, request.getAttemptId(), student.getId());
        if (attempts.isEmpty()) {
            throw new IllegalStateException("Score has not been released for appeal");
        }
        int appealWindowDays = configNumber(jt, "score.appealWindowDays", 7);
        LocalDateTime publishedAt = dateTimeValue(attempts.get(0).get("publishedAt"));
        if (appealWindowDays > 0 && publishedAt != null
                && publishedAt.plusDays(appealWindowDays).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Score appeal window has expired");
        }
        if (request.getQuestionId() != null) {
            Integer questionExists = jt.queryForObject("""
                    SELECT COUNT(*)
                    FROM answer_record ar
                    JOIN exam_attempt a ON a.id = ar.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    WHERE ar.attempt_id = ? AND ar.question_id = ?
                    """ + EXPECTED_ANSWER_SCOPE_CONDITION, Integer.class,
                    request.getAttemptId(), request.getQuestionId());
            if (questionExists == null || questionExists == 0) {
                throw new IllegalArgumentException("Appeal question does not belong to this attempt");
            }
        }
        Integer activeDuplicate = jt.queryForObject("""
                SELECT COUNT(*)
                FROM score_appeal
                WHERE attempt_id = ? AND user_id = ? AND status IN (0, 1)
                  AND (? IS NULL OR question_id IS NULL OR question_id = ?)
                """, Integer.class, request.getAttemptId(), student.getId(), request.getQuestionId(), request.getQuestionId());
        if (activeDuplicate != null && activeDuplicate > 0) {
            throw new IllegalStateException("An appeal already exists for this attempt or question");
        }

        Long examId = numberValue(attempts.get(0).get("examId"));
        jt.update("""
                INSERT INTO score_appeal (attempt_id, exam_id, question_id, user_id, reason, status)
                VALUES (?, ?, ?, ?, ?, 0)
                """, request.getAttemptId(), examId, request.getQuestionId(), student.getId(), reason);
        Long appealId = jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Long submitLogId = recordAppealLog(jt, appealId, request.getAttemptId(), examId, request.getQuestionId(), student.getId(),
                "SUBMIT", null, 0, null, reason, student.getId());
        notifyTeachersForNewAppeal(jt, attempts.get(0), appealId, request.getQuestionId(), student);
        Map<String, Object> result = getAppealById(jt, appealId);
        result.put("scoreAppealLogId", submitLogId);
        result.put("scoreAppealLogIds", logIds(submitLogId));
        return result;
    }

    public List<Map<String, Object>> listMyAppeals(AuthUser student) {
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList(baseAppealSelect() + """
                WHERE sa.user_id = ? AND e.deleted = 0
                ORDER BY sa.created_at DESC
                """, student.getId());
    }

    public List<Map<String, Object>> listMyAppealLogs(Long id, AuthUser student) {
        JdbcTemplate jt = requireJdbcTemplate();
        requireStudentAppealAccess(jt, id, student);
        return appealLogs(jt, id);
    }

    public ExportFile exportMyAppealLogs(Long id, AuthUser student) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireStudentAppealAccess(jt, id, student);
        return buildAppealLogsExport(id, appeal, appealLogs(jt, id));
    }

    public Map<String, Object> studentAppealEvidence(Long id, AuthUser student) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireStudentAppealAccess(jt, id, student);
        if (!"RECHECK_REQUIRED".equals(String.valueOf(appeal.get("handlingResult")))) {
            throw new IllegalStateException("Only recheck-required appeals have score review evidence");
        }
        if (intValue(appeal.get("status"), -1) != 2) {
            throw new IllegalStateException("Recheck evidence is available after recheck is closed");
        }
        requireStudentAppealEvidenceVisible(jt, appeal, student);

        List<Map<String, Object>> answers = recheckAnswerEvidence(jt, appeal);
        int reviewedCount = 0;
        int pendingCount = 0;
        int reviewLogCount = 0;
        for (Map<String, Object> answer : answers) {
            if (intValue(answer.get("reviewStatus"), 0) == 1) {
                reviewedCount++;
            } else {
                pendingCount++;
            }
            if (answer.get("reviewScoreLogId") != null) {
                reviewLogCount++;
            }
        }

        Map<String, Object> result = new HashMap<>(appeal);
        result.put("requiredRecheckAnswerCount", answers.size());
        result.put("reviewedRecheckAnswerCount", reviewedCount);
        result.put("pendingRecheckAnswerCount", pendingCount);
        result.put("reviewScoreLogCount", reviewLogCount);
        result.put("recheckOpenedAt", recheckOpenedAt(jt, id));
        result.put("answers", answers);
        result.put("logs", appealLogs(jt, id));
        result.put("evidenceAvailable", 1);
        return result;
    }

    public List<Map<String, Object>> listAppeals(Integer status, String handlingResult, Long appealId, AuthUser handler) {
        return listAppeals(status, handlingResult, appealId, null, handler);
    }

    public List<Map<String, Object>> listAppeals(Integer status, String handlingResult, Long appealId, Long examId, AuthUser handler) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(baseAppealSelect());
        sql.append(" WHERE e.deleted = 0");
        if (appealId != null) {
            requirePositiveAppealId(appealId);
            sql.append(" AND sa.id = ?");
            params.add(appealId);
        }
        if (examId != null) {
            requirePositiveExamId(examId);
            sql.append(" AND e.id = ?");
            params.add(examId);
        }
        if (status != null) {
            sql.append(" AND sa.status = ?");
            params.add(status);
        }
        String normalizedHandlingResult = normalizeOptionalHandlingResult(handlingResult);
        if (normalizedHandlingResult != null) {
            sql.append(" AND sa.handling_result = ?");
            params.add(normalizedHandlingResult);
        }
        appendTeachingScope(sql, params, handler);
        sql.append(" ORDER BY sa.created_at DESC");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listAppealLogs(Long id, AuthUser handler) {
        JdbcTemplate jt = requireJdbcTemplate();
        requireAppealAccess(jt, id, handler);
        return appealLogs(jt, id);
    }

    private List<Map<String, Object>> appealLogs(JdbcTemplate jt, Long id) {
        return jt.queryForList("""
                SELECT l.id, l.appeal_id AS appealId, l.attempt_id AS attemptId, l.exam_id AS examId,
                       l.question_id AS questionId, l.user_id AS userId,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.handling_result AS handlingResult, l.note,
                       l.actor_id AS actorId,
                       COALESCE(actor.real_name, actor.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.created_at AS createdAt
                FROM score_appeal_log l
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                WHERE l.appeal_id = ?
                ORDER BY l.created_at ASC, l.id ASC
                """, id);
    }

    public ExportFile exportAppealLogs(Long id, AuthUser handler) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireAppealAccess(jt, id, handler);
        return buildAppealLogsExport(id, appeal, appealLogs(jt, id));
    }

    public Map<String, Object> recheckReadiness(Long id, AuthUser handler) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireAppealAccess(jt, id, handler);
        List<Map<String, Object>> answers = recheckAnswerEvidence(jt, appeal);
        int pendingCount = 0;
        int reviewedCount = 0;
        int reviewLogCount = 0;
        for (Map<String, Object> answer : answers) {
            int reviewStatus = intValue(answer.get("reviewStatus"), 0);
            if (reviewStatus == 0) {
                pendingCount++;
            } else {
                reviewedCount++;
            }
            if (answer.get("reviewScoreLogId") != null) {
                reviewLogCount++;
            }
        }

        boolean openRecheckAppeal = intValue(appeal.get("status"), -1) == 1
                && "RECHECK_REQUIRED".equals(String.valueOf(appeal.get("handlingResult")));
        boolean finalized = isRecheckAttemptFinalized(jt, appeal);
        List<String> closeBlockers = new ArrayList<>();
        if (!openRecheckAppeal) {
            closeBlockers.add("APPEAL_NOT_OPEN_RECHECK");
        }
        if (answers.isEmpty()) {
            closeBlockers.add("NO_RECHECK_ANSWERS");
        }
        if (pendingCount > 0) {
            closeBlockers.add("PENDING_RECHECK_ANSWERS");
        }
        if (!finalized) {
            closeBlockers.add("ATTEMPT_NOT_FINALIZED");
        }
        if (reviewLogCount == 0) {
            closeBlockers.add("NO_REVIEW_SCORE_LOGS");
        }

        Map<String, Object> result = new HashMap<>(appeal);
        result.put("requiredRecheckAnswerCount", answers.size());
        result.put("pendingRecheckAnswerCount", pendingCount);
        result.put("reviewedRecheckAnswerCount", reviewedCount);
        result.put("reviewScoreLogCount", reviewLogCount);
        result.put("recheckAttemptFinalized", finalized ? 1 : 0);
        result.put("closeAllowed", closeBlockers.isEmpty() ? 1 : 0);
        result.put("closeBlockers", closeBlockers);
        result.put("recheckOpenedAt", recheckOpenedAt(jt, id));
        result.put("answers", answers);
        return result;
    }

    private ExportFile buildAppealLogsExport(Long id, Map<String, Object> appeal, List<Map<String, Object>> logs) {
        List<String> headers = List.of(
                "Log ID", "Time", "Appeal ID", "Exam", "Student", "Question ID", "Action",
                "Status From", "Status To", "Handling Result", "Actor", "Note"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    nullable(log.get("id")),
                    nullable(log.get("createdAt")),
                    nullable(log.get("appealId")),
                    nullable(appeal.get("examName")),
                    nullable(appeal.get("studentName")),
                    nullable(log.get("questionId")),
                    nullable(log.get("action")),
                    nullable(log.get("statusFrom")),
                    nullable(log.get("statusTo")),
                    nullable(log.get("handlingResult")),
                    nullable(log.get("actorName")),
                    nullable(log.get("note"))
            ));
        }
        String filename = safeExportName(String.valueOf(appeal.get("examName")))
                + "-appeal-" + id + "-log-" + LocalDate.now() + ".csv";
        return new ExportFile(filename, CsvExport.build(headers, rows));
    }

    @Transactional
    public Map<String, Object> replyAppeal(Long id, ScoreAppealReplyRequest request, AuthUser handler) {
        if (request == null) {
            throw new IllegalArgumentException("Score appeal reply request is required");
        }
        String reply = normalizeRequiredAppealText(request.getReply(), "appeal reply");
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireAppealAccessForUpdate(jt, id, handler);
        if (((Number) appeal.get("status")).intValue() != 0) {
            throw new IllegalStateException("Appeal has already been handled");
        }
        String handlingResult = normalizeHandlingResult(request.getHandlingResult());
        jt.update("""
                UPDATE score_appeal
                SET status = 1, teacher_reply = ?, handling_result = ?, handled_by = ?, handled_at = NOW()
                WHERE id = ?
                """, reply, handlingResult, handler.getId(), id);
        Long replyLogId = recordAppealLog(jt, id, numberValue(appeal.get("attemptId")), numberValue(appeal.get("examId")),
                numberValue(appeal.get("questionId")), numberValue(appeal.get("userId")),
                "REPLY", 0, 1, handlingResult, reply, handler.getId());
        List<Long> scoreAppealLogIds = logIds(replyLogId);
        int reopenedCount = 0;
        if ("RECHECK_REQUIRED".equals(handlingResult)) {
            reopenedCount = reopenAppealReviewTasks(jt, appeal);
            Long recheckOpenLogId = recordAppealLog(jt, id, numberValue(appeal.get("attemptId")), numberValue(appeal.get("examId")),
                    numberValue(appeal.get("questionId")), numberValue(appeal.get("userId")),
                    "RECHECK_OPEN", 1, 1, handlingResult,
                    "Reopened " + reopenedCount + " answer(s) for score recheck", handler.getId());
            if (recheckOpenLogId != null) {
                scoreAppealLogIds.add(recheckOpenLogId);
            }
        }
        Long studentId = numberValue(appeal.get("userId"));
        notificationService.send(studentId, "Score appeal handled",
                "Your score appeal has been replied. Please check the results page.", "SCORE_APPEAL",
                studentAppealLink(id), "SCORE_APPEAL", id);
        Map<String, Object> result = getAppealById(jt, id);
        result.put("reopenedReviewCount", reopenedCount);
        result.put("scoreAppealLogId", replyLogId);
        result.put("scoreAppealLogIds", scoreAppealLogIds);
        return result;
    }

    @Transactional
    public Map<String, Object> closeRecheck(Long id, ScoreAppealRecheckCloseRequest request, AuthUser handler) {
        if (request == null) {
            throw new IllegalArgumentException("Score appeal recheck close request is required");
        }
        String recheckNote = normalizeRequiredAppealText(request.getRecheckNote(), "recheck note");
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> appeal = requireAppealAccessForUpdate(jt, id, handler);
        if (((Number) appeal.get("status")).intValue() != 1
                || !"RECHECK_REQUIRED".equals(String.valueOf(appeal.get("handlingResult")))) {
            throw new IllegalStateException("Only recheck-required appeals can be closed");
        }
        int pendingRecheckCount = countPendingRecheckAnswers(jt, appeal);
        if (pendingRecheckCount > 0) {
            throw new IllegalStateException("Recheck review tasks must be completed before closing appeal");
        }
        List<Map<String, Object>> recheckEvidence = recheckAnswerEvidence(jt, appeal);
        if (recheckEvidence.isEmpty()) {
            throw new IllegalStateException("No answers are available for score recheck");
        }
        boolean hasReviewScoreEvidence = recheckEvidence.stream().anyMatch(answer -> answer.get("reviewScoreLogId") != null);
        if (!hasReviewScoreEvidence) {
            throw new IllegalStateException("Recheck review score evidence is required before closing appeal");
        }
        requireRecheckAttemptFinalized(jt, appeal);
        jt.update("""
                UPDATE score_appeal
                SET status = 2, recheck_note = ?, rechecked_by = ?, rechecked_at = NOW()
                WHERE id = ?
                """, recheckNote, handler.getId(), id);
        Long closeLogId = recordAppealLog(jt, id, numberValue(appeal.get("attemptId")), numberValue(appeal.get("examId")),
                numberValue(appeal.get("questionId")), numberValue(appeal.get("userId")),
                "CLOSE_RECHECK", 1, 2, "RECHECK_REQUIRED", recheckNote, handler.getId());
        Long studentId = numberValue(appeal.get("userId"));
        notificationService.send(studentId, "Score appeal recheck completed",
                "Your score appeal recheck has been completed. Please check the results page.", "SCORE_APPEAL",
                studentAppealLink(id), "SCORE_APPEAL", id);
        Map<String, Object> result = getAppealById(jt, id);
        result.put("scoreAppealLogId", closeLogId);
        result.put("scoreAppealLogIds", logIds(closeLogId));
        return result;
    }

    private List<Map<String, Object>> recheckAnswerEvidence(JdbcTemplate jt, Map<String, Object> appeal) {
        Long appealId = numberValue(appeal.get("id"));
        Long attemptId = numberValue(appeal.get("attemptId"));
        Long questionId = numberValue(appeal.get("questionId"));
        List<Object> params = new ArrayList<>();
        params.add(appealId);
        params.add(attemptId);
        StringBuilder sql = new StringBuilder("""
                SELECT ar.id AS answerRecordId, ar.question_id AS questionId,
                       COALESCE(eqs.question_type, q.question_type) AS questionType,
                       COALESCE(eqs.stem, q.stem) AS stem,
                       COALESCE(eqs.score, pq.score, 0) AS maxScore,
                       ar.review_status AS reviewStatus,
                       ar.score AS currentScore,
                       ar.is_correct AS isCorrect,
                       rsl.id AS reviewScoreLogId,
                       rsl.old_score AS oldScore,
                       rsl.new_score AS newScore,
                       rsl.comment AS reviewComment,
                       rsl.reviewer_id AS reviewerId,
                       reviewer.real_name AS reviewerName,
                       rsl.created_at AS reviewedAt
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                JOIN exam e ON e.id = a.exam_id
                LEFT JOIN paper_question pq ON pq.paper_id = e.paper_id AND pq.question_id = ar.question_id
                LEFT JOIN question q ON q.id = ar.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                LEFT JOIN review_score_log rsl ON rsl.id = (
                    SELECT MAX(rsl_latest.id)
                    FROM review_score_log rsl_latest
                    WHERE rsl_latest.answer_record_id = ar.id
                      AND rsl_latest.attempt_id = ar.attempt_id
                      AND rsl_latest.created_at >= COALESCE((
                          SELECT MAX(opened.created_at)
                          FROM score_appeal_log opened
                          WHERE opened.appeal_id = ?
                            AND opened.action = 'RECHECK_OPEN'
                      ), '1970-01-01 00:00:00')
                )
                LEFT JOIN sys_user reviewer ON reviewer.id = rsl.reviewer_id
                WHERE ar.attempt_id = ?
                """);
        sql.append(EXPECTED_ANSWER_SCOPE_CONDITION);
        if (questionId != null) {
            sql.append(" AND ar.question_id = ?");
            params.add(questionId);
        } else {
            sql.append(" AND COALESCE(eqs.question_type, q.question_type, '') IN ('FILL_BLANK', 'SUBJECTIVE')");
        }
        sql.append(" ORDER BY ar.question_id ASC, ar.id ASC");
        return jt.queryForList(sql.toString(), params.toArray());
    }

    private Object recheckOpenedAt(JdbcTemplate jt, Long appealId) {
        return jt.queryForObject("""
                SELECT MAX(created_at)
                FROM score_appeal_log
                WHERE appeal_id = ? AND action = 'RECHECK_OPEN'
                """, Object.class, appealId);
    }

    private int reopenAppealReviewTasks(JdbcTemplate jt, Map<String, Object> appeal) {
        Long attemptId = numberValue(appeal.get("attemptId"));
        Long questionId = numberValue(appeal.get("questionId"));
        int reopenedCount;
        if (questionId != null) {
            reopenedCount = jt.update("""
                    UPDATE answer_record ar
                    JOIN exam_attempt a ON a.id = ar.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    SET ar.review_status = 0
                    WHERE ar.attempt_id = ? AND ar.question_id = ?
                    """ + EXPECTED_ANSWER_SCOPE_CONDITION, attemptId, questionId);
        } else {
            reopenedCount = jt.update("""
                    UPDATE answer_record ar
                    JOIN exam_attempt a ON a.id = ar.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    LEFT JOIN exam_question_snapshot eqs
                      ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                    LEFT JOIN question q ON q.id = ar.question_id
                    SET ar.review_status = 0
                    WHERE ar.attempt_id = ?
                """
                + EXPECTED_ANSWER_SCOPE_CONDITION +
                """
                      AND COALESCE(eqs.question_type, q.question_type, '') IN ('FILL_BLANK', 'SUBJECTIVE')
                    """, attemptId);
        }
        if (reopenedCount <= 0) {
            throw new IllegalStateException("No answers are available for score recheck");
        }
        jt.update("UPDATE exam_attempt SET status = 4 WHERE id = ? AND status = 5", attemptId);
        return reopenedCount;
    }

    private int countPendingRecheckAnswers(JdbcTemplate jt, Map<String, Object> appeal) {
        Long attemptId = numberValue(appeal.get("attemptId"));
        Long questionId = numberValue(appeal.get("questionId"));
        Integer count;
        if (questionId != null) {
            count = jt.queryForObject("""
                    SELECT COUNT(*)
                    FROM answer_record ar
                    JOIN exam_attempt a ON a.id = ar.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    WHERE ar.attempt_id = ? AND ar.question_id = ? AND ar.review_status = 0
                    """ + EXPECTED_ANSWER_SCOPE_CONDITION, Integer.class, attemptId, questionId);
        } else {
            count = jt.queryForObject("""
                    SELECT COUNT(*)
                    FROM answer_record ar
                    JOIN exam_attempt a ON a.id = ar.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    WHERE ar.attempt_id = ? AND ar.review_status = 0
                    """ + EXPECTED_ANSWER_SCOPE_CONDITION, Integer.class, attemptId);
        }
        return count == null ? 0 : count;
    }

    private void requireRecheckAttemptFinalized(JdbcTemplate jt, Map<String, Object> appeal) {
        if (!isRecheckAttemptFinalized(jt, appeal)) {
            throw new IllegalStateException("Recheck attempt must be finalized with a score before closing appeal");
        }
    }

    private boolean isRecheckAttemptFinalized(JdbcTemplate jt, Map<String, Object> appeal) {
        Integer finalized = jt.queryForObject("""
                SELECT CASE WHEN status = 5 AND score IS NOT NULL THEN 1 ELSE 0 END
                FROM exam_attempt
                WHERE id = ?
                """, Integer.class, numberValue(appeal.get("attemptId")));
        return finalized != null && finalized > 0;
    }

    private Map<String, Object> requireAppealAccess(JdbcTemplate jt, Long appealId, AuthUser user) {
        requirePositiveAppealId(appealId);
        List<Map<String, Object>> rows = jt.queryForList(baseAppealSelect() + " WHERE sa.id = ?", appealId);
        return requireAppealAccessFromRows(rows, user);
    }

    private Map<String, Object> requireAppealAccessForUpdate(JdbcTemplate jt, Long appealId, AuthUser user) {
        requirePositiveAppealId(appealId);
        List<Map<String, Object>> rows = jt.queryForList(baseAppealSelect() + " WHERE sa.id = ? FOR UPDATE", appealId);
        return requireAppealAccessFromRows(rows, user);
    }

    private Map<String, Object> requireStudentAppealAccess(JdbcTemplate jt, Long appealId, AuthUser student) {
        requirePositiveAppealId(appealId);
        List<Map<String, Object>> rows = jt.queryForList(baseAppealSelect() + """
                WHERE sa.id = ? AND sa.user_id = ?
                """, appealId, student.getId());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Appeal not found");
        }
        return rows.get(0);
    }

    private void requireStudentAppealEvidenceVisible(JdbcTemplate jt, Map<String, Object> appeal, AuthUser student) {
        Long attemptId = numberValue(appeal.get("attemptId"));
        Integer visible = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt a
                JOIN score_release sr ON sr.exam_id = a.exam_id AND sr.status = 1
                WHERE a.id = ? AND a.user_id = ? AND a.status = 5 AND a.score IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM score_appeal sa
                      WHERE sa.attempt_id = a.id
                        AND sa.status = 1
                        AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """, Integer.class, attemptId, student.getId());
        if (visible == null || visible == 0) {
            throw new IllegalStateException("Recheck evidence is only available after scores are released and all rechecks are closed");
        }
    }

    private Map<String, Object> requireAppealAccessFromRows(List<Map<String, Object>> rows, AuthUser user) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Appeal not found");
        }
        Map<String, Object> row = rows.get(0);
        if (teachingScopeService.hasGlobalScope(user)) {
            return row;
        }
        Long createdBy = numberValue(row.get("examCreatedBy"));
        Long studentUserId = numberValue(row.get("userId"));
        if ((createdBy != null && createdBy.equals(user.getId()))
                || teachingScopeService.canAccessStudent(user, studentUserId)) {
            return row;
        }
        throw new IllegalArgumentException("Appeal is outside current teaching scope");
    }

    private void appendTeachingScope(StringBuilder sql, List<Object> params, AuthUser user) {
        if (teachingScopeService.hasGlobalScope(user)) {
            return;
        }
        sql.append(" AND (e.created_by = ?");
        params.add(user.getId());
        List<Long> studentIds = teachingScopeService.visibleStudentUserIds(user);
        if (!studentIds.isEmpty()) {
            sql.append(" OR sa.user_id IN (");
            appendPlaceholders(sql, params, studentIds);
            sql.append(")");
        }
        sql.append(")");
    }

    private String baseAppealSelect() {
        return """
                SELECT sa.id, sa.attempt_id AS attemptId, sa.question_id AS questionId,
                       sa.user_id AS userId, u.real_name AS studentName, sp.student_no AS studentNo,
                       e.id AS examId, e.exam_name AS examName, e.created_by AS examCreatedBy,
                       COALESCE(eqs.stem, q.stem) AS questionStem,
                       COALESCE(eqs.question_type, q.question_type) AS questionType,
                       sa.reason, sa.status, sa.teacher_reply AS teacherReply,
                       sa.handling_result AS handlingResult,
                       sa.handled_by AS handledBy, handler.real_name AS handlerName,
                       sa.handled_at AS handledAt,
                       sa.recheck_note AS recheckNote,
                       sa.rechecked_by AS recheckedBy, rechecker.real_name AS recheckerName,
                       sa.rechecked_at AS recheckedAt,
                       (SELECT MIN(l.id)
                        FROM score_appeal_log l
                        WHERE l.appeal_id = sa.id AND l.action = 'SUBMIT') AS scoreAppealLogId,
                       sa.created_at AS createdAt
                FROM score_appeal sa
                JOIN exam_attempt a ON a.id = sa.attempt_id
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = sa.user_id
                LEFT JOIN student_profile sp ON sp.user_id = sa.user_id AND sp.deleted = 0
                LEFT JOIN question q ON q.id = sa.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = sa.question_id
                LEFT JOIN sys_user handler ON handler.id = sa.handled_by
                LEFT JOIN sys_user rechecker ON rechecker.id = sa.rechecked_by
                """;
    }

    private Map<String, Object> getAppealById(JdbcTemplate jt, Long id) {
        return jt.queryForMap(baseAppealSelect() + " WHERE sa.id = ?", id);
    }

    private Long recordAppealLog(JdbcTemplate jt, Long appealId, Long attemptId, Long examId, Long questionId,
                                 Long userId, String action, Integer statusFrom, Integer statusTo,
                                 String handlingResult, String note, Long actorId) {
        Long resolvedExamId = resolveAppealLogExamId(jt, attemptId, examId);
        jt.update("""
                INSERT INTO score_appeal_log (appeal_id, attempt_id, exam_id, question_id, user_id,
                                              action, status_from, status_to, handling_result, note, actor_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, appealId, attemptId, resolvedExamId, questionId, userId, action, statusFrom, statusTo,
                handlingResult, trim(note), actorId);
        return jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void validateAppealRequestIds(ScoreAppealRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Score appeal request is required");
        }
        if (request.getAttemptId() == null || request.getAttemptId() <= 0) {
            throw new IllegalArgumentException("attemptId must be positive");
        }
        if (request.getQuestionId() != null && request.getQuestionId() <= 0) {
            throw new IllegalArgumentException("questionId must be positive");
        }
    }

    private String normalizeRequiredAppealText(String value, String fieldName) {
        String normalized = trim(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() > MAX_APPEAL_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be at most 1000 characters");
        }
        return normalized;
    }

    private void requirePositiveAppealId(Long appealId) {
        if (appealId == null || appealId <= 0) {
            throw new IllegalArgumentException("appealId must be positive");
        }
    }

    private void requirePositiveExamId(Long examId) {
        if (examId == null || examId <= 0) {
            throw new IllegalArgumentException("examId must be positive");
        }
    }

    private List<Long> logIds(Long firstLogId) {
        List<Long> ids = new ArrayList<>();
        if (firstLogId != null) {
            ids.add(firstLogId);
        }
        return ids;
    }

    private void notifyTeachersForNewAppeal(JdbcTemplate jt, Map<String, Object> attempt, Long appealId,
                                            Long questionId, AuthUser student) {
        Set<Long> recipientIds = scoreAppealTeacherRecipientIds(jt, attempt, student);
        if (recipientIds.isEmpty()) {
            return;
        }
        String examName = firstNonBlank(String.valueOf(attempt.get("examName")), "exam");
        String target = questionId == null ? "the whole paper" : "question " + questionId;
        String studentName = firstNonBlank(student.getRealName(), student.getUsername(), "student");
        for (Long recipientId : recipientIds) {
            notificationService.sendOnceAndReturnId(recipientId,
                    "New score appeal: " + examName,
                    studentName + " submitted a score appeal for " + target + ". Please handle it in the review workbench.",
                    "SCORE_APPEAL",
                    teacherAppealLink(appealId),
                    "SCORE_APPEAL",
                    appealId);
        }
    }

    private Set<Long> scoreAppealTeacherRecipientIds(JdbcTemplate jt, Map<String, Object> attempt, AuthUser student) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        Long ownerId = numberValue(attempt.get("examOwnerId"));
        if (ownerId != null) {
            recipientIds.add(ownerId);
        }
        Long examId = numberValue(attempt.get("examId"));
        if (examId != null) {
            recipientIds.addAll(jt.queryForList("""
                    SELECT DISTINCT tcc.teacher_user_id
                    FROM exam e
                    JOIN paper p ON p.id = e.paper_id AND p.deleted = 0
                    JOIN edu_course co ON co.subject_id = p.subject_id AND co.deleted = 0 AND co.status = 1
                    JOIN class_course cc ON cc.course_id = co.id AND cc.deleted = 0 AND cc.status = 1
                    JOIN student_course_enrollment sce
                      ON sce.class_course_id = cc.id
                     AND sce.student_user_id = ?
                     AND sce.deleted = 0
                     AND sce.status = 1
                    JOIN teacher_class_course tcc
                      ON tcc.class_course_id = cc.id
                     AND tcc.deleted = 0
                     AND tcc.status = 1
                    JOIN sys_user u ON u.id = tcc.teacher_user_id AND u.deleted = 0 AND u.status = 1
                    WHERE e.id = ? AND e.deleted = 0
                    ORDER BY tcc.teacher_user_id
                    """, Long.class, student.getId(), examId));
        }
        recipientIds.remove(student.getId());
        return recipientIds;
    }

    private String studentAppealLink(Long appealId) {
        return "/student/results?appealId=" + appealId;
    }

    private String teacherAppealLink(Long appealId) {
        return "/reviews?appealId=" + appealId;
    }

    private Long resolveAppealLogExamId(JdbcTemplate jt, Long attemptId, Long fallbackExamId) {
        if (attemptId == null) {
            return fallbackExamId;
        }
        List<Long> examIds = jt.queryForList("""
                SELECT exam_id
                FROM exam_attempt
                WHERE id = ?
                """, Long.class, attemptId);
        return examIds.isEmpty() ? fallbackExamId : examIds.get(0);
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

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean configBoolean(JdbcTemplate jt, String key, boolean fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        return "true".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    private int configNumber(JdbcTemplate jt, String key, int fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String configValue(JdbcTemplate jt, String key) {
        List<String> rows = jt.queryForList("""
                SELECT config_value
                FROM system_config
                WHERE config_key = ?
                """, String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = trim(value);
            if (trimmed != null && !trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return "";
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String safeExportName(String value) {
        String normalized = value == null ? "score-appeal-log" : value.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            normalized = "score-appeal-log";
        }
        return normalized.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private String normalizeHandlingResult(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("MAINTAINED".equals(result)
                || "RECHECK_REQUIRED".equals(result)
                || "ADJUSTED_OFFLINE".equals(result)) {
            return result;
        }
        throw new IllegalArgumentException("Invalid score appeal handling result");
    }

    private String normalizeOptionalHandlingResult(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return normalizeHandlingResult(value);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }
}
