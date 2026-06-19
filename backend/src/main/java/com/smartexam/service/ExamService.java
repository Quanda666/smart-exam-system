package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.ExamApprovalDecisionRequest;
import com.smartexam.dto.exam.ExamRequest;
import com.smartexam.dto.exam.ExamUpdateRequest;
import com.smartexam.dto.exam.ScoreRevokeRequest;
import com.smartexam.dto.exam.StartExamRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import com.smartexam.util.PasswordHashUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HexFormat;

@Service
public class ExamService {

    private static final int SUBMIT_GRACE_SECONDS = 30;
    private static final int MAX_SUBMITTED_ANSWER_COUNT = 1000;
    private static final int MAX_SUBMITTED_ANSWER_LENGTH = 20000;
    private static final int MAX_SUBMIT_TOKEN_LENGTH = 80;
    private static final int MAX_EXAM_LIFECYCLE_EVENTS = 500;
    private static final int MAX_SCORE_REVOKE_REASON_LENGTH = 500;
    private static final int MAX_APPROVAL_NOTE_LENGTH = 1000;
    private static final int EXAM_STATUS_PENDING_APPROVAL = 0;
    private static final int EXAM_STATUS_PUBLISHED = 1;
    private static final int EXAM_STATUS_CLOSED = 2;
    private static final int EXAM_STATUS_REJECTED = 3;
    private static final String APPROVAL_ACTION_SUBMIT = "SUBMIT";
    private static final String APPROVAL_ACTION_RESUBMIT = "RESUBMIT";
    private static final String APPROVAL_ACTION_APPROVE = "APPROVE";
    private static final String APPROVAL_ACTION_REJECT = "REJECT";
    private static final String APPROVAL_ACTION_DIRECT_PUBLISH = "DIRECT_PUBLISH";
    private static final String SCORE_RELEASE_ACTION_PUBLISH = "PUBLISH";
    private static final String SCORE_RELEASE_ACTION_REVOKE = "REVOKE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> STUDENT_TAKING_SENSITIVE_KEYS = Set.of(
            "correct",
            "iscorrect",
            "correctanswer",
            "correctoption",
            "correctflag",
            "rightanswer",
            "standardanswer",
            "referenceanswer",
            "answer",
            "answercontent",
            "analysis",
            "explanation",
            "solution"
    );

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final NotificationService notificationService;
    private final TeachingScopeService teachingScopeService;
    private final ExamDraftCacheService examDraftCacheService;

    public ExamService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                       NotificationService notificationService,
                       TeachingScopeService teachingScopeService,
                       ExamDraftCacheService examDraftCacheService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.notificationService = notificationService;
        this.teachingScopeService = teachingScopeService;
        this.examDraftCacheService = examDraftCacheService;
    }

    public List<Map<String, Object>> listTeacherExams(String keyword, Integer status, AuthUser user) {
        return listTeacherExams(keyword, status, null, user, 1, 200).getList();
    }

    public PageResult<Map<String, Object>> listTeacherExams(String keyword, Integer status, Long examId, AuthUser user,
                                                            int page, int size) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        String kw = blankToNull(keyword);

        List<Object> countParams = new ArrayList<>();
        countParams.add(status);
        countParams.add(status);
        countParams.add(examId);
        countParams.add(examId);
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
                  AND (? IS NULL OR e.id = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%'))
                """ + scopeSql, Long.class, countParams.toArray());

        List<Object> listParams = new ArrayList<>();
        listParams.add(status);
        listParams.add(status);
        listParams.add(examId);
        listParams.add(examId);
        listParams.add(kw);
        listParams.add(kw);
        listParams.add(kw);
        String listScopeSql = appendExamScope(user, listParams);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT e.id, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore,
                       e.status, e.created_by AS createdBy, p.paper_name AS paperName, s.subject_name AS subjectName,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       sr.published_by AS scorePublishedBy,
                       COALESCE(pub.real_name, pub.username, CAST(sr.published_by AS CHAR)) AS scorePublishedByName,
                       sr.published_at AS scorePublishedAt,
                       sr.revoked_by AS scoreRevokedBy,
                       COALESCE(rev.real_name, rev.username, CAST(sr.revoked_by AS CHAR)) AS scoreRevokedByName,
                       sr.revoked_at AS scoreRevokedAt,
                       COALESCE(sr.publish_note, sr.note) AS scorePublishNote,
                       COALESCE(sr.revoke_reason, sr.note) AS scoreRevokeReason,
                       sr.note AS scoreReleaseNote,
                       (SELECT COUNT(*) FROM exam_target et WHERE et.exam_id = e.id) AS targetCount,
                       (SELECT GROUP_CONCAT(CONCAT(et.target_type, ':', et.target_id, ':', et.target_code) ORDER BY et.id SEPARATOR ',')
                        FROM exam_target et WHERE et.exam_id = e.id) AS targetSummary,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                        (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount,
                        (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status = 5) AS completedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status = 5 AND ea.score IS NULL) AS unscoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status = 4) AS pendingReviewAttemptCount,
                         (SELECT COUNT(*)
                          FROM answer_record ar
                          JOIN exam_attempt ea ON ea.id = ar.attempt_id
                          WHERE ea.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                         (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status <> 0) AS startedAttemptCount,
                        (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status = 1) AS activeAttemptCount,
                        (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status <> 0 AND ea.status <> 5) AS nonFinalStartedAttemptCount,
                        (SELECT COUNT(*) FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id) AS candidateSnapshotCount,
                        (SELECT COUNT(*) FROM exam_question_snapshot eqs WHERE eqs.exam_id = e.id) AS questionSnapshotCount,
                       (SELECT COUNT(*) FROM score_appeal sa
                        LEFT JOIN exam_attempt aa ON aa.id = sa.attempt_id
                        WHERE ((aa.id IS NOT NULL AND aa.exam_id = e.id)
                            OR (aa.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 0) AS pendingScoreAppealCount,
                        (SELECT COUNT(*) FROM score_appeal sa
                         LEFT JOIN exam_attempt aa ON aa.id = sa.attempt_id
                         WHERE ((aa.id IS NOT NULL AND aa.exam_id = e.id)
                             OR (aa.id IS NULL AND sa.exam_id = e.id))
                           AND sa.status = 1
                          AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                LEFT JOIN sys_user pub ON pub.id = sr.published_by
                LEFT JOIN sys_user rev ON rev.id = sr.revoked_by
                WHERE e.deleted = 0
                  AND (? IS NULL OR e.status = ?)
                  AND (? IS NULL OR e.id = ?)
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%')) 
                """
                + listScopeSql +
                """
                ORDER BY e.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        appendScoreReleaseReadiness(list);
        enrichExamLifecycleHealth(list);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> listApprovalQueue(String keyword, String creatorKeyword, Integer status,
                                                             String startFrom, String startTo, String risk,
                                                             Long examId, int page, int size, AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can view exam approval queue");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        String whereSql = approvalQueueWhere(keyword, creatorKeyword, status, startFrom, startTo, risk, examId, params);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = e.created_by
                """ + whereSql, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT e.id, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore, e.status,
                       e.created_by AS createdBy, e.created_at AS createdAt,
                       COALESCE(u.real_name, u.username, CAST(e.created_by AS CHAR)) AS creatorName,
                       p.paper_name AS paperName, s.subject_name AS subjectName,
                       (SELECT COUNT(*) FROM exam_target et WHERE et.exam_id = e.id) AS targetCount,
                       (SELECT GROUP_CONCAT(CONCAT(et.target_type, ':', et.target_id, ':', et.target_code) ORDER BY et.id SEPARATOR ',')
                        FROM exam_target et WHERE et.exam_id = e.id) AS targetSummary,
                       COALESCE(NULLIF((SELECT COUNT(*)
                                        FROM exam_question_snapshot eqs_count
                                        WHERE eqs_count.exam_id = e.id), 0),
                                (SELECT COUNT(*)
                                 FROM paper_question pq_count
                                 WHERE pq_count.paper_id = e.paper_id)) AS questionCount,
                       COALESCE((SELECT SUM(eqs_score.score)
                                 FROM exam_question_snapshot eqs_score
                                 WHERE eqs_score.exam_id = e.id),
                                (SELECT COALESCE(SUM(pq_score.score), 0)
                                 FROM paper_question pq_score
                                 WHERE pq_score.paper_id = e.paper_id)) AS totalScore,
                       (SELECT COUNT(*) FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id) AS candidateSnapshotCount,
                       TIMESTAMPDIFF(HOUR, e.created_at, NOW()) AS pendingHours,
                       (SELECT l.action FROM exam_approval_log l WHERE l.exam_id = e.id ORDER BY l.created_at DESC, l.id DESC LIMIT 1) AS latestApprovalAction,
                       (SELECT l.note FROM exam_approval_log l WHERE l.exam_id = e.id ORDER BY l.created_at DESC, l.id DESC LIMIT 1) AS latestApprovalNote,
                       (SELECT l.created_at FROM exam_approval_log l WHERE l.exam_id = e.id ORDER BY l.created_at DESC, l.id DESC LIMIT 1) AS latestApprovalAt,
                       CONCAT_WS(',',
                         CASE WHEN e.status = 0 AND e.start_time <= NOW() THEN 'PAST_START' END,
                         CASE WHEN NOT EXISTS (SELECT 1 FROM exam_target et WHERE et.exam_id = e.id) THEN 'NO_TARGET' END,
                         CASE WHEN COALESCE(NULLIF((SELECT COUNT(*)
                                                   FROM exam_question_snapshot eqs_count
                                                   WHERE eqs_count.exam_id = e.id), 0),
                                           (SELECT COUNT(*)
                                            FROM paper_question pq_count
                                            WHERE pq_count.paper_id = e.paper_id)) = 0 THEN 'NO_QUESTIONS' END,
                         CASE WHEN e.pass_score IS NOT NULL
                                AND e.pass_score > COALESCE((SELECT SUM(eqs_score.score)
                                                             FROM exam_question_snapshot eqs_score
                                                             WHERE eqs_score.exam_id = e.id),
                                                            (SELECT COALESCE(SUM(pq_score.score), 0)
                                                             FROM paper_question pq_score
                                                             WHERE pq_score.paper_id = e.paper_id))
                              THEN 'PASS_SCORE_OVER_TOTAL' END
                       ) AS riskFlags
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user u ON u.id = e.created_by 
                """
                + whereSql +
                """
                ORDER BY
                  CASE WHEN e.status = 0 THEN 0 ELSE 1 END,
                  e.start_time ASC,
                  e.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> listApprovalReminderLogs(int page, int size, Long logId, AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can view approval reminder logs");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        String whereSql = "";
        if (logId != null && logId > 0) {
            whereSql = " WHERE l.id = ?";
            params.add(logId);
        }
        Long total = jt.queryForObject("SELECT COUNT(*) FROM exam_approval_reminder_log l " + whereSql,
                Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT l.id, l.triggered_by AS triggeredBy,
                       CASE WHEN l.triggered_by = 0 THEN 'System scheduler'
                            ELSE COALESCE(u.real_name, u.username, CAST(l.triggered_by AS CHAR)) END AS triggeredByName,
                       l.overdue_hours AS overdueHours, l.cooldown_hours AS cooldownHours,
                       l.overdue_exam_count AS overdueExamCount, l.recipient_count AS recipientCount,
                       l.status, l.trigger_source AS triggerSource, l.node_id AS nodeId,
                       l.duration_ms AS durationMs, l.message, l.created_at AS createdAt
                FROM exam_approval_reminder_log l
                LEFT JOIN sys_user u ON u.id = l.triggered_by 
                """
                + whereSql +
                """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportApprovalReminderLogs(AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can export approval reminder logs");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        List<Map<String, Object>> logs = jt.queryForList("""
                SELECT l.id, l.triggered_by AS triggeredBy,
                       CASE WHEN l.triggered_by = 0 THEN 'System scheduler'
                            ELSE COALESCE(u.real_name, u.username, CAST(l.triggered_by AS CHAR)) END AS triggeredByName,
                       l.overdue_hours AS overdueHours, l.cooldown_hours AS cooldownHours,
                       l.overdue_exam_count AS overdueExamCount, l.recipient_count AS recipientCount,
                       l.status, l.trigger_source AS triggerSource, l.node_id AS nodeId,
                       l.duration_ms AS durationMs, l.message, l.created_at AS createdAt
                FROM exam_approval_reminder_log l
                LEFT JOIN sys_user u ON u.id = l.triggered_by
                ORDER BY l.created_at DESC, l.id DESC
                """);
        List<String> headers = List.of(
                "Reminder Log ID", "Time", "Status", "Trigger Source", "Triggered By",
                "Overdue Hours", "Cooldown Hours", "Overdue Exam Count", "Recipient Count",
                "Node ID", "Duration Ms", "Message"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    nullable(log.get("id")),
                    nullable(log.get("createdAt")),
                    nullable(log.get("status")),
                    nullable(log.get("triggerSource")),
                    nullable(log.get("triggeredByName")),
                    nullable(log.get("overdueHours")),
                    nullable(log.get("cooldownHours")),
                    nullable(log.get("overdueExamCount")),
                    nullable(log.get("recipientCount")),
                    nullable(log.get("nodeId")),
                    nullable(log.get("durationMs")),
                    nullable(log.get("message"))
            ));
        }
        return new ExportFile(safeExportName("approval-reminder") + "-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    @Transactional
    public Map<String, Object> sendApprovalOverdueReminders(AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can send approval reminders");
        }
        return processApprovalOverdueReminders(user.getId(), "MANUAL", false, null);
    }

    @Transactional
    public Map<String, Object> sendScheduledApprovalOverdueReminders() {
        return sendScheduledApprovalOverdueReminders(null);
    }

    @Transactional
    public Map<String, Object> sendScheduledApprovalOverdueReminders(String nodeId) {
        return processApprovalOverdueReminders(0L, "SCHEDULE", true, nodeId);
    }

    private Map<String, Object> processApprovalOverdueReminders(Long triggeredBy, String triggerSource,
                                                                boolean scheduled, String nodeId) {
        long startedAt = System.currentTimeMillis();
        JdbcTemplate jt = requireJdbcTemplate();
        int overdueHours = configNumber(jt, "approval.slaOverdueHours", 24);
        boolean enabled = configBoolean(jt, "approval.reminderEnabled", true);
        int cooldownHours = configNumber(jt, "approval.reminderCooldownHours", 6);
        boolean scheduleEnabled = configBoolean(jt, "approval.reminderScheduleEnabled", true);
        int scheduleIntervalMinutes = configNumber(jt, "approval.reminderScheduleIntervalMinutes", 60);
        if (scheduled && !scheduleEnabled) {
            return approvalReminderResult(false, enabled, overdueHours, cooldownHours, false,
                    0, 0, "SKIPPED_SCHEDULE_DISABLED",
                    "Approval reminder scheduler is disabled", null, triggerSource,
                    scheduleIntervalMinutes, nodeId, elapsedMillis(startedAt), null);
        }
        if (scheduled && isApprovalReminderScheduleIntervalActive(jt, scheduleIntervalMinutes)) {
            return approvalReminderResult(false, enabled, overdueHours, cooldownHours, false,
                    0, 0, "SKIPPED_SCHEDULE_INTERVAL",
                    "Approval reminder scheduler is waiting for the next interval", null,
                    triggerSource, scheduleIntervalMinutes, nodeId, elapsedMillis(startedAt), null);
        }
        Integer overdueCount = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND status = ?
                  AND TIMESTAMPDIFF(HOUR, created_at, NOW()) >= ?
                """, Integer.class, EXAM_STATUS_PENDING_APPROVAL, overdueHours);
        List<Long> adminIds = jt.queryForList("""
                SELECT DISTINCT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.deleted = 0 AND u.status = 1 AND r.role_code = 'ADMIN'
                """, Long.class);
        Map<String, Object> lastReminder = latestSentApprovalReminder(jt);
        boolean cooldownActive = enabled
                && overdueCount != null && overdueCount > 0
                && !adminIds.isEmpty()
                && isApprovalReminderInCooldown(jt, cooldownHours);
        boolean sent = enabled && overdueCount != null && overdueCount > 0 && !adminIds.isEmpty() && !cooldownActive;
        String status;
        String message;
        if (sent) {
            status = "SENT";
            message = "Approval overdue reminder sent";
        } else if (!enabled) {
            status = "SKIPPED_DISABLED";
            message = "Approval overdue reminders are disabled";
        } else if (overdueCount == null || overdueCount == 0) {
            status = "SKIPPED_EMPTY";
            message = "No overdue approval requests";
        } else if (adminIds.isEmpty()) {
            status = "SKIPPED_NO_RECIPIENT";
            message = "No active administrators found";
        } else {
            status = "SKIPPED_COOLDOWN";
            message = "Approval reminder is still in cooldown";
        }
        long durationMs = elapsedMillis(startedAt);
        Long reminderLogId = recordApprovalReminderLog(jt, triggeredBy, overdueHours, cooldownHours,
                overdueCount == null ? 0 : overdueCount, adminIds.size(), status, triggerSource,
                nodeId, durationMs, message);
        if (sent) {
            notificationService.sendBatch(adminIds,
                    "Exam approvals overdue",
                    "There are " + overdueCount + " exam approval requests waiting for more than "
                            + overdueHours + " hours. Please review them in the approval queue.",
                    "EXAM_APPROVAL", approvalReminderLink(reminderLogId), "APPROVAL_REMINDER", reminderLogId);
        }
        return approvalReminderResult(sent, enabled, overdueHours, cooldownHours, cooldownActive,
                overdueCount == null ? 0 : overdueCount, adminIds.size(), status, message,
                lastReminder.get("created_at"), triggerSource, scheduleIntervalMinutes, nodeId, durationMs,
                reminderLogId);
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
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore,
                       a.attempt_no AS attemptNo, a.status, p.paper_name AS paperName, s.subject_name AS subjectName,
                       CASE WHEN COALESCE(sr.status, 0) = 1 AND a.status = 5 AND a.score IS NOT NULL
                              AND NOT EXISTS (
                                SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = a.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                              )
                            THEN a.score ELSE NULL END AS score,
                       CASE WHEN COALESCE(sr.status, 0) = 1 AND a.status = 5 AND a.score IS NOT NULL
                              AND NOT EXISTS (
                                SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = a.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                              )
                            THEN 1 ELSE 0 END AS scoreVisible,
                       CASE
                         WHEN a.status = 4 THEN 'PENDING_REVIEW'
                          WHEN EXISTS (
                                 SELECT 1 FROM score_appeal sa
                                WHERE sa.attempt_id = a.id
                                  AND sa.status = 1
                                  AND sa.handling_result = 'RECHECK_REQUIRED'
                               ) THEN 'PENDING_RECHECK'
                          WHEN a.status <> 5 THEN 'PENDING_FINALIZE'
                          WHEN a.score IS NULL THEN 'PENDING_SCORE'
                          WHEN COALESCE(sr.status, 0) = 1 THEN 'RELEASED'
                         WHEN sr.revoked_at IS NOT NULL OR COALESCE(sr.revoke_reason, sr.note) IS NOT NULL THEN 'REVOKED'
                         ELSE 'PENDING_RELEASE'
                       END AS scoreVisibility,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus, sr.published_at AS scorePublishedAt,
                       a.rules_confirmed_at AS rulesConfirmedAt, a.submit_time AS submitTime,
                       NOW() AS serverTime,
                       CASE
                         WHEN a.status >= 2 THEN 'SUBMITTED'
                         WHEN e.status <> ? THEN 'UNPUBLISHED'
                         WHEN e.start_time IS NOT NULL AND e.start_time > NOW() THEN 'WAITING'
                         WHEN e.end_time IS NOT NULL AND e.end_time <= NOW() THEN 'CLOSED'
                         WHEN a.status = 1 THEN 'IN_PROGRESS'
                         ELSE 'READY'
                       END AS accessStatus,
                       CASE WHEN a.status < 2
                              AND e.status = ?
                              AND (e.start_time IS NULL OR e.start_time <= NOW())
                              AND (e.end_time IS NULL OR e.end_time > NOW())
                            THEN 1 ELSE 0 END AS canStart,
                       CASE WHEN e.start_time IS NULL
                            THEN 0 ELSE GREATEST(TIMESTAMPDIFF(SECOND, NOW(), e.start_time), 0) END AS secondsUntilStart,
                       CASE WHEN e.end_time IS NULL
                            THEN NULL ELSE GREATEST(TIMESTAMPDIFF(SECOND, NOW(), e.end_time), 0) END AS secondsUntilEnd
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE a.user_id = ? AND e.deleted = 0
                ORDER BY e.start_time DESC, e.id DESC, a.attempt_no DESC
                LIMIT ? OFFSET ?
                """, EXAM_STATUS_PUBLISHED, EXAM_STATUS_PUBLISHED, user.getId(), safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    private String approvalQueueWhere(String keyword, String creatorKeyword, Integer status,
                                      String startFrom, String startTo, String risk, Long examId, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        if (examId != null) {
            where.append(" AND e.id = ?");
            params.add(examId);
        }
        String kw = blankToNull(keyword);
        if (kw != null) {
            where.append("""
                    AND (e.exam_name LIKE CONCAT('%', ?, '%')
                         OR p.paper_name LIKE CONCAT('%', ?, '%')
                         OR s.subject_name LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String creatorKw = blankToNull(creatorKeyword);
        if (creatorKw != null) {
            where.append("""
                    AND (u.real_name LIKE CONCAT('%', ?, '%') OR u.username LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(creatorKw);
            params.add(creatorKw);
        }
        if (status != null) {
            where.append(" AND e.status = ?");
            params.add(status);
        }
        String from = blankToNull(startFrom);
        if (from != null) {
            where.append(" AND e.start_time >= ?");
            params.add(from);
        }
        String to = blankToNull(startTo);
        if (to != null) {
            where.append(" AND e.start_time <= ?");
            params.add(to);
        }
        String riskValue = blankToNull(risk);
        if (riskValue != null) {
            switch (riskValue) {
                case "PAST_START" -> where.append(" AND e.status = 0 AND e.start_time <= NOW()");
                case "NO_TARGET" -> where.append("""
                        AND NOT EXISTS (SELECT 1 FROM exam_target et WHERE et.exam_id = e.id)
                        """);
                case "NO_QUESTIONS" -> where.append("""
                        AND COALESCE(NULLIF((SELECT COUNT(*)
                                             FROM exam_question_snapshot eqs_count
                                             WHERE eqs_count.exam_id = e.id), 0),
                                     (SELECT COUNT(*)
                                      FROM paper_question pq_count
                                      WHERE pq_count.paper_id = e.paper_id)) = 0
                        """);
                case "PASS_SCORE_OVER_TOTAL" -> where.append("""
                        AND e.pass_score IS NOT NULL
                        AND e.pass_score > COALESCE((SELECT SUM(eqs_score.score)
                                                     FROM exam_question_snapshot eqs_score
                                                     WHERE eqs_score.exam_id = e.id),
                                                    (SELECT COALESCE(SUM(pq_score.score), 0)
                                                     FROM paper_question pq_score
                                                     WHERE pq_score.paper_id = e.paper_id))
                        """);
                default -> {
                }
            }
        }
        return where.toString();
    }

    public List<Map<String, Object>> listStudentExams(AuthUser user) {
        return listStudentExams(user, 1, 200).getList();
    }

    public List<Map<String, Object>> listTargetStudents(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Long> studentIds = teachingScopeService.visibleStudentUserIds(user);
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", studentIds.stream().map(id -> "?").toList());
        return jt.queryForList("""
                SELECT u.id AS userId, u.real_name AS realName, sp.student_no AS studentNo,
                       c.class_name AS className, c.class_code AS classCode
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE u.deleted = 0 AND u.status = 1 AND u.id IN ( 
                """
                + placeholders +
                """
                )
                ORDER BY c.class_name, sp.student_no, u.real_name
                """, studentIds.toArray());
    }

    @Transactional
    public Map<String, Object> createExam(ExamRequest request, AuthUser creator) {
        JdbcTemplate jt = requireJdbcTemplate();
        ExamPublishPlan plan = validateExamPublishPlan(jt, request, creator);
        List<TargetSpec> targets = plan.targets;
        jt.update("""
                INSERT INTO exam (paper_id, exam_name, description, start_time, end_time, duration_minutes, max_attempts, pass_score, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.getPaperId(), trim(request.getExamName()), trim(request.getDescription()),
                request.getStartTime(), request.getEndTime(), request.getDurationMinutes(), request.getMaxAttempts(),
                request.getPassScore(), EXAM_STATUS_PENDING_APPROVAL, creator.getId());
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

        if (creator.hasRole("ADMIN")) {
            createPaperSnapshot(jt, examId, request.getPaperId());
            PublishNotificationStats publishStats = publishApprovedExam(jt, examId, trim(request.getExamName()), plan);
            recordApprovalLog(jt, examId, APPROVAL_ACTION_DIRECT_PUBLISH,
                    EXAM_STATUS_PENDING_APPROVAL, EXAM_STATUS_PUBLISHED, null, creator.getId(), publishStats);
            Map<String, Object> result = getExamById(examId);
            attachPublishNotificationStats(result, publishStats);
            return result;
        } else {
            recordApprovalLog(jt, examId, APPROVAL_ACTION_SUBMIT,
                    null, EXAM_STATUS_PENDING_APPROVAL, null, creator.getId());
        }
        return getExamById(examId);
    }

    @Transactional
    public Map<String, Object> approveExam(Long id, ExamApprovalDecisionRequest decision, AuthUser approver) {
        if (!approver.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can approve exams");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        id = requirePositiveExamId(id);
        int status = currentExamStatusForUpdate(jt, id);
        if (status != EXAM_STATUS_PENDING_APPROVAL) {
            throw new IllegalStateException("Only pending exams can be approved");
        }
        ExamRequest request = loadExamRequestForApproval(jt, id);
        ExamPublishPlan plan = validateExamPublishPlan(jt, request, approver);
        createPaperSnapshot(jt, id, request.getPaperId());
        PublishNotificationStats publishStats = publishApprovedExam(jt, id, trim(request.getExamName()), plan);
        String note = normalizeApprovalNote(decision == null ? null : decision.getNote());
        Long approvalLogId = recordApprovalLog(jt, id, APPROVAL_ACTION_APPROVE,
                EXAM_STATUS_PENDING_APPROVAL, EXAM_STATUS_PUBLISHED, note,
                approver.getId(), publishStats);
        notifyExamCreator(jt, id, "Exam approved: " + trim(request.getExamName()),
                "Your exam has been approved and published.", teacherExamLink(id));
        Map<String, Object> result = getExamById(id);
        attachPublishNotificationStats(result, publishStats);
        result.put("approvalLogId", approvalLogId);
        return result;
    }

    @Transactional
    public Map<String, Object> rejectExam(Long id, ExamApprovalDecisionRequest decision, AuthUser approver) {
        if (!approver.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can reject exams");
        }
        id = requirePositiveExamId(id);
        String note = normalizeApprovalNote(decision == null ? null : decision.getNote());
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Reject reason is required");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int status = currentExamStatusForUpdate(jt, id);
        if (status != EXAM_STATUS_PENDING_APPROVAL) {
            throw new IllegalStateException("Only pending exams can be rejected");
        }
        jt.update("UPDATE exam SET status = ? WHERE id = ? AND deleted = 0", EXAM_STATUS_REJECTED, id);
        Long approvalLogId = recordApprovalLog(jt, id, APPROVAL_ACTION_REJECT,
                EXAM_STATUS_PENDING_APPROVAL, EXAM_STATUS_REJECTED, note, approver.getId());
        String examName = jt.queryForObject("SELECT exam_name FROM exam WHERE id = ?", String.class, id);
        notifyExamCreator(jt, id, "Exam rejected: " + trim(examName), note, teacherExamLink(id));
        Map<String, Object> result = getExamById(id);
        result.put("approvalLogId", approvalLogId);
        return result;
    }

    public List<Map<String, Object>> listApprovalLogs(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT l.id, l.exam_id AS examId, l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.candidate_count AS candidateCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM exam_approval_log l
                LEFT JOIN sys_user u ON u.id = l.actor_id
                WHERE l.exam_id = ?
                ORDER BY l.created_at ASC, l.id ASC
                """, id);
    }

    public ExportFile exportApprovalLogs(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        String examName = jt.queryForObject("""
                SELECT exam_name
                FROM exam
                WHERE id = ? AND deleted = 0
                """, String.class, id);
        List<Map<String, Object>> logs = listApprovalLogs(id, user);
        List<String> headers = List.of(
                "Log ID", "Time", "Exam", "Action", "Status From", "Status To", "Actor",
                "Candidate Count", "Notified Student Count", "Notified Attempt Count", "Note"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    nullable(log.get("id")),
                    nullable(log.get("createdAt")),
                    nullable(examName),
                    nullable(log.get("action")),
                    nullable(log.get("statusFrom")),
                    nullable(log.get("statusTo")),
                    nullable(log.get("actorName")),
                    nullable(log.get("candidateCount")),
                    nullable(log.get("notifiedStudentCount")),
                    nullable(log.get("notifiedAttemptCount")),
                    nullable(log.get("note"))
            ));
        }
        return new ExportFile(safeExportName(examName) + "-approval-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public List<Map<String, Object>> listScoreReleaseLogs(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        return jt.queryForList("""
                SELECT l.id, l.exam_id AS examId, l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.visible_attempt_count AS visibleAttemptCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM score_release_log l
                LEFT JOIN sys_user u ON u.id = l.actor_id
                WHERE l.exam_id = ?
                ORDER BY l.created_at ASC, l.id ASC
                """, id);
    }

    public ExportFile exportScoreReleaseLogs(Long id, AuthUser user) {
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        String examName = jt.queryForObject("""
                SELECT exam_name
                FROM exam
                WHERE id = ? AND deleted = 0
                """, String.class, id);
        List<Map<String, Object>> logs = listScoreReleaseLogs(id, user);
        List<String> headers = List.of(
                "Log ID", "Time", "Exam", "Action", "Status From", "Status To", "Actor",
                "Visible Attempt Count", "Notified Student Count", "Notified Attempt Count", "Note"
        );
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    nullable(log.get("id")),
                    nullable(log.get("createdAt")),
                    nullable(examName),
                    nullable(log.get("action")),
                    nullable(log.get("statusFrom")),
                    nullable(log.get("statusTo")),
                    nullable(log.get("actorName")),
                    nullable(log.get("visibleAttemptCount")),
                    nullable(log.get("notifiedStudentCount")),
                    nullable(log.get("notifiedAttemptCount")),
                    nullable(log.get("note"))
            ));
        }
        return new ExportFile(safeExportName(examName) + "-score-release-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public Map<String, Object> scoreReleaseSafety(String keyword, String state, AuthUser user, int page, int size) {
        String normalizedState = normalizeScoreSafetyState(state);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        List<Map<String, Object>> allRows = scoreReleaseSafetyRows(keyword, user);
        Map<String, Object> summary = scoreReleaseSafetySummary(allRows);
        List<Map<String, Object>> filtered = allRows.stream()
                .filter(row -> matchesScoreSafetyState(row, normalizedState))
                .toList();
        int offset = Math.min((safePage - 1) * safeSize, filtered.size());
        int end = Math.min(offset + safeSize, filtered.size());
        Map<String, Object> result = new HashMap<>();
        result.put("state", normalizedState);
        result.put("summary", summary);
        result.put("page", PageResult.of(filtered.subList(offset, end), filtered.size(), safePage, safeSize));
        return result;
    }

    public ExportFile exportScoreReleaseSafety(String keyword, String state, AuthUser user) {
        String normalizedState = normalizeScoreSafetyState(state);
        List<Map<String, Object>> rows = scoreReleaseSafetyRows(keyword, user).stream()
                .filter(row -> matchesScoreSafetyState(row, normalizedState))
                .limit(5000)
                .toList();
        List<String> headers = List.of(
                "Exam ID", "Exam", "Paper", "Subject", "State", "Ready", "Release Status",
                "Attempts", "Completed", "Scored Completed", "Unscored Completed", "Active",
                "Pending Review", "Pending Answers", "Pending Appeals", "Open Recheck",
                "Blockers", "First Action"
        );
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(List.of(
                    nullable(row.get("examId")),
                    nullable(row.get("examName")),
                    nullable(row.get("paperName")),
                    nullable(row.get("subjectName")),
                    nullable(row.get("scoreSafetyState")),
                    nullable(row.get("scoreReleaseReady")),
                    nullable(row.get("scoreReleaseStatus")),
                    nullable(row.get("attemptCount")),
                    nullable(row.get("completedAttemptCount")),
                    nullable(row.get("scoredCompletedAttemptCount")),
                    nullable(row.get("unscoredCompletedAttemptCount")),
                    nullable(row.get("activeAttemptCount")),
                    nullable(row.get("pendingReviewAttemptCount")),
                    nullable(row.get("pendingAnswerReviewCount")),
                    nullable(row.get("pendingScoreAppealCount")),
                    nullable(row.get("openRecheckAppealCount")),
                    nullable(row.get("scoreReleaseBlockers")),
                    nullable(row.get("nextAction"))
            ));
        }
        return new ExportFile("score-release-safety-" + normalizedState.toLowerCase(Locale.ROOT)
                + "-" + LocalDate.now() + ".csv", CsvExport.build(headers, data));
    }

    public Map<String, Object> examLifecycleHealth(String keyword, String state, AuthUser user, int page, int size) {
        String normalizedState = normalizeLifecycleHealthState(state);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        List<Map<String, Object>> allRows = examLifecycleHealthRows(keyword, user);
        Map<String, Object> summary = examLifecycleHealthSummary(allRows);
        List<Map<String, Object>> filtered = allRows.stream()
                .filter(row -> matchesLifecycleHealthState(row, normalizedState))
                .toList();
        int offset = Math.min((safePage - 1) * safeSize, filtered.size());
        int end = Math.min(offset + safeSize, filtered.size());
        Map<String, Object> result = new HashMap<>();
        result.put("state", normalizedState);
        result.put("summary", summary);
        result.put("page", PageResult.of(filtered.subList(offset, end), filtered.size(), safePage, safeSize));
        return result;
    }

    public ExportFile exportExamLifecycleHealth(String keyword, String state, AuthUser user) {
        String normalizedState = normalizeLifecycleHealthState(state);
        List<Map<String, Object>> rows = examLifecycleHealthRows(keyword, user).stream()
                .filter(row -> matchesLifecycleHealthState(row, normalizedState))
                .limit(5000)
                .toList();
        List<String> headers = List.of(
                "Exam ID", "Exam", "Paper", "Subject", "Lifecycle State", "Group", "Severity",
                "Action Required", "Next Action", "Next Action Type", "Blockers",
                "Targets", "Candidate Snapshots", "Question Snapshots",
                "Attempts", "Not Started", "Active", "Submitted", "Completed",
                "Scored Completed", "Unscored Completed", "Pending Review Attempts", "Pending Answer Reviews",
                "Pending Appeals", "Open Recheck", "Monitor Sessions", "Monitor Events",
                "Offline Monitor", "High Risk Monitor", "Stale Drafts", "Timeout Pressure",
                "Deadline Passed Active", "Forced Submit", "Score Release Status", "Score Ready",
                "Score Blockers", "Start Time", "End Time"
        );
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(List.of(
                    nullable(row.get("examId")),
                    nullable(row.get("examName")),
                    nullable(row.get("paperName")),
                    nullable(row.get("subjectName")),
                    nullable(row.get("lifecycleState")),
                    nullable(row.get("lifecycleGroup")),
                    nullable(row.get("lifecycleSeverity")),
                    nullable(row.get("lifecycleActionRequired")),
                    nullable(row.get("lifecycleNextAction")),
                    nullable(row.get("lifecycleNextActionType")),
                    nullable(row.get("lifecycleBlockerCodes")),
                    nullable(row.get("targetCount")),
                    nullable(row.get("candidateSnapshotCount")),
                    nullable(row.get("questionSnapshotCount")),
                    nullable(row.get("attemptCount")),
                    nullable(row.get("notStartedAttemptCount")),
                    nullable(row.get("activeAttemptCount")),
                    nullable(row.get("submittedCount")),
                    nullable(row.get("completedAttemptCount")),
                    nullable(row.get("scoredCompletedAttemptCount")),
                    nullable(row.get("unscoredCompletedAttemptCount")),
                    nullable(row.get("pendingReviewAttemptCount")),
                    nullable(row.get("pendingAnswerReviewCount")),
                    nullable(row.get("pendingScoreAppealCount")),
                    nullable(row.get("openRecheckAppealCount")),
                    nullable(row.get("monitorSessionCount")),
                    nullable(row.get("monitorEventCount")),
                    nullable(row.get("offlineMonitorCount")),
                    nullable(row.get("highRiskMonitorCount")),
                    nullable(row.get("staleDraftCount")),
                    nullable(row.get("timeoutPressureCount")),
                    nullable(row.get("deadlinePassedActiveCount")),
                    nullable(row.get("forcedSubmitCount")),
                    nullable(row.get("scoreReleaseStatus")),
                    nullable(row.get("scoreReleaseReady")),
                    nullable(row.get("scoreReleaseBlockers")),
                    nullable(row.get("startTime")),
                    nullable(row.get("endTime"))
            ));
        }
        return new ExportFile("exam-lifecycle-health-" + normalizedState.toLowerCase(Locale.ROOT)
                + "-" + LocalDate.now() + ".csv", CsvExport.build(headers, data));
    }

    public Map<String, Object> examLifecycleHealthHandoff(String keyword, String state, AuthUser user) {
        String normalizedState = normalizeLifecycleHealthState(state);
        List<Map<String, Object>> allRows = examLifecycleHealthRows(keyword, user);
        Map<String, Object> summary = examLifecycleHealthSummary(allRows);
        List<Map<String, Object>> filtered = allRows.stream()
                .filter(row -> matchesLifecycleHealthState(row, normalizedState))
                .toList();
        List<Map<String, Object>> actionRows = filtered.stream()
                .filter(this::isLifecycleHandoffCandidate)
                .sorted(Comparator.comparingInt(this::lifecycleHandoffPriority))
                .limit(20)
                .map(this::lifecycleHandoffRow)
                .toList();
        if (actionRows.isEmpty()) {
            actionRows = filtered.stream()
                    .limit(10)
                    .map(this::lifecycleHandoffRow)
                    .toList();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("keyword", blankToNull(keyword));
        result.put("state", normalizedState);
        result.put("role", user == null ? null : user.getPrimaryRole());
        result.put("summary", summary);
        result.put("filteredTotal", filtered.size());
        result.put("actionTotal", actionRows.size());
        result.put("topBlockers", lifecycleTopBlockers(summary));
        result.put("groupCounts", lifecycleCountBy(filtered, "lifecycleGroup"));
        result.put("stateCounts", lifecycleCountBy(filtered, "lifecycleState"));
        result.put("severityCounts", lifecycleCountBy(filtered, "lifecycleSeverity"));
        result.put("actionRows", actionRows);
        return result;
    }

    public Map<String, Object> notifyExamLifecycleHealthHandoff(String keyword, String state, String audience,
                                                                AuthUser user) {
        String normalizedAudience = normalizeLifecycleHandoffAudience(audience);
        String normalizedState = normalizeLifecycleHealthState(state);
        List<Map<String, Object>> allRows = examLifecycleHealthRows(keyword, user);
        Map<String, Object> summary = examLifecycleHealthSummary(allRows);
        List<Map<String, Object>> filtered = allRows.stream()
                .filter(row -> matchesLifecycleHealthState(row, normalizedState))
                .toList();
        List<Map<String, Object>> actionRows = filtered.stream()
                .filter(this::isLifecycleHandoffCandidate)
                .sorted(Comparator.comparingInt(this::lifecycleHandoffPriority))
                .limit(20)
                .toList();
        if (actionRows.isEmpty()) {
            actionRows = filtered.stream().limit(10).toList();
        }
        Set<Long> recipientIds = lifecycleHandoffRecipientIds(actionRows, normalizedAudience, user);
        String link = lifecycleHealthHandoffLink(keyword, normalizedState);
        String title = trimToLength("Lifecycle handoff: " + normalizedState + " (" + actionRows.size() + " actions)", 128);
        String content = lifecycleHandoffNotificationContent(normalizedState, summary, filtered.size(), actionRows);
        List<Long> notificationIds = new ArrayList<>();
        for (Long recipientId : recipientIds) {
            Long notificationId = notificationService.sendAndReturnId(recipientId,
                    title,
                    content,
                    "LIFECYCLE_HANDOFF",
                    link,
                    "EXAM_LIFECYCLE_HANDOFF",
                    null);
            if (notificationId != null) {
                notificationIds.add(notificationId);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("audience", normalizedAudience);
        result.put("state", normalizedState);
        result.put("keyword", blankToNull(keyword));
        result.put("link", link);
        result.put("recipientCount", recipientIds.size());
        result.put("notificationIds", notificationIds);
        result.put("notificationCount", notificationIds.size());
        result.put("handoff", examLifecycleHealthHandoff(keyword, normalizedState, user));
        return result;
    }

    private List<Map<String, Object>> examLifecycleHealthRows(String keyword, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        String kw = blankToNull(keyword);
        int highRiskThreshold = configNumber(jt, "monitor.riskHighThreshold", 20);
        List<Object> params = new ArrayList<>();
        params.add(highRiskThreshold);
        params.add(kw);
        params.add(kw);
        params.add(kw);
        String scopeSql = appendExamScope(user, params);
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.id, e.id AS examId, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore,
                       e.status, e.created_by AS createdBy, p.paper_name AS paperName, s.subject_name AS subjectName,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       sr.published_at AS scorePublishedAt,
                       sr.revoked_at AS scoreRevokedAt,
                       COALESCE(sr.revoke_reason, sr.note) AS scoreRevokeReason,
                       (SELECT COUNT(*) FROM exam_target et WHERE et.exam_id = e.id) AS targetCount,
                       (SELECT COUNT(*) FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id) AS candidateSnapshotCount,
                       (SELECT COUNT(*) FROM exam_question_snapshot eqs WHERE eqs.exam_id = e.id) AS questionSnapshotCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 0) AS notStartedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status IN (2,4,5)) AS submittedCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                       (SELECT COUNT(*)
                        FROM answer_record ar
                        JOIN exam_attempt a ON a.id = ar.attempt_id
                        WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0) AS startedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.submit_type = 'FORCED') AS forcedSubmitCount,
                       (SELECT COUNT(*)
                        FROM exam_attempt a
                        WHERE a.exam_id = e.id AND a.status = 1
                          AND a.start_time IS NOT NULL
                          AND e.duration_minutes IS NOT NULL
                          AND TIMESTAMPDIFF(SECOND, NOW(), DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)) BETWEEN 0 AND 300
                       ) AS timeoutPressureCount,
                       (SELECT COUNT(*)
                        FROM exam_attempt a
                        WHERE a.exam_id = e.id AND a.status = 1
                          AND (
                            (e.end_time IS NOT NULL AND e.end_time < NOW())
                            OR (a.start_time IS NOT NULL AND e.duration_minutes IS NOT NULL
                                AND DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < NOW())
                          )
                       ) AS deadlinePassedActiveCount,
                       (SELECT COUNT(*) FROM exam_monitor_session ms WHERE ms.exam_id = e.id) AS monitorSessionCount,
                       (SELECT COUNT(*)
                        FROM exam_monitor_session ms
                        JOIN exam_attempt a ON a.id = ms.attempt_id
                        WHERE ms.exam_id = e.id AND a.status = 1
                          AND (ms.last_heartbeat_at IS NULL OR ms.last_heartbeat_at < DATE_SUB(NOW(), INTERVAL 90 SECOND))
                       ) AS offlineMonitorCount,
                       (SELECT COUNT(*)
                        FROM exam_monitor_session ms
                        JOIN exam_attempt a ON a.id = ms.attempt_id
                        WHERE ms.exam_id = e.id AND a.status = 1 AND ms.risk_score >= ?
                       ) AS highRiskMonitorCount,
                       (SELECT COALESCE(SUM(ms.event_count), 0) FROM exam_monitor_session ms WHERE ms.exam_id = e.id) AS monitorEventCount,
                       (SELECT COUNT(*)
                        FROM exam_answer_draft d
                        JOIN exam_attempt a ON a.id = d.attempt_id
                        WHERE a.exam_id = e.id AND a.status = 1
                          AND d.updated_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
                       ) AS staleDraftCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 0) AS pendingScoreAppealCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 1
                          AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE e.deleted = 0
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%')) 
                """
                + scopeSql +
                """
                ORDER BY
                  CASE WHEN e.status = 0 THEN 0
                       WHEN e.status = 1 AND e.start_time <= NOW() AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                       WHEN e.status = 1 THEN 2
                       ELSE 3 END,
                  e.end_time DESC,
                  e.id DESC
                LIMIT 5000
                """, params.toArray());
        appendScoreReleaseReadiness(rows);
        enrichExamLifecycleHealth(rows);
        return rows;
    }

    private Map<String, Object> examLifecycleHealthSummary(List<Map<String, Object>> rows) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Integer> blockerCounts = new TreeMap<>();
        int actionRequired = 0;
        int approval = 0;
        int waiting = 0;
        int running = 0;
        int review = 0;
        int scoreReady = 0;
        int released = 0;
        int risk = 0;
        for (Map<String, Object> row : rows) {
            String group = stringValue(row.get("lifecycleGroup"));
            String state = stringValue(row.get("lifecycleState"));
            if (longValue(row.get("lifecycleActionRequired"), 0L) == 1L) {
                actionRequired++;
            }
            if ("APPROVAL".equals(group)) {
                approval++;
            } else if ("WAITING".equals(group)) {
                waiting++;
            } else if ("RUNNING".equals(group)) {
                running++;
            } else if ("REVIEW".equals(group)) {
                review++;
            } else if ("RISK".equals(group)) {
                risk++;
            }
            if ("SCORE_READY".equals(state)) {
                scoreReady++;
            }
            if ("SCORE_RELEASED".equals(state)) {
                released++;
            }
            Object blockers = row.get("lifecycleBlockers");
            if (blockers instanceof List<?> list) {
                for (Object blocker : list) {
                    String code = String.valueOf(blocker);
                    blockerCounts.put(code, blockerCounts.getOrDefault(code, 0) + 1);
                }
            }
        }
        summary.put("total", rows.size());
        summary.put("actionRequired", actionRequired);
        summary.put("approval", approval);
        summary.put("waiting", waiting);
        summary.put("running", running);
        summary.put("review", review);
        summary.put("scoreReady", scoreReady);
        summary.put("released", released);
        summary.put("risk", risk);
        summary.put("blockerCounts", blockerCounts);
        return summary;
    }

    private String normalizeLifecycleHealthState(String state) {
        String value = state == null ? "ALL" : state.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ALL", "ACTION_REQUIRED", "APPROVAL", "WAITING", "RUNNING", "REVIEW",
                    "SCORE_READY", "RELEASED", "RISK" -> value;
            default -> "ALL";
        };
    }

    private boolean matchesLifecycleHealthState(Map<String, Object> row, String state) {
        String group = stringValue(row.get("lifecycleGroup"));
        String current = stringValue(row.get("lifecycleState"));
        return switch (state) {
            case "ACTION_REQUIRED" -> longValue(row.get("lifecycleActionRequired"), 0L) == 1L;
            case "APPROVAL" -> "APPROVAL".equals(group);
            case "WAITING" -> "WAITING".equals(group);
            case "RUNNING" -> "RUNNING".equals(group);
            case "REVIEW" -> "REVIEW".equals(group);
            case "SCORE_READY" -> "SCORE_READY".equals(current);
            case "RELEASED" -> "SCORE_RELEASED".equals(current);
            case "RISK" -> "RISK".equals(group);
            default -> true;
        };
    }

    private boolean isLifecycleHandoffCandidate(Map<String, Object> row) {
        String severity = stringValue(row.get("lifecycleSeverity"));
        String group = stringValue(row.get("lifecycleGroup"));
        return longValue(row.get("lifecycleActionRequired"), 0L) == 1L
                || "RISK".equals(group)
                || "HIGH".equals(severity)
                || "WARN".equals(severity);
    }

    private int lifecycleHandoffPriority(Map<String, Object> row) {
        int actionRank = longValue(row.get("lifecycleActionRequired"), 0L) == 1L ? 0 : 10;
        return actionRank + lifecycleSeverityRank(stringValue(row.get("lifecycleSeverity")));
    }

    private int lifecycleSeverityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "HIGH" -> 0;
            case "WARN" -> 1;
            case "INFO" -> 2;
            case "OK" -> 3;
            default -> 4;
        };
    }

    private List<Map<String, Object>> lifecycleTopBlockers(Map<String, Object> summary) {
        Object blockerCounts = summary.get("blockerCounts");
        if (!(blockerCounts instanceof Map<?, ?> counts)) {
            return List.of();
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", String.valueOf(entry.getKey()));
                    item.put("count", longValue(entry.getValue(), 0L));
                    return item;
                })
                .filter(item -> longValue(item.get("count"), 0L) > 0L)
                .sorted((left, right) -> Long.compare(longValue(right.get("count"), 0L), longValue(left.get("count"), 0L)))
                .limit(10)
                .toList();
    }

    private Map<String, Integer> lifecycleCountBy(List<Map<String, Object>> rows, String key) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String value = stringValue(row.get(key));
            if (value == null || value.isBlank()) {
                value = "UNKNOWN";
            }
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
        return counts;
    }

    private Map<String, Object> lifecycleHandoffRow(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("examId", nullable(row.get("examId")));
        item.put("examName", nullable(row.get("examName")));
        item.put("paperName", nullable(row.get("paperName")));
        item.put("subjectName", nullable(row.get("subjectName")));
        item.put("lifecycleState", nullable(row.get("lifecycleState")));
        item.put("lifecycleGroup", nullable(row.get("lifecycleGroup")));
        item.put("lifecycleSeverity", nullable(row.get("lifecycleSeverity")));
        item.put("lifecycleActionRequired", nullable(row.get("lifecycleActionRequired")));
        item.put("lifecycleNextAction", nullable(row.get("lifecycleNextAction")));
        item.put("lifecycleNextActionType", nullable(row.get("lifecycleNextActionType")));
        item.put("lifecycleBlockerCodes", nullable(row.get("lifecycleBlockerCodes")));
        item.put("targetPath", lifecycleHealthTargetPath(row));
        item.put("activeAttemptCount", nullable(row.get("activeAttemptCount")));
        item.put("completedAttemptCount", nullable(row.get("completedAttemptCount")));
        item.put("pendingReviewAttemptCount", nullable(row.get("pendingReviewAttemptCount")));
        item.put("pendingAnswerReviewCount", nullable(row.get("pendingAnswerReviewCount")));
        item.put("pendingScoreAppealCount", nullable(row.get("pendingScoreAppealCount")));
        item.put("openRecheckAppealCount", nullable(row.get("openRecheckAppealCount")));
        item.put("offlineMonitorCount", nullable(row.get("offlineMonitorCount")));
        item.put("highRiskMonitorCount", nullable(row.get("highRiskMonitorCount")));
        item.put("staleDraftCount", nullable(row.get("staleDraftCount")));
        item.put("timeoutPressureCount", nullable(row.get("timeoutPressureCount")));
        item.put("deadlinePassedActiveCount", nullable(row.get("deadlinePassedActiveCount")));
        return item;
    }

    private String lifecycleHealthTargetPath(Map<String, Object> row) {
        Long examId = numberValue(row.get("examId"));
        if (examId == null) {
            return "/exam-tasks";
        }
        String fallback = "/exam-tasks?examId=" + examId;
        String actionType = stringValue(row.get("lifecycleNextActionType"));
        return switch (actionType == null ? "" : actionType) {
            case "APPROVAL" -> "/exam-approvals?examId=" + examId;
            case "MONITOR" -> "/exam-monitor?examId=" + examId;
            case "REVIEW" -> "/reviews?reviewExamId=" + examId;
            case "RECHECK" -> "/reviews?reviewExamId=" + examId
                    + "&reviewTaskType=RECHECK&appealExamId=" + examId
                    + "&appealStatus=1&appealHandlingResult=RECHECK_REQUIRED";
            case "APPEALS" -> "/reviews?appealExamId=" + examId + "&appealStatus=0&appealHandlingResult=ALL";
            default -> fallback;
        };
    }

    private String normalizeLifecycleHandoffAudience(String audience) {
        String value = audience == null ? "SELF" : audience.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SELF", "OPERATORS" -> value;
            default -> throw new IllegalArgumentException("Unsupported lifecycle handoff audience: " + audience);
        };
    }

    private Set<Long> lifecycleHandoffRecipientIds(List<Map<String, Object>> actionRows, String audience, AuthUser user) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        if (user != null && user.getId() != null) {
            recipientIds.add(user.getId());
        }
        if (!"OPERATORS".equals(audience)) {
            return recipientIds;
        }
        JdbcTemplate jt = requireJdbcTemplate();
        recipientIds.addAll(jt.queryForList("""
                SELECT DISTINCT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.deleted = 0
                  AND u.status = 1
                  AND r.deleted = 0
                  AND r.status = 1
                  AND r.role_code = 'ADMIN'
                ORDER BY u.id
                """, Long.class));
        for (Map<String, Object> row : actionRows) {
            Long creatorId = numberValue(row.get("createdBy"));
            if (creatorId != null && lifecycleHandoffOperatorUser(jt, creatorId)) {
                recipientIds.add(creatorId);
            }
        }
        return recipientIds;
    }

    private boolean lifecycleHandoffOperatorUser(JdbcTemplate jt, Long userId) {
        Long count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.id = ?
                  AND u.deleted = 0
                  AND u.status = 1
                  AND r.deleted = 0
                  AND r.status = 1
                  AND r.role_code IN ('ADMIN', 'TEACHER')
                """, Long.class, userId);
        return count != null && count > 0;
    }

    private String lifecycleHealthHandoffLink(String keyword, String state) {
        StringBuilder link = new StringBuilder("/exam-tasks?lifecycleHealth=1");
        link.append("&lifecycleState=").append(encodeQueryValue(state));
        String kw = blankToNull(keyword);
        if (kw != null) {
            link.append("&lifecycleKeyword=").append(encodeQueryValue(kw));
        }
        return link.toString();
    }

    private String lifecycleHandoffNotificationContent(String state, Map<String, Object> summary,
                                                       int filteredTotal, List<Map<String, Object>> actionRows) {
        String blockers = lifecycleTopBlockers(summary).stream()
                .map(item -> item.get("code") + ":" + item.get("count"))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        return "Lifecycle handoff " + state
                + ". Filtered exams: " + filteredTotal
                + ", action required: " + nullable(summary.get("actionRequired"))
                + ", risk: " + nullable(summary.get("risk"))
                + ", review: " + nullable(summary.get("review"))
                + ", score ready: " + nullable(summary.get("scoreReady"))
                + ". Top blockers: " + blockers
                + ". Priority actions: " + actionRows.size()
                + ". Open the lifecycle health workbench to continue handling.";
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> scoreReleaseSafetyRows(String keyword, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        String kw = blankToNull(keyword);
        List<Object> params = new ArrayList<>();
        params.add(kw);
        params.add(kw);
        params.add(kw);
        String scopeSql = appendExamScope(user, params);
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.id, e.id AS examId, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore,
                       e.status, e.created_by AS createdBy, p.paper_name AS paperName, s.subject_name AS subjectName,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       sr.published_by AS scorePublishedBy,
                       COALESCE(pub.real_name, pub.username, CAST(sr.published_by AS CHAR)) AS scorePublishedByName,
                       sr.published_at AS scorePublishedAt,
                       sr.revoked_by AS scoreRevokedBy,
                       COALESCE(rev.real_name, rev.username, CAST(sr.revoked_by AS CHAR)) AS scoreRevokedByName,
                       sr.revoked_at AS scoreRevokedAt,
                       COALESCE(sr.publish_note, sr.note) AS scorePublishNote,
                       COALESCE(sr.revoke_reason, sr.note) AS scoreRevokeReason,
                       sr.note AS scoreReleaseNote,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                       (SELECT COUNT(*)
                        FROM answer_record ar
                        JOIN exam_attempt a ON a.id = ar.attempt_id
                        WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0) AS startedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 0) AS pendingScoreAppealCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 1
                          AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                LEFT JOIN sys_user pub ON pub.id = sr.published_by
                LEFT JOIN sys_user rev ON rev.id = sr.revoked_by
                WHERE e.deleted = 0
                  AND (? IS NULL OR e.exam_name LIKE CONCAT('%', ?, '%') OR p.paper_name LIKE CONCAT('%', ?, '%')) 
                """
                + scopeSql +
                """
                ORDER BY
                  CASE WHEN COALESCE(sr.status, 0) = 1 THEN 4 ELSE 0 END,
                  e.end_time DESC,
                  e.id DESC
                LIMIT 5000
                """, params.toArray());
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> enriched = new HashMap<>(row);
            List<String> blockers = scoreReleaseBlockers(enriched, now);
            List<Map<String, Object>> blockerDetails = scoreReleaseBlockerDetails(enriched, blockers);
            boolean ready = blockers.isEmpty();
            enriched.put("ended", scoreReleaseExamEnded(enriched, now) ? 1 : 0);
            enriched.put("ready", ready ? 1 : 0);
            enriched.put("scoreReleaseReady", ready ? 1 : 0);
            enriched.put("blockers", blockers);
            enriched.put("scoreReleaseBlockers", String.join(",", blockers));
            enriched.put("blockerDetails", blockerDetails);
            enriched.put("scoreSafetyState", scoreSafetyState(enriched, ready));
            enriched.put("nextAction", blockerDetails.isEmpty() ? "Publish scores" : blockerDetails.get(0).get("action"));
            result.add(enriched);
        }
        return result;
    }

    private Map<String, Object> scoreReleaseSafetySummary(List<Map<String, Object>> rows) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Integer> blockerCounts = new TreeMap<>();
        int ready = 0;
        int blocked = 0;
        int released = 0;
        int revoked = 0;
        int actionRequired = 0;
        for (Map<String, Object> row : rows) {
            String state = stringValue(row.get("scoreSafetyState"));
            if ("READY".equals(state)) {
                ready++;
            } else if ("BLOCKED".equals(state)) {
                blocked++;
            } else if ("RELEASED".equals(state)) {
                released++;
            } else if ("REVOKED".equals(state)) {
                revoked++;
            }
            if (!"RELEASED".equals(state) && longValue(row.get("scoreReleaseReady"), 0L) != 1L) {
                actionRequired++;
            }
            Object blockers = row.get("blockers");
            if (blockers instanceof List<?> list) {
                for (Object blocker : list) {
                    String code = String.valueOf(blocker);
                    blockerCounts.put(code, blockerCounts.getOrDefault(code, 0) + 1);
                }
            }
        }
        summary.put("total", rows.size());
        summary.put("ready", ready);
        summary.put("blocked", blocked);
        summary.put("released", released);
        summary.put("revoked", revoked);
        summary.put("actionRequired", actionRequired);
        summary.put("blockerCounts", blockerCounts);
        return summary;
    }

    private String normalizeScoreSafetyState(String state) {
        String value = state == null ? "ALL" : state.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ALL", "READY", "BLOCKED", "RELEASED", "REVOKED", "ACTION_REQUIRED" -> value;
            default -> "ALL";
        };
    }

    private boolean matchesScoreSafetyState(Map<String, Object> row, String state) {
        String current = stringValue(row.get("scoreSafetyState"));
        return switch (state) {
            case "READY" -> "READY".equals(current);
            case "BLOCKED" -> "BLOCKED".equals(current);
            case "RELEASED" -> "RELEASED".equals(current);
            case "REVOKED" -> "REVOKED".equals(current);
            case "ACTION_REQUIRED" -> !"RELEASED".equals(current)
                    && longValue(row.get("scoreReleaseReady"), 0L) != 1L;
            default -> true;
        };
    }

    private String scoreSafetyState(Map<String, Object> row, boolean ready) {
        if (longValue(row.get("scoreReleaseStatus"), 0L) == 1L) {
            return "RELEASED";
        }
        if (row.get("scoreRevokedAt") != null || blankToNull(stringValue(row.get("scoreRevokeReason"))) != null) {
            return "REVOKED";
        }
        return ready ? "READY" : "BLOCKED";
    }

    public Map<String, Object> getExamLifecycle(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();

        Map<String, Object> exam = jt.queryForMap("""
                SELECT e.id AS examId, e.paper_id AS paperId, e.exam_name AS examName, e.description,
                       e.start_time AS startTime, e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore, e.status,
                       e.created_by AS createdBy,
                       COALESCE(creator.real_name, creator.username, CAST(e.created_by AS CHAR)) AS creatorName,
                       e.created_at AS createdAt, e.updated_at AS updatedAt,
                       p.paper_name AS paperName, s.subject_name AS subjectName,
                       (SELECT COUNT(*) FROM exam_target et WHERE et.exam_id = e.id) AS targetCount,
                       (SELECT COUNT(*) FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id) AS candidateSnapshotCount,
                       (SELECT COUNT(*) FROM exam_question_snapshot eqs WHERE eqs.exam_id = e.id) AS questionSnapshotCount,
                       (SELECT COALESCE(SUM(eqs.score), 0)
                        FROM exam_question_snapshot eqs
                        WHERE eqs.exam_id = e.id) AS snapshotTotalScore
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN edu_subject s ON s.id = p.subject_id
                LEFT JOIN sys_user creator ON creator.id = e.created_by
                WHERE e.id = ? AND e.deleted = 0
                """, id);
        Map<String, Object> attemptStats = jt.queryForMap("""
                SELECT COUNT(*) AS attemptCount,
                       COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS notStartedCount,
                       COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS activeAttemptCount,
                       COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS pendingReviewAttemptCount,
                       COALESCE(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END), 0) AS completedAttemptCount,
                       COALESCE(SUM(CASE WHEN submit_type = 'MANUAL' THEN 1 ELSE 0 END), 0) AS manualSubmitCount,
                       COALESCE(SUM(CASE WHEN submit_type = 'TIMEOUT' THEN 1 ELSE 0 END), 0) AS timeoutSubmitCount,
                       COALESCE(SUM(CASE WHEN submit_type = 'FORCED' THEN 1 ELSE 0 END), 0) AS forcedSubmitCount,
                       MIN(start_time) AS firstStartedAt,
                       MAX(submit_time) AS lastSubmittedAt
                FROM exam_attempt
                WHERE exam_id = ?
                """, id);
        Map<String, Object> monitorStats = jt.queryForMap("""
                SELECT COUNT(*) AS monitorSessionCount,
                       COALESCE(SUM(CASE WHEN status = 'ONLINE' THEN 1 ELSE 0 END), 0) AS onlineSessionCount,
                       COALESCE(SUM(CASE WHEN status = 'OFFLINE' THEN 1 ELSE 0 END), 0) AS offlineSessionCount,
                       COALESCE(SUM(CASE WHEN risk_score >= 60 THEN 1 ELSE 0 END), 0) AS highRiskSessionCount,
                       COALESCE(SUM(event_count), 0) AS monitorEventCount,
                       MAX(last_event_at) AS latestMonitorEventAt
                FROM exam_monitor_session
                WHERE exam_id = ?
                """, id);
        List<Map<String, Object>> approvalLogs = listApprovalLogs(id, user);
        List<Map<String, Object>> scoreReleaseLogs = listScoreReleaseLogs(id, user);
        List<Map<String, Object>> operationLogs = jt.queryForList("""
                SELECT l.id, l.action, l.target, l.detail, l.ip, l.operator_id AS actorId,
                       COALESCE(l.operator_name, u.real_name, u.username, CAST(l.operator_id AS CHAR)) AS actorName,
                       l.created_at AS createdAt
                FROM operation_log l
                LEFT JOIN sys_user u ON u.id = l.operator_id
                WHERE l.target = ?
                ORDER BY l.created_at ASC, l.id ASC
                """, "EXAM#" + id);
        List<Map<String, Object>> monitorActions = jt.queryForList("""
                SELECT ma.id, ma.session_id AS sessionId, ma.attempt_id AS attemptId, ma.action_type AS actionType,
                       ma.note, ma.notification_sent AS notificationSent, ma.notification_id AS notificationId,
                       ma.handled_by AS actorId,
                       COALESCE(handler.real_name, handler.username, CAST(ma.handled_by AS CHAR)) AS actorName,
                       ma.handled_at AS createdAt,
                       ma.user_id AS studentUserId,
                       COALESCE(student.real_name, student.username, CAST(ma.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo
                FROM exam_monitor_action ma
                LEFT JOIN sys_user handler ON handler.id = ma.handled_by
                LEFT JOIN sys_user student ON student.id = ma.user_id
                LEFT JOIN student_profile sp ON sp.user_id = ma.user_id AND sp.deleted = 0
                WHERE ma.exam_id = ?
                ORDER BY ma.handled_at ASC, ma.id ASC
                """, id);
        List<Map<String, Object>> forcedSubmissions = jt.queryForList("""
                SELECT a.id AS attemptId, a.user_id AS studentUserId,
                       COALESCE(student.real_name, student.username, CAST(a.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo,
                       a.submit_time AS createdAt, a.submit_type AS submitType, a.submit_reason AS submitReason
                FROM exam_attempt a
                LEFT JOIN sys_user student ON student.id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                WHERE a.exam_id = ? AND a.submit_type = 'FORCED' AND a.submit_time IS NOT NULL
                ORDER BY a.submit_time ASC, a.id ASC
                """, id);

        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(lifecycleEvent("EXAM", id, "CREATED", exam.get("createdAt"),
                exam.get("createdBy"), exam.get("creatorName"), null, exam.get("status"), exam.get("description"),
                lifecycleDetails("paperName", exam.get("paperName"), "subjectName", exam.get("subjectName"),
                        "targetCount", exam.get("targetCount")), "EXAM", id));
        for (Map<String, Object> log : operationLogs) {
            timeline.add(lifecycleEvent("OPERATION_LOG", log.get("id"), stringValue(log.get("action")),
                    log.get("createdAt"), log.get("actorId"), log.get("actorName"), null, null, log.get("detail"),
                    lifecycleDetails("target", log.get("target"), "ip", log.get("ip")), "OPERATION_LOG", log.get("id")));
        }
        for (Map<String, Object> log : approvalLogs) {
            timeline.add(lifecycleEvent("EXAM_APPROVAL_LOG", log.get("id"),
                    "APPROVAL_" + stringValue(log.get("action")), log.get("createdAt"),
                    log.get("actorId"), log.get("actorName"), log.get("statusFrom"), log.get("statusTo"),
                    log.get("note"), lifecycleDetails("candidateCount", log.get("candidateCount"),
                            "notifiedStudentCount", log.get("notifiedStudentCount"),
                            "notifiedAttemptCount", log.get("notifiedAttemptCount")),
                    "EXAM_APPROVAL_LOG", log.get("id")));
        }
        for (Map<String, Object> log : scoreReleaseLogs) {
            timeline.add(lifecycleEvent("SCORE_RELEASE_LOG", log.get("id"),
                    "SCORE_" + stringValue(log.get("action")), log.get("createdAt"),
                    log.get("actorId"), log.get("actorName"), log.get("statusFrom"), log.get("statusTo"),
                    log.get("note"), lifecycleDetails("visibleAttemptCount", log.get("visibleAttemptCount"),
                            "notifiedStudentCount", log.get("notifiedStudentCount"),
                            "notifiedAttemptCount", log.get("notifiedAttemptCount")),
                    "SCORE_RELEASE_LOG", log.get("id")));
        }
        for (Map<String, Object> action : monitorActions) {
            timeline.add(lifecycleEvent("MONITOR_ACTION", action.get("id"),
                    "MONITOR_" + stringValue(action.get("actionType")), action.get("createdAt"),
                    action.get("actorId"), action.get("actorName"), null, null, action.get("note"),
                    lifecycleDetails("sessionId", action.get("sessionId"), "attemptId", action.get("attemptId"),
                            "studentName", action.get("studentName"), "studentNo", action.get("studentNo"),
                            "notificationSent", action.get("notificationSent"),
                            "notificationId", action.get("notificationId")),
                    "MONITOR_ACTION", action.get("id")));
        }
        for (Map<String, Object> submission : forcedSubmissions) {
            timeline.add(lifecycleEvent("ATTEMPT", submission.get("attemptId"), "ATTEMPT_FORCED_SUBMIT",
                    submission.get("createdAt"), null, "System", null, null, submission.get("submitReason"),
                    lifecycleDetails("attemptId", submission.get("attemptId"),
                            "studentName", submission.get("studentName"), "studentNo", submission.get("studentNo")),
                    "EXAM_ATTEMPT", submission.get("attemptId")));
        }

        sortLifecycleTimeline(timeline);
        int timelineTotal = timeline.size();
        List<Map<String, Object>> visibleTimeline = timeline;
        if (timelineTotal > MAX_EXAM_LIFECYCLE_EVENTS) {
            visibleTimeline = new ArrayList<>();
            visibleTimeline.add(timeline.get(0));
            visibleTimeline.addAll(timeline.subList(timelineTotal - MAX_EXAM_LIFECYCLE_EVENTS + 1, timelineTotal));
        }

        Map<String, Object> summary = new HashMap<>();
        summary.putAll(attemptStats);
        summary.putAll(monitorStats);
        summary.put("approvalLogCount", approvalLogs.size());
        summary.put("scoreReleaseLogCount", scoreReleaseLogs.size());
        summary.put("operationLogCount", operationLogs.size());
        summary.put("monitorActionCount", monitorActions.size());
        summary.put("timelineTotal", timelineTotal);
        summary.put("timelineReturned", visibleTimeline.size());
        summary.put("timelineTruncated", Math.max(0, timelineTotal - visibleTimeline.size()));

        Map<String, Object> result = new HashMap<>();
        result.put("exam", exam);
        result.put("summary", summary);
        result.put("timeline", visibleTimeline);
        result.put("approvalLogs", approvalLogs);
        result.put("scoreReleaseLogs", scoreReleaseLogs);
        result.put("operationLogs", operationLogs);
        result.put("monitorActions", monitorActions);
        return result;
    }

    private Map<String, Object> lifecycleEvent(String source, Object sourceId, String eventType, Object createdAt,
                                               Object actorId, Object actorName, Object statusFrom, Object statusTo,
                                               Object note, Map<String, Object> details,
                                               String relatedType, Object relatedId) {
        Map<String, Object> event = new HashMap<>();
        event.put("source", source);
        event.put("sourceId", sourceId);
        event.put("eventType", eventType == null ? source : eventType);
        event.put("createdAt", createdAt);
        event.put("actorId", actorId);
        event.put("actorName", actorName);
        event.put("statusFrom", statusFrom);
        event.put("statusTo", statusTo);
        event.put("note", note);
        event.put("relatedType", relatedType);
        event.put("relatedId", relatedId);
        if (details != null && !details.isEmpty()) {
            event.put("details", details);
        }
        return event;
    }

    private Map<String, Object> lifecycleDetails(Object... entries) {
        Map<String, Object> details = new HashMap<>();
        if (entries == null) {
            return details;
        }
        for (int i = 0; i + 1 < entries.length; i += 2) {
            Object key = entries[i];
            Object value = entries[i + 1];
            if (key != null && value != null) {
                details.put(String.valueOf(key), value);
            }
        }
        return details;
    }

    private void sortLifecycleTimeline(List<Map<String, Object>> timeline) {
        timeline.sort((left, right) -> {
            LocalDateTime leftTime = localDateTimeValue(left.get("createdAt"));
            LocalDateTime rightTime = localDateTimeValue(right.get("createdAt"));
            if (leftTime == null && rightTime == null) {
                return lifecycleSortKey(left).compareTo(lifecycleSortKey(right));
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            int byTime = leftTime.compareTo(rightTime);
            return byTime != 0 ? byTime : lifecycleSortKey(left).compareTo(lifecycleSortKey(right));
        });
    }

    private String lifecycleSortKey(Map<String, Object> event) {
        return String.valueOf(event.get("source")) + "#"
                + String.valueOf(event.get("sourceId")) + "#"
                + String.valueOf(event.get("eventType"));
    }

    private Long recordApprovalLog(JdbcTemplate jt, Long examId, String action,
                                   Integer statusFrom, Integer statusTo, String note, Long actorId) {
        return recordApprovalLog(jt, examId, action, statusFrom, statusTo, note, actorId,
                new PublishNotificationStats(0, 0, 0));
    }

    private Long recordApprovalLog(JdbcTemplate jt, Long examId, String action,
                                   Integer statusFrom, Integer statusTo, String note, Long actorId,
                                   PublishNotificationStats publishStats) {
        PublishNotificationStats stats = publishStats == null ? new PublishNotificationStats(0, 0, 0) : publishStats;
        jt.update("""
                INSERT INTO exam_approval_log (exam_id, action, status_from, status_to, note, actor_id,
                                               candidate_count, notified_student_count, notified_attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, examId, action, statusFrom, statusTo, blankToNull(note), actorId,
                stats.candidateCount, stats.notifiedStudentCount, stats.notifiedAttemptCount);
        return lastInsertId(jt);
    }

    private void attachPublishNotificationStats(Map<String, Object> result, PublishNotificationStats stats) {
        PublishNotificationStats safeStats = stats == null ? new PublishNotificationStats(0, 0, 0) : stats;
        result.put("publishCandidateCount", safeStats.candidateCount);
        result.put("publishNotifiedStudentCount", safeStats.notifiedStudentCount);
        result.put("publishNotifiedAttemptCount", safeStats.notifiedAttemptCount);
    }

    private Long recordScoreReleaseLog(JdbcTemplate jt, Long examId, String action,
                                       Integer statusFrom, Integer statusTo, String note, Long actorId,
                                       int visibleAttemptCount, int notifiedStudentCount, int notifiedAttemptCount) {
        jt.update("""
                INSERT INTO score_release_log (exam_id, action, status_from, status_to, note, actor_id,
                                               visible_attempt_count, notified_student_count, notified_attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, examId, action, statusFrom, statusTo, blankToNull(note), actorId,
                visibleAttemptCount, notifiedStudentCount, notifiedAttemptCount);
        return lastInsertId(jt);
    }

    private void notifyExamCreator(JdbcTemplate jt, Long examId, String title, String content, String link) {
        Long creatorId = jt.queryForObject("""
                SELECT created_by
                FROM exam
                WHERE id = ? AND deleted = 0
                """, Long.class, examId);
        if (creatorId != null) {
            notificationService.send(creatorId, title, content, "EXAM_APPROVAL", link, "EXAM", examId);
        }
    }

    private PublishNotificationStats publishApprovedExam(JdbcTemplate jt, Long examId, String examName, ExamPublishPlan plan) {
        for (Map.Entry<Long, TargetSpec> entry : plan.studentSources.entrySet()) {
            Long studentId = entry.getKey();
            TargetSpec source = entry.getValue();
            insertCandidateSnapshotIfMissing(jt, examId, studentId, source.targetType, source.targetId);
            insertAttemptIfMissing(jt, examId, studentId);
        }
        jt.update("UPDATE exam SET status = ? WHERE id = ? AND deleted = 0", EXAM_STATUS_PUBLISHED, examId);
        return notifyPublishedExamStudents(jt, examId, examName, plan.studentSources.keySet());
    }

    private PublishNotificationStats notifyPublishedExamStudents(JdbcTemplate jt, Long examId, String examName, Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return new PublishNotificationStats(0, 0, 0);
        }
        List<Object> params = new ArrayList<>();
        params.add(examId);
        params.addAll(studentIds);
        List<Map<String, Object>> attempts = jt.queryForList("""
                SELECT id AS attemptId, user_id AS userId
                FROM exam_attempt
                WHERE exam_id = ?
                  AND user_id IN (%s)
                  AND attempt_no = 1
                """.formatted(placeholders(studentIds.size())), params.toArray());
        Set<Long> notifiedStudentIds = new LinkedHashSet<>();
        int notifiedAttemptCount = 0;
        for (Map<String, Object> attempt : attempts) {
            Long attemptId = numberValue(attempt.get("attemptId"));
            Long studentId = numberValue(attempt.get("userId"));
            if (attemptId == null || studentId == null) {
                continue;
            }
            notificationService.sendOnceAndReturnId(studentId, "New exam: " + examName,
                    "A new exam has been published. Please check the exam center.", "EXAM",
                    studentExamLink(attemptId), "EXAM_ATTEMPT", attemptId);
            notifiedStudentIds.add(studentId);
            notifiedAttemptCount++;
        }
        return new PublishNotificationStats(studentIds.size(), notifiedStudentIds.size(), notifiedAttemptCount);
    }

    private ExamRequest loadExamRequestForApproval(JdbcTemplate jt, Long id) {
        Map<String, Object> row = jt.queryForMap("""
                SELECT paper_id AS paperId, exam_name AS examName, description,
                       start_time AS startTime, end_time AS endTime, duration_minutes AS durationMinutes,
                       max_attempts AS maxAttempts, pass_score AS passScore
                FROM exam
                WHERE id = ? AND deleted = 0
                """, id);
        ExamRequest request = new ExamRequest();
        request.setPaperId(numberValue(row.get("paperId")));
        request.setExamName(String.valueOf(row.get("examName")));
        request.setDescription((String) row.get("description"));
        request.setStartTime(localDateTimeValue(row.get("startTime")));
        request.setEndTime(localDateTimeValue(row.get("endTime")));
        request.setDurationMinutes(((Number) row.get("durationMinutes")).intValue());
        request.setMaxAttempts(((Number) row.get("maxAttempts")).intValue());
        request.setPassScore((BigDecimal) row.get("passScore"));
        request.setClassIds(loadTargetIds(jt, id, "CLASS"));
        request.setClassCourseIds(loadTargetIds(jt, id, "CLASS_COURSE"));
        request.setStudentUserIds(loadTargetIds(jt, id, "USER"));
        return request;
    }

    private List<Long> loadTargetIds(JdbcTemplate jt, Long examId, String targetType) {
        return jt.queryForList("""
                SELECT target_id
                FROM exam_target
                WHERE exam_id = ? AND target_type = ?
                ORDER BY id
                """, Long.class, examId, targetType);
    }

    public Map<String, Object> preflightExam(ExamRequest request, AuthUser creator) {
        JdbcTemplate jt = requireJdbcTemplate();
        try {
            ExamPublishPlan plan = validateExamPublishPlan(jt, request, creator);
            Map<String, Object> result = preflightResult(plan);
            result.put("ok", true);
            result.put("errors", List.of());
            return result;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Map<String, Object> result = new HashMap<>();
            result.put("ok", false);
            result.put("errors", List.of(ex.getMessage()));
            result.put("warnings", List.of());
            return result;
        }
    }

    public Map<String, Object> getExamById(Long examId) {
        examId = requirePositiveExamId(examId);
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> exam = jt.queryForMap("""
                SELECT e.*, p.paper_name AS paperName,
                       e.paper_id AS paperId, e.exam_name AS examName, e.start_time AS startTime,
                       e.end_time AS endTime, e.duration_minutes AS durationMinutes,
                       e.max_attempts AS maxAttempts, e.pass_score AS passScore,
                       (SELECT COUNT(*) FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id) AS candidateSnapshotCount,
                       (SELECT COUNT(*) FROM exam_question_snapshot eqs WHERE eqs.exam_id = e.id) AS questionSnapshotCount,
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

    public Map<String, Object> getExamSnapshot(Long examId, AuthUser user) {
        examId = requirePositiveExamId(examId);
        requireOwnedExam(examId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> exam = getExamById(examId);
        List<Map<String, Object>> candidates = loadCandidateSnapshotForAudit(jt, examId);
        List<Map<String, Object>> questions = loadQuestionSnapshotForAudit(jt, examId, numberValue(exam.get("paperId")));
        for (Map<String, Object> question : questions) {
            Long questionId = numberValue(question.get("questionId"));
            if (questionId != null && List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE")
                    .contains(String.valueOf(question.get("questionType")))) {
                question.put("options", loadExamQuestionOptions(jt, examId, questionId));
            }
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("exam", exam);
        snapshot.put("candidateCount", candidates.size());
        snapshot.put("questionCount", questions.size());
        snapshot.put("totalScore", questions.stream()
                .map(item -> item.get("score"))
                .filter(BigDecimal.class::isInstance)
                .map(BigDecimal.class::cast)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        snapshot.put("candidates", candidates);
        snapshot.put("questions", questions);
        return snapshot;
    }

    @Transactional
    public Map<String, Object> repairExamSnapshot(Long examId, AuthUser user) {
        examId = requirePositiveExamId(examId);
        requireOwnedExam(examId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        int status = currentExamStatusForUpdate(jt, examId);
        if (status != EXAM_STATUS_PUBLISHED && status != EXAM_STATUS_CLOSED) {
            throw new IllegalStateException("Only published or closed exams can repair snapshots");
        }
        Map<String, Object> exam = getExamById(examId);
        Long paperId = numberValue(exam.get("paperId"));
        if (paperId == null) {
            throw new IllegalStateException("Exam paper is missing");
        }
        long beforeCandidateCount = snapshotCount(jt, "exam_candidate_snapshot", examId);
        long beforeQuestionCount = snapshotCount(jt, "exam_question_snapshot", examId);
        ExamPublishPlan plan = loadExamPublishPlanForRepair(jt, examId, paperId, user);
        createPaperSnapshot(jt, examId, paperId);
        int insertedCandidates = 0;
        int insertedAttempts = 0;
        for (Map.Entry<Long, TargetSpec> entry : plan.studentSources.entrySet()) {
            Long studentId = entry.getKey();
            TargetSpec source = entry.getValue();
            insertedCandidates += insertCandidateSnapshotIfMissing(jt, examId, studentId,
                    source.targetType, source.targetId);
            insertedAttempts += insertAttemptIfMissing(jt, examId, studentId);
        }
        long afterCandidateCount = snapshotCount(jt, "exam_candidate_snapshot", examId);
        long afterQuestionCount = snapshotCount(jt, "exam_question_snapshot", examId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", examId);
        result.put("examName", exam.get("examName"));
        result.put("status", status);
        result.put("candidateSnapshotBefore", beforeCandidateCount);
        result.put("candidateSnapshotAfter", afterCandidateCount);
        result.put("questionSnapshotBefore", beforeQuestionCount);
        result.put("questionSnapshotAfter", afterQuestionCount);
        result.put("targetStudentCount", plan.studentSources.size());
        result.put("insertedCandidateSnapshots", insertedCandidates);
        result.put("insertedAttempts", insertedAttempts);
        result.put("questionSnapshotRebuilt", true);
        return result;
    }

    public ExportFile exportExamSnapshot(Long examId, AuthUser user) {
        examId = requirePositiveExamId(examId);
        requireOwnedExam(examId, user);
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> exam = getExamById(examId);
        List<Map<String, Object>> candidates = loadCandidateSnapshotForAudit(jt, examId);
        List<Map<String, Object>> questions = loadQuestionSnapshotForExport(jt, examId, numberValue(exam.get("paperId")));
        List<String> headers = List.of(
                "Section", "Exam ID", "Exam", "Paper", "Start Time", "End Time",
                "Item ID", "Name", "Student No", "Class", "Source Type", "Source ID",
                "Attempt Status", "Submit Type", "Active Attempt ID",
                "Question Type", "Sort Order", "Score", "Option Count", "Snapshot Time", "Note"
        );
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "EXAM",
                nullable(exam.get("id")),
                nullable(exam.get("examName")),
                nullable(exam.get("paperName")),
                nullable(exam.get("startTime")),
                nullable(exam.get("endTime")),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                nullable(exam.get("totalScore")),
                "",
                "",
                "Snapshot evidence export excludes student answers, correct answers, analysis, stems, and option content."
        ));
        Object targets = exam.get("targetSummary");
        if (targets != null && !String.valueOf(targets).isBlank()) {
            rows.add(List.of(
                    "TARGETS",
                    nullable(exam.get("id")),
                    nullable(exam.get("examName")),
                    nullable(exam.get("paperName")),
                    nullable(exam.get("startTime")),
                    nullable(exam.get("endTime")),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    nullable(targets)
            ));
        }
        for (Map<String, Object> candidate : candidates) {
            rows.add(List.of(
                    "CANDIDATE",
                    nullable(exam.get("id")),
                    nullable(exam.get("examName")),
                    nullable(exam.get("paperName")),
                    nullable(exam.get("startTime")),
                    nullable(exam.get("endTime")),
                    nullable(candidate.get("userId")),
                    nullable(candidate.get("realName")),
                    nullable(candidate.get("studentNo")),
                    nullable(candidate.get("className")),
                    nullable(candidate.get("sourceType")),
                    nullable(candidate.get("sourceId")),
                    nullable(candidate.get("latestAttemptStatus")),
                    nullable(candidate.get("submitType")),
                    nullable(candidate.get("activeAttemptId")),
                    "",
                    "",
                    "",
                    "",
                    nullable(candidate.get("createdAt")),
                    ""
            ));
        }
        for (Map<String, Object> question : questions) {
            rows.add(List.of(
                    "QUESTION",
                    nullable(exam.get("id")),
                    nullable(exam.get("examName")),
                    nullable(exam.get("paperName")),
                    nullable(exam.get("startTime")),
                    nullable(exam.get("endTime")),
                    nullable(question.get("questionId")),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    nullable(question.get("questionType")),
                    nullable(question.get("sortOrder")),
                    nullable(question.get("score")),
                    nullable(question.get("optionCount")),
                    nullable(question.get("createdAt")),
                    "Question structure only; stem, options, correct answer, and analysis are intentionally excluded."
            ));
        }
        return new ExportFile(safeExportName(stringValue(exam.get("examName"))) + "-snapshot-evidence-"
                + LocalDate.now() + ".csv", CsvExport.build(headers, rows));
    }

    @Transactional
    public Map<String, Object> startExam(Long attemptId, StartExamRequest request, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        attemptId = requirePositiveAttemptId(attemptId);
        Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, user.getId());

        int status = ((Number) attempt.get("status")).intValue();
        if (status >= 2) {
            Map<String, Object> result = new HashMap<>(submittedAttemptResult(jt, attempt, true));
            result.put("submitted", true);
            result.put("remainingSeconds", 0);
            return result;
        }
        Long examId = ((Number) attempt.get("exam_id")).longValue();
        requireCandidateSnapshotAccess(jt, examId, user.getId());
        Map<String, Object> exam = jt.queryForMap("""
                SELECT e.*, e.id AS examId, e.paper_id AS paperId, e.exam_name AS examName,
                       e.start_time AS startTime, e.end_time AS endTime,
                       e.duration_minutes AS durationMinutes, e.max_attempts AS maxAttempts,
                       e.pass_score AS passScore,
                       COALESCE((SELECT SUM(eqs_score.score)
                                 FROM exam_question_snapshot eqs_score
                                 WHERE eqs_score.exam_id = e.id),
                                p.total_score, 0) AS totalScore,
                       p.paper_name AS paperName
                FROM exam e
                JOIN paper p ON e.paper_id = p.id
                WHERE e.id = ?
                """, examId);
        Map<String, Object> startFinalization = finalizeAttemptOnStartIfClosedOrExpired(jt, attempt);
        if (!startFinalization.isEmpty()) {
            return startFinalization;
        }
        validateExamWindow(exam);
        requireStartRulesConfirmed(status, request);
        if (status == 0) {
            jt.update("""
                    UPDATE exam_attempt
                    SET status = 1,
                        start_time = NOW(),
                        rules_confirmed_at = COALESCE(rules_confirmed_at, NOW())
                    WHERE id = ? AND status = 0
                    """, attemptId);
        } else if (shouldRecordInProgressRulesConfirmation(attempt, request)) {
            jt.update("""
                    UPDATE exam_attempt
                    SET rules_confirmed_at = COALESCE(rules_confirmed_at, NOW())
                    WHERE id = ? AND status = 1
                    """, attemptId);
        }
        touchMonitorSession(jt, attemptId, examId, user.getId(), "ONLINE");
        Object paperId = exam.get("paper_id");
        List<Map<String, Object>> questions = loadExamQuestions(jt, examId, ((Number) paperId).longValue());

        for (Map<String, Object> q : questions) {
            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(q.get("questionType"))) {
                q.put("options", loadExamQuestionOptions(jt, examId, ((Number) q.get("questionId")).longValue()));
            }
        }
        exam.put("questions", sanitizeStudentTakingQuestions(questions));

        Integer durationMinutes = exam.get("durationMinutes") == null ? null : ((Number) exam.get("durationMinutes")).intValue();
        if (durationMinutes != null) {
            Long elapsed = jt.queryForObject("""
                    SELECT TIMESTAMPDIFF(SECOND, start_time, NOW())
                    FROM exam_attempt
                    WHERE id = ?
                    """, Long.class, attemptId);
            long remaining = durationMinutes * 60L - (elapsed == null ? 0L : elapsed);
            if (remaining < -SUBMIT_GRACE_SECONDS) {
                Map<Long, String> draftAnswers = loadDraftAnswerMap(jt, attemptId);
                Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, draftAnswers,
                        "TIMEOUT", "Auto submitted when student re-entered after timeout", false, null));
                result.put("autoSubmitted", true);
                result.put("message", "Exam has timed out and was submitted automatically");
                return result;
            }
            exam.put("remainingSeconds", Math.max(0L, remaining));
        }

        Map<String, Object> bestDraft = loadBestDraftState(jt, attemptId);
        exam.put("draftAnswers", sanitizeRecoveredDraftAnswersJson(jt, attemptId, stringValue(bestDraft.get("answers"))));
        exam.put("draftSource", bestDraft.getOrDefault("source", "DB"));
        Map<String, Object> recovery = attemptRecoveryState(jt, attemptId);
        exam.putAll(recovery);
        return exam;
    }

    private void requireStartRulesConfirmed(int attemptStatus, StartExamRequest request) {
        if (attemptStatus == 0 && (request == null || !Boolean.TRUE.equals(request.getRulesConfirmed()))) {
            throw new IllegalStateException("Exam rules must be confirmed before starting");
        }
    }

    private boolean shouldRecordInProgressRulesConfirmation(Map<String, Object> attempt, StartExamRequest request) {
        int status = attempt.get("status") instanceof Number number ? number.intValue() : -1;
        Object rulesConfirmedAt = attempt.get("rules_confirmed_at");
        return status == 1
                && (rulesConfirmedAt == null || String.valueOf(rulesConfirmedAt).isBlank())
                && request != null
                && Boolean.TRUE.equals(request.getRulesConfirmed());
    }

    private List<Map<String, Object>> sanitizeStudentTakingQuestions(List<Map<String, Object>> questions) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            sanitized.add(sanitizeStudentTakingMap(question));
        }
        return sanitized;
    }

    private Map<String, Object> sanitizeStudentTakingMap(Map<?, ?> raw) {
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (isStudentTakingSensitiveKey(key)) {
                continue;
            }
            sanitized.put(key, sanitizeStudentTakingValue(entry.getValue()));
        }
        return sanitized;
    }

    private Object sanitizeStudentTakingValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return sanitizeStudentTakingMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : list) {
                sanitized.add(sanitizeStudentTakingValue(item));
            }
            return sanitized;
        }
        return value;
    }

    private boolean isStudentTakingSensitiveKey(String key) {
        String normalized = key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return STUDENT_TAKING_SENSITIVE_KEYS.contains(normalized);
    }

    @Transactional
    public Map<String, Object> saveDraft(Long attemptId, String answersJson, String clientDraftId,
                                         Long revision, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        attemptId = requirePositiveAttemptId(attemptId);
        Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, user.getId());
        if (!attemptDraftSaveOpen(attempt)) {
            return Map.of("saved", false, "reason", "Attempt is not active");
        }
        String safeAnswersJson = validateDraftAnswersJson(jt, attemptId, answersJson);
        long safeRevision = normalizeDraftRevision(revision);
        String safeClientDraftId = normalizeClientDraftId(clientDraftId);
        boolean writeBack = configBoolean(jt, "exam.draftRedisWriteBackEnabled", false)
                && examDraftCacheService.available();
        if (writeBack) {
            Map<String, Object> bestDraft = loadBestDraftState(jt, attemptId);
            long serverRevision = longValue(bestDraft.get("revision"), 0L);
            if (serverRevision > safeRevision) {
                Map<String, Object> stale = new HashMap<>();
                stale.put("saved", false);
                stale.put("revision", safeRevision);
                stale.put("serverRevision", serverRevision);
                stale.put("stale", true);
                stale.put("savedAt", bestDraft.get("updatedAt"));
                stale.put("clientDraftId", bestDraft.get("clientDraftId"));
                stale.put("draftSource", bestDraft.getOrDefault("source", "DB"));
                stale.put("cacheEnabled", true);
                stale.put("writeBack", true);
                return stale;
            }
            examDraftCacheService.put(attemptId, safeAnswersJson, safeClientDraftId, safeRevision,
                    LocalDateTime.now(), true);
            jt.update("""
                    UPDATE exam_attempt
                    SET last_draft_saved_at = NOW(), draft_version = ?
                    WHERE id = ? AND user_id = ?
                    """, safeRevision, attemptId, user.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("saved", true);
            result.put("revision", safeRevision);
            result.put("serverRevision", safeRevision);
            result.put("stale", false);
            result.put("savedAt", LocalDateTime.now());
            result.put("clientDraftId", safeClientDraftId);
            result.put("draftSource", "REDIS");
            result.put("cacheEnabled", true);
            result.put("writeBack", true);
            return result;
        }
        jt.update("""
                INSERT INTO exam_answer_draft (attempt_id, answers, client_draft_id, revision, saved_count)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                  answers = IF(VALUES(revision) >= revision, VALUES(answers), answers),
                  client_draft_id = IF(VALUES(revision) >= revision, VALUES(client_draft_id), client_draft_id),
                  saved_count = IF(VALUES(revision) >= revision, saved_count + 1, saved_count),
                  updated_at = IF(VALUES(revision) >= revision, CURRENT_TIMESTAMP, updated_at),
                  revision = IF(VALUES(revision) >= revision, VALUES(revision), revision)
                """, attemptId, safeAnswersJson, safeClientDraftId, safeRevision);
        Map<String, Object> draft = loadDraftState(jt, attemptId);
        long serverRevision = numberValue(draft.get("revision")) == null ? 0L : numberValue(draft.get("revision"));
        boolean saved = serverRevision == safeRevision || serverRevision <= safeRevision;
        if (saved) {
            jt.update("""
                    UPDATE exam_attempt
                    SET last_draft_saved_at = NOW(), draft_version = ?
                    WHERE id = ? AND user_id = ?
                    """, serverRevision, attemptId, user.getId());
            examDraftCacheService.put(attemptId, safeAnswersJson, stringValue(draft.get("clientDraftId")),
                    serverRevision, draft.get("updatedAt"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("saved", saved);
        result.put("revision", safeRevision);
        result.put("serverRevision", serverRevision);
        result.put("stale", serverRevision > safeRevision);
        result.put("savedAt", draft.get("updatedAt"));
        result.put("clientDraftId", draft.get("clientDraftId"));
        result.put("draftSource", examDraftCacheService.available() && saved ? "REDIS" : "DB");
        result.put("cacheEnabled", examDraftCacheService.available());
        result.put("writeBack", false);
        return result;
    }

    private long normalizeDraftRevision(Long revision) {
        if (revision == null) {
            return System.currentTimeMillis();
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be greater than or equal to 0");
        }
        return revision;
    }

    private String normalizeClientDraftId(String clientDraftId) {
        if (clientDraftId == null) {
            return null;
        }
        String value = clientDraftId.trim();
        if (value.length() > 80) {
            throw new IllegalArgumentException("clientDraftId must be at most 80 characters");
        }
        return value;
    }

    private boolean attemptDraftSaveOpen(Map<String, Object> attempt) {
        if (((Number) attempt.get("status")).intValue() != 1 || !attemptExamOpen(attempt)) {
            return false;
        }
        Object examStarted = attempt.get("examStarted");
        if (examStarted instanceof Number number && number.intValue() == 0) {
            return false;
        }
        Object remainingValue = attempt.get("secondsUntilDeadline");
        return remainingValue == null || ((Number) remainingValue).longValue() > 0;
    }

    @Transactional
    public Map<String, Object> submitExam(Long attemptId, Map<Long, String> answers, String submitToken, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        attemptId = requirePositiveAttemptId(attemptId);
        String safeSubmitToken = normalizeSubmitToken(submitToken);
        Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, user.getId());
        List<Map<String, Object>> paperQuestions = loadQuestionsForSubmit(jt, attemptId);
        String requestPayloadHash = answerPayloadHash(answers, paperQuestions);
        if (((Number) attempt.get("status")).intValue() >= 2) {
            return submittedAttemptResult(jt, attempt, true, safeSubmitToken, requestPayloadHash);
        }
        boolean draftFlushed = flushRedisDraftForAttempt(jt, attemptId);
        boolean deadlinePassed = validateSubmitWindow(attempt, false);
        String submitType = deadlinePassed ? "TIMEOUT" : "MANUAL";
        String submitReason = deadlinePassed ? "Submitted within timeout grace window" : null;
        Map<String, Object> result = finalizeAttempt(jt, attempt, answers, submitType, submitReason, true, safeSubmitToken);
        result.put("draftFlushedBeforeSubmit", draftFlushed);
        storeSubmitResponse(jt, attemptId, safeSubmitToken,
                stringValue(result.get("submitPayloadHash")), result);
        return result;
    }

    @Transactional
    public Map<String, Object> flushRedisDrafts(String nodeId) {
        JdbcTemplate jt = requireJdbcTemplate();
        int batchSize = configNumber(jt, "exam.draftRedisFlushBatchSize", 200);
        List<Map<String, Object>> drafts = examDraftCacheService.dirtyDrafts(batchSize);
        int flushed = 0;
        int skipped = 0;
        int cleaned = 0;
        for (Map<String, Object> draft : drafts) {
            Long attemptId = numberValue(draft.get("attemptId"));
            if (attemptId == null || attemptId <= 0) {
                skipped++;
                examDraftCacheService.markFlushSkipped();
                continue;
            }
            List<Map<String, Object>> attempts = jt.queryForList("""
                    SELECT id, status
                    FROM exam_attempt
                    WHERE id = ?
                    """, attemptId);
            if (attempts.isEmpty()) {
                examDraftCacheService.delete(attemptId);
                cleaned++;
                continue;
            }
            int status = ((Number) attempts.get(0).get("status")).intValue();
            if (status >= 2) {
                examDraftCacheService.delete(attemptId);
                cleaned++;
                continue;
            }
            String answers = stringValue(draft.get("answers"));
            String safeAnswers;
            String clientDraftId;
            try {
                safeAnswers = validateDraftAnswersJson(jt, attemptId, answers);
                clientDraftId = normalizeClientDraftId(stringValue(draft.get("clientDraftId")));
            } catch (IllegalArgumentException ex) {
                examDraftCacheService.delete(attemptId);
                examDraftCacheService.markFlushSkipped();
                skipped++;
                continue;
            }
            long revision = longValue(draft.get("revision"), 0L);
            Map<String, Object> dbDraft = loadDraftState(jt, attemptId);
            long dbRevision = longValue(dbDraft.get("revision"), 0L);
            if (revision < dbRevision) {
                examDraftCacheService.markFlushSkipped();
                skipped++;
                continue;
            }
            jt.update("""
                    INSERT INTO exam_answer_draft (attempt_id, answers, client_draft_id, revision, saved_count)
                    VALUES (?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      answers = IF(VALUES(revision) >= revision, VALUES(answers), answers),
                      client_draft_id = IF(VALUES(revision) >= revision, VALUES(client_draft_id), client_draft_id),
                      saved_count = IF(VALUES(revision) >= revision, saved_count + 1, saved_count),
                      updated_at = IF(VALUES(revision) >= revision, CURRENT_TIMESTAMP, updated_at),
                      revision = IF(VALUES(revision) >= revision, VALUES(revision), revision)
                    """, attemptId, safeAnswers, clientDraftId, revision);
            jt.update("""
                    UPDATE exam_attempt
                    SET last_draft_saved_at = NOW(), draft_version = ?
                    WHERE id = ?
                    """, revision, attemptId);
            Map<String, Object> after = loadDraftState(jt, attemptId);
            examDraftCacheService.markClean(attemptId, safeAnswers, clientDraftId, revision, after.get("updatedAt"));
            flushed++;
        }
        examDraftCacheService.recordFlushRun(drafts.size(), flushed, skipped, cleaned);
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("checked", drafts.size());
        result.put("flushed", flushed);
        result.put("skipped", skipped);
        result.put("cleaned", cleaned);
        result.put("cache", examDraftCacheService.stats());
        return result;
    }

    public Map<String, Object> draftCacheStatus(AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can view draft cache status");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> status = new HashMap<>(examDraftCacheService.stats());
        status.put("writeBackEnabled", configBoolean(jt, "exam.draftRedisWriteBackEnabled", false));
        status.put("flushBatchSize", configNumber(jt, "exam.draftRedisFlushBatchSize", 200));
        status.put("activeAttempts", queryInt(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 1"));
        status.put("dbDrafts", queryInt(jt, "SELECT COUNT(*) FROM exam_answer_draft"));
        int dirtyWarningThreshold = configNumber(jt, "exam.draftCacheDirtyWarningThreshold", 100);
        int dirtyHighThreshold = configNumber(jt, "exam.draftCacheDirtyHighThreshold", 500);
        int errorWarningThreshold = configNumber(jt, "exam.draftCacheErrorWarningThreshold", 5);
        int staleFlushWarningSeconds = configNumber(jt, "exam.draftCacheStaleFlushWarningSeconds", 300);
        status.put("dirtyWarningThreshold", dirtyWarningThreshold);
        status.put("dirtyHighThreshold", dirtyHighThreshold);
        status.put("errorWarningThreshold", errorWarningThreshold);
        status.put("staleFlushWarningSeconds", staleFlushWarningSeconds);
        status.putAll(draftCacheAlert(status, dirtyWarningThreshold, dirtyHighThreshold,
                errorWarningThreshold, staleFlushWarningSeconds));
        return status;
    }

    public PageResult<Map<String, Object>> listAttemptResilienceCandidates(String keyword, boolean openOnly,
                                                                           int page, int size, AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can view attempt resilience candidates");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        String whereSql = attemptResilienceCandidateWhere(keyword, openOnly, params);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                """ + whereSql, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT a.id AS attemptId, a.status AS attemptStatus, a.attempt_no AS attemptNo,
                       a.start_time AS attemptStartTime, a.last_draft_saved_at AS lastDraftSavedAt,
                       a.draft_version AS draftVersion, a.submit_token AS submitToken,
                       e.id AS examId, e.exam_name AS examName, e.status AS examStatus,
                       e.start_time AS startTime, e.end_time AS endTime,
                       e.duration_minutes AS durationMinutes, e.max_attempts AS maxAttempts,
                       p.paper_name AS paperName,
                       u.id AS studentUserId, u.username AS studentUsername, u.real_name AS studentName,
                       sp.student_no AS studentNo, c.class_name AS className,
                       CASE
                         WHEN e.deleted = 0
                          AND e.status = ?
                          AND a.status IN (0, 1)
                          AND (e.start_time IS NULL OR e.start_time <= NOW())
                          AND (e.end_time IS NULL OR e.end_time > NOW())
                          AND (
                            e.duration_minutes IS NULL
                            OR a.start_time IS NULL
                            OR DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) > NOW()
                          )
                         THEN 1 ELSE 0 END AS openForVerification,
                       COALESCE(
                         (SELECT COUNT(*) FROM exam_question_snapshot eqs WHERE eqs.exam_id = e.id),
                         0
                       ) AS snapshotQuestionCount,
                       (SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = e.paper_id) AS paperQuestionCount,
                       CONCAT_WS(',',
                         CASE WHEN a.status = 1 THEN 'IN_PROGRESS' END,
                         CASE WHEN a.status = 0 THEN 'NOT_STARTED' END,
                         CASE WHEN e.status <> ? THEN 'EXAM_NOT_PUBLISHED' END,
                         CASE WHEN e.start_time IS NOT NULL AND e.start_time > NOW() THEN 'BEFORE_START' END,
                         CASE WHEN e.end_time IS NOT NULL AND e.end_time <= NOW() THEN 'AFTER_END' END,
                         CASE WHEN a.start_time IS NOT NULL AND e.duration_minutes IS NOT NULL
                                AND DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) <= NOW()
                              THEN 'DURATION_EXPIRED' END
                       ) AS verificationFlags
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0 
                """
                + whereSql +
                """
                ORDER BY openForVerification DESC, a.status ASC, e.start_time ASC, a.id ASC
                LIMIT ? OFFSET ?
                """, withLeadingParams(listParams, EXAM_STATUS_PUBLISHED, EXAM_STATUS_PUBLISHED));
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    private String attemptResilienceCandidateWhere(String keyword, boolean openOnly, List<Object> params) {
        StringBuilder where = new StringBuilder("""
                WHERE e.deleted = 0
                  AND u.deleted = 0
                  AND u.status = 1
                  AND a.status IN (0, 1)
                """);
        if (openOnly) {
            where.append("""
                    AND e.status = ?
                    AND (e.start_time IS NULL OR e.start_time <= NOW())
                    AND (e.end_time IS NULL OR e.end_time > NOW())
                    AND (
                      e.duration_minutes IS NULL
                      OR a.start_time IS NULL
                      OR DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) > NOW()
                    )
                    """);
            params.add(EXAM_STATUS_PUBLISHED);
        }
        String kw = blankToNull(keyword);
        if (kw != null) {
            where.append("""
                    AND (
                      e.exam_name LIKE CONCAT('%', ?, '%')
                      OR p.paper_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%')
                      OR u.real_name LIKE CONCAT('%', ?, '%')
                      OR sp.student_no LIKE CONCAT('%', ?, '%')
                    )
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        return where.toString();
    }

    @Transactional
    public Map<String, Object> prepareAttemptResilienceFixture(Map<String, Object> request, AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can prepare attempt resilience fixtures");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        if (!configBoolean(jt, "system.testFixtureEnabled", false)) {
            throw new IllegalStateException("Test fixture preparation is disabled. Enable system.testFixtureEnabled in a test environment.");
        }

        String suffix = String.valueOf(System.currentTimeMillis());
        String username = firstNonBlank(stringValue(request.get("studentUsername")), "verify_student");
        String password = firstNonBlank(stringValue(request.get("studentPassword")), "student123");
        String realName = firstNonBlank(stringValue(request.get("studentName")), "Resilience Test Student");
        String subjectName = firstNonBlank(stringValue(request.get("subjectName")), "Attempt Resilience Verification");
        String className = firstNonBlank(stringValue(request.get("className")), "Attempt Resilience Test Class");
        String classCode = firstNonBlank(stringValue(request.get("classCode")), "VERIFY-RESILIENCE");
        int durationMinutes = (int) longValue(request.get("durationMinutes"), 120L);

        Long subjectId = ensureFixtureSubject(jt, subjectName);
        Long classId = ensureFixtureClass(jt, className, classCode);
        Long studentId = ensureFixtureStudent(jt, username, password, realName, classId);
        Long questionId = createFixtureQuestion(jt, subjectId, user.getId(), suffix);
        Long questionVersionId = createFixtureQuestionVersion(jt, questionId, user.getId());
        Long paperId = createFixturePaper(jt, subjectId, questionId, questionVersionId, user.getId(), suffix);
        Long examId = createFixtureExam(jt, paperId, studentId, user.getId(), durationMinutes, suffix);
        Long attemptId = jt.queryForObject("""
                SELECT id
                FROM exam_attempt
                WHERE exam_id = ? AND user_id = ? AND attempt_no = 1
                """, Long.class, examId, studentId);

        Map<String, Object> result = new HashMap<>();
        result.put("studentUsername", username);
        result.put("studentPassword", password);
        result.put("studentUserId", studentId);
        result.put("classId", classId);
        result.put("subjectId", subjectId);
        result.put("questionId", questionId);
        result.put("paperId", paperId);
        result.put("examId", examId);
        result.put("attemptId", attemptId);
        result.put("durationMinutes", durationMinutes);
        result.put("fixtureEnabled", true);
        return result;
    }

    @Transactional
    public Map<String, Object> cleanupAttemptResilienceFixtures(int olderThanHours, String studentPrefix,
                                                                boolean dryRun, AuthUser user) {
        if (!user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only administrators can clean attempt resilience fixtures");
        }
        JdbcTemplate jt = requireJdbcTemplate();
        if (!configBoolean(jt, "system.testFixtureEnabled", false)) {
            throw new IllegalStateException("Test fixture cleanup is disabled. Enable system.testFixtureEnabled in a test environment.");
        }

        int safeOlderThanHours = Math.max(0, olderThanHours);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(safeOlderThanHours);
        String safeStudentPrefix = firstNonBlank(studentPrefix, "verify_student");

        List<Long> examIds = jt.queryForList("""
                SELECT e.id
                FROM exam e
                WHERE e.exam_name LIKE 'Attempt Resilience Fixture Exam %'
                  AND e.description = 'Fixture exam for attempt resilience acceptance tests'
                  AND e.created_at <= ?
                  AND EXISTS (
                      SELECT 1
                      FROM exam_candidate_snapshot ecs
                      JOIN sys_user u ON u.id = ecs.user_id
                      WHERE ecs.exam_id = e.id
                        AND u.username LIKE ?
                  )
                """, Long.class, cutoff, safeStudentPrefix + "%");
        List<Long> paperIds = queryIdsForParents(jt, """
                SELECT DISTINCT e.paper_id
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                WHERE e.id IN (%s)
                  AND p.paper_name LIKE 'Attempt Resilience Fixture Paper %'
                  AND p.description = 'Fixture paper for attempt resilience acceptance tests'
                """, examIds);
        List<Long> questionIds = queryIdsForParents(jt, """
                SELECT DISTINCT q.id
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                WHERE pq.paper_id IN (%s)
                  AND q.source_detail = 'attempt-resilience-fixture'
                """, paperIds);
        List<Long> studentIds = jt.queryForList("""
                SELECT u.id
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id AND r.role_code = 'STUDENT'
                WHERE u.username LIKE ?
                  AND u.created_at <= ?
                """, Long.class, safeStudentPrefix + "%", cutoff);
        List<Long> attemptIds = queryIdsForParents(jt, """
                SELECT id
                FROM exam_attempt
                WHERE exam_id IN (%s)
                """, examIds);
        List<Long> sessionIds = queryIdsForParents(jt, """
                SELECT id
                FROM exam_monitor_session
                WHERE attempt_id IN (%s)
                """, attemptIds);
        List<Long> questionVersionIds = queryIdsForParents(jt, """
                SELECT id
                FROM question_version
                WHERE question_id IN (%s)
                """, questionIds);

        Map<String, Object> result = new HashMap<>();
        result.put("dryRun", dryRun);
        result.put("olderThanHours", safeOlderThanHours);
        result.put("cutoff", cutoff);
        result.put("studentPrefix", safeStudentPrefix);
        result.put("examIds", examIds);
        result.put("paperIds", paperIds);
        result.put("questionIds", questionIds);
        result.put("studentIds", studentIds);
        result.put("attemptIds", attemptIds);
        result.put("sessionIds", sessionIds);
        result.put("questionVersionIds", questionVersionIds);
        result.put("examCount", examIds.size());
        result.put("paperCount", paperIds.size());
        result.put("questionCount", questionIds.size());
        result.put("studentCount", studentIds.size());
        result.put("attemptCount", attemptIds.size());
        result.put("monitorSessionCount", sessionIds.size());
        result.put("questionVersionCount", questionVersionIds.size());

        if (dryRun) {
            result.put("cleaned", false);
            return result;
        }

        Map<String, Integer> affected = new HashMap<>();
        affected.put("examMonitorActions", updateByIds(jt,
                "DELETE FROM exam_monitor_action WHERE session_id IN (%s)", sessionIds));
        affected.put("cheatEvents", updateByIds(jt,
                "DELETE FROM cheat_event WHERE attempt_id IN (%s)", attemptIds));
        affected.put("examMonitorSessions", updateByIds(jt,
                "DELETE FROM exam_monitor_session WHERE id IN (%s)", sessionIds));
        affected.put("scoreAppealLogs", updateByIds(jt,
                "DELETE FROM score_appeal_log WHERE attempt_id IN (%s)", attemptIds));
        affected.put("scoreAppeals", updateByIds(jt,
                "DELETE FROM score_appeal WHERE attempt_id IN (%s)", attemptIds));
        affected.put("scoreReleaseLogs", updateByIds(jt,
                "DELETE FROM score_release_log WHERE exam_id IN (%s)", examIds));
        affected.put("scoreReleases", updateByIds(jt,
                "DELETE FROM score_release WHERE exam_id IN (%s)", examIds));
        affected.put("reviewScoreLogs", updateByIds(jt,
                "DELETE FROM review_score_log WHERE attempt_id IN (%s)", attemptIds));
        affected.put("reviewRecords", updateByIds(jt,
                "DELETE rr FROM review_record rr JOIN answer_record ar ON ar.id = rr.answer_record_id WHERE ar.attempt_id IN (%s)", attemptIds));
        affected.put("wrongQuestions", updateByIds(jt,
                "DELETE FROM wrong_question_book WHERE exam_id IN (%s)", examIds));
        affected.put("answerRecords", updateByIds(jt,
                "DELETE FROM answer_record WHERE attempt_id IN (%s)", attemptIds));
        affected.put("answerDrafts", updateByIds(jt,
                "DELETE FROM exam_answer_draft WHERE attempt_id IN (%s)", attemptIds));
        affected.put("submitResponses", updateByIds(jt,
                "DELETE FROM exam_submit_response WHERE attempt_id IN (%s)", attemptIds));
        affected.put("attempts", updateByIds(jt,
                "DELETE FROM exam_attempt WHERE id IN (%s)", attemptIds));
        affected.put("examTargets", updateByIds(jt,
                "DELETE FROM exam_target WHERE exam_id IN (%s)", examIds));
        affected.put("candidateSnapshots", updateByIds(jt,
                "DELETE FROM exam_candidate_snapshot WHERE exam_id IN (%s)", examIds));
        affected.put("questionOptionSnapshots", updateByIds(jt,
                "DELETE FROM exam_question_option_snapshot WHERE exam_id IN (%s)", examIds));
        affected.put("questionSnapshots", updateByIds(jt,
                "DELETE FROM exam_question_snapshot WHERE exam_id IN (%s)", examIds));
        affected.put("approvalLogs", updateByIds(jt,
                "DELETE FROM exam_approval_log WHERE exam_id IN (%s)", examIds));
        affected.put("exams", updateByIds(jt,
                "UPDATE exam SET deleted = 1, status = ? WHERE id IN (%s)", examIds, EXAM_STATUS_CLOSED));
        affected.put("paperQuestions", updateByIds(jt,
                "DELETE FROM paper_question WHERE paper_id IN (%s)", paperIds));
        affected.put("papers", updateByIds(jt,
                "UPDATE paper SET deleted = 1 WHERE id IN (%s)", paperIds));
        affected.put("questionVersionOptions", updateByIds(jt,
                "DELETE FROM question_version_option WHERE question_version_id IN (%s)", questionVersionIds));
        affected.put("questionVersions", updateByIds(jt,
                "DELETE FROM question_version WHERE id IN (%s)", questionVersionIds));
        affected.put("questionOptions", updateByIds(jt,
                "DELETE FROM question_option WHERE question_id IN (%s)", questionIds));
        affected.put("questions", updateByIds(jt,
                "UPDATE question SET deleted = 1, status = 0 WHERE id IN (%s)", questionIds));
        affected.put("studentProfiles", updateByIds(jt,
                "UPDATE student_profile SET deleted = 1, status = 0 WHERE user_id IN (%s)", studentIds));
        affected.put("studentClassMemberships", updateByIds(jt,
                "UPDATE student_class_membership SET deleted = 1, status = 0, left_at = COALESCE(left_at, NOW()) WHERE student_user_id IN (%s)", studentIds));
        affected.put("studentCourseEnrollments", updateByIds(jt,
                "UPDATE student_course_enrollment SET deleted = 1, status = 0, dropped_at = COALESCE(dropped_at, NOW()) WHERE student_user_id IN (%s)", studentIds));
        affected.put("userRoles", updateByIds(jt,
                "DELETE FROM sys_user_role WHERE user_id IN (%s)", studentIds));
        affected.put("users", updateByIds(jt,
                "UPDATE sys_user SET deleted = 1, status = 0 WHERE id IN (%s)", studentIds));

        result.put("cleaned", true);
        result.put("affected", affected);
        return result;
    }

    private Long ensureFixtureSubject(JdbcTemplate jt, String subjectName) {
        jt.update("""
                INSERT IGNORE INTO edu_subject (subject_name, description, status, deleted)
                VALUES (?, 'Created for attempt resilience acceptance tests', 1, 0)
                """, subjectName);
        return jt.queryForObject("""
                SELECT id
                FROM edu_subject
                WHERE subject_name = ? AND deleted = 0
                LIMIT 1
                """, Long.class, subjectName);
    }

    private Long ensureFixtureClass(JdbcTemplate jt, String className, String classCode) {
        jt.update("""
                INSERT INTO edu_class (class_name, class_code, class_type, major, grade, status, deleted)
                VALUES (?, ?, 'TEMPORARY', 'Verification', 'TEST', 1, 0)
                ON DUPLICATE KEY UPDATE status = 1, deleted = 0, class_type = 'TEMPORARY'
                """, className, classCode);
        return jt.queryForObject("""
                SELECT id
                FROM edu_class
                WHERE class_code = ? AND deleted = 0
                LIMIT 1
                """, Long.class, classCode);
    }

    private Long ensureFixtureStudent(JdbcTemplate jt, String username, String password, String realName, Long classId) {
        Long roleId = jt.queryForObject("""
                SELECT id
                FROM sys_role
                WHERE role_code = 'STUDENT' AND status = 1 AND deleted = 0
                LIMIT 1
                """, Long.class);
        jt.update("""
                INSERT INTO sys_user (username, password_hash, real_name, status, deleted)
                VALUES (?, ?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash),
                    real_name = VALUES(real_name), status = 1, deleted = 0
                """, username, PasswordHashUtil.encode(password), realName);
        Long studentId = jt.queryForObject("""
                SELECT id
                FROM sys_user
                WHERE username = ? AND deleted = 0
                LIMIT 1
                """, Long.class, username);
        jt.update("""
                INSERT IGNORE INTO sys_user_role (user_id, role_id)
                VALUES (?, ?)
                """, studentId, roleId);
        jt.update("""
                INSERT INTO student_profile (user_id, student_no, class_id, primary_class_id, status, deleted)
                VALUES (?, ?, ?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE student_no = VALUES(student_no),
                    class_id = VALUES(class_id), primary_class_id = VALUES(primary_class_id),
                    status = 1, deleted = 0
                """, studentId, username, classId, classId);
        return studentId;
    }

    private Long createFixtureQuestion(JdbcTemplate jt, Long subjectId, Long adminId, String suffix) {
        jt.update("""
                INSERT INTO question
                  (subject_id, question_type, difficulty, stem, correct_answer, analysis, default_score,
                   status, review_status, reviewed_by, reviewed_at, created_by, source_type, source_detail)
                VALUES (?, 'SINGLE_CHOICE', 'EASY', ?, 'A', 'Fixture objective question', 5.00,
                        1, 'APPROVED', ?, NOW(), ?, 'MANUAL', 'attempt-resilience-fixture')
                """, subjectId, "Fixture question " + suffix + ": choose option A.", adminId, adminId);
        Long questionId = lastInsertId(jt);
        jt.update("""
                INSERT INTO question_option (question_id, option_label, option_content, is_correct, sort_order)
                VALUES (?, 'A', 'Correct fixture option', 1, 1),
                       (?, 'B', 'Distractor fixture option', 0, 2)
                """, questionId, questionId);
        return questionId;
    }

    private Long createFixtureQuestionVersion(JdbcTemplate jt, Long questionId, Long adminId) {
        jt.update("""
                INSERT INTO question_version
                  (question_id, version_no, subject_id, question_type, difficulty, stem, correct_answer,
                   analysis, default_score, status, review_status, source_type, source_detail, snapshot_reason, snapshot_by)
                SELECT id, version_no, subject_id, question_type, difficulty, stem, correct_answer,
                       analysis, default_score, status, review_status, source_type, source_detail, 'FIXTURE', ?
                FROM question
                WHERE id = ?
                """, adminId, questionId);
        Long versionId = lastInsertId(jt);
        jt.update("""
                INSERT INTO question_version_option
                    (question_version_id, question_id, option_label, option_content, is_correct, sort_order)
                SELECT ?, question_id, option_label, option_content, is_correct, sort_order
                FROM question_option
                WHERE question_id = ?
                """, versionId, questionId);
        return versionId;
    }

    private Long createFixturePaper(JdbcTemplate jt, Long subjectId, Long questionId, Long questionVersionId,
                                    Long adminId, String suffix) {
        jt.update("""
                INSERT INTO paper (subject_id, paper_name, description, total_score, status, created_by)
                VALUES (?, ?, 'Fixture paper for attempt resilience acceptance tests', 5.00, 1, ?)
                """, subjectId, "Attempt Resilience Fixture Paper " + suffix, adminId);
        Long paperId = lastInsertId(jt);
        jt.update("""
                INSERT INTO paper_question (paper_id, question_id, question_version_id, score, sort_order)
                VALUES (?, ?, ?, 5.00, 1)
                """, paperId, questionId, questionVersionId);
        return paperId;
    }

    private Long createFixtureExam(JdbcTemplate jt, Long paperId, Long studentId, Long adminId,
                                  int durationMinutes, String suffix) {
        int safeDuration = Math.max(1, durationMinutes);
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(Math.max(30, safeDuration));
        jt.update("""
                INSERT INTO exam
                  (paper_id, exam_name, description, start_time, end_time, duration_minutes,
                   max_attempts, pass_score, status, created_by)
                VALUES (?, ?, 'Fixture exam for attempt resilience acceptance tests',
                        ?, ?, ?, 1, 3.00, ?, ?)
                """, paperId, "Attempt Resilience Fixture Exam " + suffix,
                startTime, endTime, safeDuration, EXAM_STATUS_PUBLISHED, adminId);
        Long examId = lastInsertId(jt);
        jt.update("""
                INSERT INTO exam_target (exam_id, target_type, target_id, target_code)
                VALUES (?, 'USER', ?, '')
                """, examId, studentId);
        createPaperSnapshot(jt, examId, paperId);
        insertCandidateSnapshotIfMissing(jt, examId, studentId, "USER", studentId);
        insertAttemptIfMissing(jt, examId, studentId);
        recordApprovalLog(jt, examId, APPROVAL_ACTION_DIRECT_PUBLISH,
                EXAM_STATUS_PENDING_APPROVAL, EXAM_STATUS_PUBLISHED, "Fixture auto-published", adminId);
        return examId;
    }

    private Map<String, Object> draftCacheAlert(Map<String, Object> status, int dirtyWarningThreshold,
                                                int dirtyHighThreshold, int errorWarningThreshold,
                                                int staleFlushWarningSeconds) {
        boolean enabled = Boolean.TRUE.equals(status.get("enabled"));
        boolean available = Boolean.TRUE.equals(status.get("available"));
        boolean writeBackEnabled = Boolean.TRUE.equals(status.get("writeBackEnabled"));
        long dirtyCount = longValue(status.get("dirtyCount"), 0L);
        long errors = longValue(status.get("errors"), 0L);
        long lastFlushAt = longValue(status.get("lastFlushAtEpochMillis"), 0L);
        Map<String, Object> alert = new HashMap<>();
        if (!enabled) {
            alert.put("alertLevel", "DISABLED");
            alert.put("alertMessage", "Redis draft cache is disabled; drafts are written to MySQL directly");
            return alert;
        }
        if (!available && writeBackEnabled) {
            alert.put("alertLevel", "HIGH");
            alert.put("alertMessage", "Redis write-back is enabled but Redis is unavailable");
            return alert;
        }
        if (!available) {
            alert.put("alertLevel", "WARN");
            alert.put("alertMessage", "Redis draft cache is unavailable; MySQL remains the source of truth");
            return alert;
        }
        if (dirtyCount >= Math.max(dirtyWarningThreshold, dirtyHighThreshold)) {
            alert.put("alertLevel", "HIGH");
            alert.put("alertMessage", "Dirty Redis drafts are above the high-risk threshold");
            return alert;
        }
        if (dirtyCount >= dirtyWarningThreshold) {
            alert.put("alertLevel", "WARN");
            alert.put("alertMessage", "Dirty Redis drafts are above the warning threshold");
            return alert;
        }
        if (errors >= errorWarningThreshold) {
            alert.put("alertLevel", "WARN");
            alert.put("alertMessage", "Redis draft cache has accumulated errors");
            return alert;
        }
        if (writeBackEnabled && dirtyCount > 0 && lastFlushAt <= 0) {
            alert.put("alertLevel", "WARN");
            alert.put("alertMessage", "Write-back mode has dirty drafts but no successful flush has been recorded");
            return alert;
        }
        if (writeBackEnabled && dirtyCount > 0 && staleFlushWarningSeconds > 0) {
            long elapsedSeconds = (System.currentTimeMillis() - lastFlushAt) / 1000L;
            if (elapsedSeconds > staleFlushWarningSeconds) {
                alert.put("alertLevel", "WARN");
                alert.put("alertMessage", "Redis draft flush has not run recently while dirty drafts exist");
                return alert;
            }
        }
        alert.put("alertLevel", "OK");
        alert.put("alertMessage", writeBackEnabled
                ? "Redis write-back draft cache is healthy"
                : "Redis draft cache is healthy in write-through mode");
        return alert;
    }

    @Transactional
    public Map<String, Object> attemptHeartbeat(Long attemptId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        attemptId = requirePositiveAttemptId(attemptId);
        Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, user.getId());
        int status = ((Number) attempt.get("status")).intValue();
        if (status >= 2) {
            Map<String, Object> result = new HashMap<>(submittedAttemptResult(jt, attempt, true));
            result.put("remainingSeconds", 0);
            result.put("submitted", true);
            return result;
        }
        if (status != 1) {
            throw new IllegalStateException("Exam is not in progress");
        }
        if (!attemptExamOpen(attempt)) {
            Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                    "FORCED", "Exam is no longer open for taking", false, null));
            result.put("autoSubmitted", true);
            result.put("forcedSubmitted", true);
            result.put("remainingSeconds", 0);
            result.put("message", "Exam is no longer open and was submitted automatically");
            return result;
        }
        touchMonitorSession(jt, attemptId,
                ((Number) attempt.get("exam_id")).longValue(),
                ((Number) attempt.get("user_id")).longValue(),
                "ONLINE");
        jt.update("UPDATE exam_attempt SET last_heartbeat_at = NOW() WHERE id = ?", attemptId);
        Object remainingValue = attempt.get("secondsUntilDeadline");
        if (remainingValue == null) {
            Map<String, Object> result = new HashMap<>(attemptRecoveryState(jt, attemptId));
            result.put("status", status);
            result.put("remainingSeconds", -1);
            result.put("serverTime", System.currentTimeMillis());
            return result;
        }
        long remaining = ((Number) remainingValue).longValue();
        if (remaining < -SUBMIT_GRACE_SECONDS) {
            Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                    "TIMEOUT", "Auto submitted by server heartbeat after timeout", false, null));
            result.put("autoSubmitted", true);
            result.put("remainingSeconds", 0);
            return result;
        }
        Map<String, Object> result = new HashMap<>(attemptRecoveryState(jt, attemptId));
        result.put("status", status);
        result.put("remainingSeconds", Math.max(0L, remaining));
        result.put("serverTime", System.currentTimeMillis());
        return result;
    }

    private Map<String, Object> finalizeAttemptOnStartIfClosedOrExpired(JdbcTemplate jt, Map<String, Object> attempt) {
        int status = ((Number) attempt.get("status")).intValue();
        if (status != 1) {
            return Map.of();
        }
        Long attemptId = ((Number) attempt.get("id")).longValue();
        if (!attemptExamOpen(attempt)) {
            Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                    "FORCED", "Exam is no longer open when student re-entered", false, null));
            result.put("autoSubmitted", true);
            result.put("forcedSubmitted", true);
            result.put("remainingSeconds", 0);
            result.put("message", "Exam is no longer open and was submitted automatically");
            return result;
        }
        Object remainingValue = attempt.get("secondsUntilDeadline");
        if (remainingValue == null) {
            return Map.of();
        }
        long remaining = ((Number) remainingValue).longValue();
        if (remaining <= 0) {
            Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                    "TIMEOUT", "Auto submitted when student re-entered after deadline", false, null));
            result.put("autoSubmitted", true);
            result.put("remainingSeconds", 0);
            result.put("message", "Exam has timed out and was submitted automatically");
            return result;
        }
        return Map.of();
    }

    @Transactional
    public Map<String, Object> forceSubmitAttempt(Long attemptId, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        attemptId = requirePositiveAttemptId(attemptId);
        Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, null);
        Long examId = ((Number) attempt.get("exam_id")).longValue();
        requireOwnedExam(examId, user);
        Map<Long, String> draftAnswers = loadDraftAnswerMap(jt, attemptId);
        Map<String, Object> result = new HashMap<>(finalizeAttempt(jt, attempt, draftAnswers,
                "FORCED", "Forced by teacher or administrator", false, null));
        appendForcedSubmitFlags(result);
        return result;
    }

    private void appendForcedSubmitFlags(Map<String, Object> result) {
        String submitType = result.get("submitType") == null ? "" : String.valueOf(result.get("submitType"));
        if ("FORCED".equalsIgnoreCase(submitType)) {
            result.put("forcedSubmitted", true);
            result.put("submitted", true);
        }
    }

    private Map<String, Object> finalizeAttempt(JdbcTemplate jt, Map<String, Object> attempt,
                                                Map<Long, String> answers, String submitType,
                                                String submitReason, boolean createNextAttempt, String submitToken) {
        Long attemptId = ((Number) attempt.get("id")).longValue();
        Long userId = ((Number) attempt.get("user_id")).longValue();
        if (((Number) attempt.get("status")).intValue() != 1) {
            if (((Number) attempt.get("status")).intValue() >= 2) {
                return submittedAttemptResult(jt, attempt, true, submitToken);
            }
            throw new IllegalStateException("Exam is not in progress");
        }
        String normalizedSubmitToken = normalizeSubmitToken(submitToken);
        List<Map<String, Object>> paperQuestions = loadQuestionsForSubmit(jt, attemptId);
        SubmissionAnswerStats answerStats = validateSubmissionAnswers(answers, paperQuestions);
        String payloadHash = answerPayloadHash(answers, paperQuestions);

        int updated = jt.update("""
                UPDATE exam_attempt
                SET status = 2, submit_time = NOW(), submit_type = ?, submit_reason = ?,
                    submit_token = COALESCE(?, submit_token), submit_payload_hash = ?
                WHERE id = ? AND status = 1
                """, submitType, submitReason, normalizedSubmitToken, payloadHash, attemptId);
        if (updated == 0) {
            Map<String, Object> current = loadAttemptForSubmit(jt, attemptId, userId);
            if (((Number) current.get("status")).intValue() >= 2) {
                return submittedAttemptResult(jt, current, true, submitToken, payloadHash);
            }
            throw new IllegalStateException("Exam is not in progress");
        }
        Long examId = ((Number) attempt.get("exam_id")).longValue();
        markMonitorSessionSubmitted(jt, attemptId, examId, ((Number) attempt.get("user_id")).longValue());
        BigDecimal totalScore = BigDecimal.ZERO;
        boolean hasPendingManualReview = false;

        for (Map<String, Object> question : paperQuestions) {
            Long questionId = ((Number) question.get("questionId")).longValue();
            String answer = answers == null ? null : answers.get(questionId);
            boolean answered = !isBlankAnswer(answer);
            String questionType = (String) question.get("question_type");
            if (questionType == null) {
                questionType = (String) question.get("questionType");
            }
            String correctAnswer = (String) question.get("correctAnswer");

            boolean isCorrect = false;
            BigDecimal score = BigDecimal.ZERO;
            int reviewStatus = 0;
            if (List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(questionType)) {
                isCorrect = answer != null && correctAnswer != null
                        && normalizeObjectiveAnswer(answer).equals(normalizeObjectiveAnswer(correctAnswer));
                if (isCorrect) {
                    score = (BigDecimal) question.get("score");
                }
                reviewStatus = 1;
            } else if ("FILL_BLANK".equals(questionType)) {
                isCorrect = !isBlankAnswer(answer) && correctAnswer != null
                        && normalizeFillBlank(answer).equalsIgnoreCase(normalizeFillBlank(correctAnswer));
                if (isCorrect) {
                    score = (BigDecimal) question.get("score");
                }
                reviewStatus = 1;
            } else if (answered) {
                hasPendingManualReview = true;
            } else {
                reviewStatus = 1;
            }

            jt.update("""
                    INSERT INTO answer_record (attempt_id, question_id, answer_content, score, is_correct, review_status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      answer_content = VALUES(answer_content),
                      score = VALUES(score),
                      is_correct = VALUES(is_correct),
                      review_status = VALUES(review_status)
                    """, attemptId, questionId, answer, score, isCorrect, reviewStatus);
            if (reviewStatus == 1 && !isCorrect) {
                jt.update("""
                        INSERT INTO wrong_question_book (user_id, exam_id, question_id, wrong_count, last_wrong_time)
                        VALUES (?, ?, ?, 1, NOW())
                        ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW()
                        """, userId, examId, questionId);
            }
            totalScore = totalScore.add(score);
        }

        int finalStatus = hasPendingManualReview ? 4 : 5;
        jt.update("UPDATE exam_attempt SET score = ?, status = ? WHERE id = ?", totalScore, finalStatus, attemptId);
        jt.update("DELETE FROM exam_answer_draft WHERE attempt_id = ?", attemptId);
        examDraftCacheService.delete(attemptId);
        if (createNextAttempt) {
            createNextAttemptIfAllowed(jt, attempt);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Submitted");
        result.put("status", finalStatus);
        result.put("submitType", submitType);
        result.put("scoreVisible", false);
        result.put("scoreVisibility", "PENDING_RELEASE");
        result.put("submitToken", normalizedSubmitToken);
        result.put("submitPayloadHash", payloadHash);
        Map<String, Object> submitMeta = jt.queryForMap("""
                SELECT submit_time AS submitTime
                FROM exam_attempt
                WHERE id = ?
                """, attemptId);
        result.put("submitTime", submitMeta.get("submitTime"));
        result.put("questionCount", answerStats.questionCount);
        result.put("answeredCount", answerStats.answeredCount);
        result.put("unansweredCount", answerStats.unansweredCount);
        result.put("alreadySubmitted", false);
        appendForcedSubmitFlags(result);
        storeSubmitResponse(jt, attemptId, normalizedSubmitToken, payloadHash, result);
        return result;
    }

    private SubmissionAnswerStats validateSubmissionAnswers(Map<Long, String> answers,
                                                            List<Map<String, Object>> paperQuestions) {
        Set<Long> expectedQuestionIds = new LinkedHashSet<>();
        for (Map<String, Object> question : paperQuestions) {
            expectedQuestionIds.add(((Number) question.get("questionId")).longValue());
        }
        if (answers != null) {
            if (answers.size() > MAX_SUBMITTED_ANSWER_COUNT) {
                throw new IllegalArgumentException("Submitted answers cannot contain more than 1000 entries");
            }
            for (Long questionId : answers.keySet()) {
                requirePositiveAnswerQuestionId(questionId, "Submitted answers contain a non-positive question id");
                requireBoundedAnswerContent(answers.get(questionId));
                if (!expectedQuestionIds.contains(questionId)) {
                    throw new IllegalArgumentException("Submitted answers contain a question that does not belong to this attempt");
                }
            }
        }
        int answeredCount = 0;
        for (Long questionId : expectedQuestionIds) {
            if (answers != null && !isBlankAnswer(answers.get(questionId))) {
                answeredCount++;
            }
        }
        return new SubmissionAnswerStats(expectedQuestionIds.size(), answeredCount,
                expectedQuestionIds.size() - answeredCount);
    }

    private void requireBoundedAnswerContent(String answer) {
        if (answer != null && answer.length() > MAX_SUBMITTED_ANSWER_LENGTH) {
            throw new IllegalArgumentException("Submitted answer content must be at most 20000 characters");
        }
    }

    private String validateDraftAnswersJson(JdbcTemplate jt, Long attemptId, String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return "{}";
        }
        Map<String, Object> parsed;
        try {
            parsed = OBJECT_MAPPER.readValue(answersJson, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Draft answers must be a valid JSON object");
        }
        Set<Long> expectedQuestionIds = expectedQuestionIdsForAttempt(jt, attemptId);
        Map<String, Object> sanitized = new TreeMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            Long questionId;
            try {
                questionId = Long.valueOf(entry.getKey());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Draft answers contain an invalid question id");
            }
            requirePositiveAnswerQuestionId(questionId, "Draft answers contain a non-positive question id");
            if (!expectedQuestionIds.contains(questionId)) {
                throw new IllegalArgumentException("Draft answers contain a question that does not belong to this attempt");
            }
            validateDraftAnswerValue(entry.getValue());
            sanitized.put(String.valueOf(questionId), entry.getValue());
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(sanitized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Draft answers must be a valid JSON object");
        }
    }

    private void validateDraftAnswerValue(Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list) {
            StringBuilder normalized = new StringBuilder();
            for (Object item : list) {
                if (!isDraftAnswerScalar(item)) {
                    throw new IllegalArgumentException("Draft answer values must be strings, numbers, booleans, or arrays of those values");
                }
                normalized.append(String.valueOf(item));
            }
            requireBoundedDraftAnswerContent(normalized.toString());
            return;
        }
        if (!isDraftAnswerScalar(value)) {
            throw new IllegalArgumentException("Draft answer values must be strings, numbers, booleans, or arrays of those values");
        }
        requireBoundedDraftAnswerContent(String.valueOf(value));
    }

    private boolean isDraftAnswerScalar(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean;
    }

    private void requireBoundedDraftAnswerContent(String answer) {
        if (answer != null && answer.length() > MAX_SUBMITTED_ANSWER_LENGTH) {
            throw new IllegalArgumentException("Draft answer content must be at most 20000 characters");
        }
    }

    private Long requirePositiveAnswerQuestionId(Long questionId, String message) {
        if (questionId == null || questionId <= 0) {
            throw new IllegalArgumentException(message);
        }
        return questionId;
    }

    private String sanitizeRecoveredDraftAnswersJson(JdbcTemplate jt, Long attemptId, String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return answersJson;
        }
        Map<String, Object> parsed;
        try {
            parsed = OBJECT_MAPPER.readValue(answersJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return "{}";
        }
        Set<Long> expectedQuestionIds = expectedQuestionIdsForAttempt(jt, attemptId);
        Map<String, Object> sanitized = new TreeMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            Long questionId;
            try {
                questionId = Long.valueOf(entry.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (expectedQuestionIds.contains(questionId) && isRecoverableDraftAnswerValue(entry.getValue())) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(sanitized);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean isRecoverableDraftAnswerValue(Object value) {
        try {
            validateDraftAnswerValue(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Set<Long> expectedQuestionIdsForAttempt(JdbcTemplate jt, Long attemptId) {
        Set<Long> expectedQuestionIds = new LinkedHashSet<>();
        for (Map<String, Object> question : loadQuestionsForSubmit(jt, attemptId)) {
            expectedQuestionIds.add(((Number) question.get("questionId")).longValue());
        }
        return expectedQuestionIds;
    }

    @Transactional
    public Map<String, Object> updateExam(Long id, ExamUpdateRequest request, AuthUser user) {
        id = requirePositiveExamId(id);
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        long windowSeconds = Duration.between(request.getStartTime(), request.getEndTime()).getSeconds();
        if (request.getDurationMinutes() != null && request.getDurationMinutes() * 60L > windowSeconds) {
            throw new IllegalArgumentException("Duration cannot be longer than the exam time window");
        }
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        int previousStatus = currentExamStatusForUpdate(jt, id);
        requireExamEditable(jt, id);
        validateExamUpdateScore(jt, id, request.getPassScore());
        jt.update("""
                UPDATE exam
                SET exam_name = ?, description = ?, start_time = ?, end_time = ?, duration_minutes = ?,
                    max_attempts = ?, pass_score = ?,
                    status = CASE WHEN status = ? THEN ? ELSE status END
                WHERE id = ? AND deleted = 0
                """, trim(request.getExamName()), trim(request.getDescription()), request.getStartTime(),
                request.getEndTime(), request.getDurationMinutes(), request.getMaxAttempts(), request.getPassScore(),
                EXAM_STATUS_REJECTED, EXAM_STATUS_PENDING_APPROVAL, id);
        if (previousStatus == EXAM_STATUS_REJECTED) {
            recordApprovalLog(jt, id, APPROVAL_ACTION_RESUBMIT,
                    EXAM_STATUS_REJECTED, EXAM_STATUS_PENDING_APPROVAL, null, user.getId());
        }
        return getExamById(id);
    }

    @Transactional
    public void deleteExam(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        currentExamStatusForUpdate(jt, id);
        requireExamDeletable(jt, id);
        jt.update("UPDATE exam SET deleted = 1 WHERE id = ? AND deleted = 0", id);
        jt.update("DELETE FROM exam_attempt WHERE exam_id = ? AND status = 0", id);
        jt.update("DELETE FROM exam_target WHERE exam_id = ?", id);
    }

    @Transactional
    public void closeExam(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        int status = currentExamStatusForUpdate(jt, id);
        if (status == EXAM_STATUS_CLOSED) {
            return;
        }
        if (status != EXAM_STATUS_PUBLISHED) {
            throw new IllegalStateException("Only published exams can be closed");
        }
        jt.update("""
                UPDATE exam
                SET status = ?, end_time = CASE WHEN end_time IS NULL OR end_time > NOW() THEN NOW() ELSE end_time END
                WHERE id = ? AND deleted = 0
                """, EXAM_STATUS_CLOSED, id);
        List<Long> activeAttemptIds = jt.queryForList("""
                SELECT id
                FROM exam_attempt
                WHERE exam_id = ? AND status = 1
                """, Long.class, id);
        for (Long attemptId : activeAttemptIds) {
            Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, null);
            finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                    "FORCED", "Exam closed by teacher or administrator", false, null);
        }
    }

    public Map<String, Object> scoreReleaseReadiness(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        return buildScoreReleaseReadiness(requireJdbcTemplate(), id);
    }

    @Transactional
    public Map<String, Object> recalculateMissingScores(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        lockExamForScoreReleaseTransition(jt, id);
        Integer releaseStatus = currentScoreReleaseStatus(jt, id);
        if (releaseStatus != null && releaseStatus == 1) {
            throw new IllegalStateException("Published scores cannot be recalculated. Revoke scores first if a correction is required.");
        }

        int beforeMissing = countMissingCompletedScores(jt, id);
        int skippedPendingReview = countMissingCompletedScoresWithPendingReview(jt, id);
        int skippedNoAnswers = countMissingCompletedScoresWithoutAnswers(jt, id);
        List<Long> eligibleAttemptIds = jt.queryForList("""
                SELECT a.id
                FROM exam_attempt a
                WHERE a.exam_id = ?
                  AND a.status = 5
                  AND a.score IS NULL
                  AND EXISTS (SELECT 1 FROM answer_record ar WHERE ar.attempt_id = a.id)
                  AND NOT EXISTS (
                    SELECT 1 FROM answer_record ar
                    WHERE ar.attempt_id = a.id AND ar.review_status = 0
                  )
                ORDER BY a.id
                """, Long.class, id);

        int recalculated = 0;
        BigDecimal totalRecalculatedScore = BigDecimal.ZERO;
        for (Long attemptId : eligibleAttemptIds) {
            BigDecimal totalScore = sumAttemptScore(jt, attemptId);
            int updated = jt.update("""
                    UPDATE exam_attempt
                    SET score = ?
                    WHERE id = ? AND exam_id = ? AND status = 5 AND score IS NULL
                    """, totalScore, attemptId, id);
            if (updated > 0) {
                recalculated++;
                totalRecalculatedScore = totalRecalculatedScore.add(totalScore == null ? BigDecimal.ZERO : totalScore);
            }
        }

        int afterMissing = countMissingCompletedScores(jt, id);
        Map<String, Object> readiness = buildScoreReleaseReadiness(jt, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", id);
        result.put("scoreReleaseStatus", releaseStatus == null ? 0 : releaseStatus);
        result.put("missingBefore", beforeMissing);
        result.put("eligibleAttempts", eligibleAttemptIds.size());
        result.put("recalculatedAttempts", recalculated);
        result.put("missingAfter", afterMissing);
        result.put("skippedPendingReviewAttempts", skippedPendingReview);
        result.put("skippedNoAnswerAttempts", skippedNoAnswers);
        result.put("totalRecalculatedScore", totalRecalculatedScore);
        result.put("scoreReleaseReady", readiness.get("scoreReleaseReady"));
        result.put("scoreReleaseBlockers", readiness.get("scoreReleaseBlockers"));
        result.put("blockerDetails", readiness.get("blockerDetails"));
        return result;
    }

    @Transactional
    public Map<String, Object> finalizeActiveAttempts(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        lockExamForScoreReleaseTransition(jt, id);
        Integer releaseStatus = currentScoreReleaseStatus(jt, id);
        if (releaseStatus != null && releaseStatus == 1) {
            throw new IllegalStateException("Published scores cannot finalize attempts. Revoke scores first if a correction is required.");
        }
        Map<String, Object> state = examLifecycleState(jt, id);
        int examStatus = ((Number) state.get("status")).intValue();
        int endedByTime = ((Number) state.get("endedByTime")).intValue();
        if (examStatus != EXAM_STATUS_CLOSED && endedByTime != 1) {
            throw new IllegalStateException("Active attempts can only be bulk-finalized after the exam has ended or been closed");
        }

        int activeBefore = countAttemptsByStatus(jt, id, 1);
        int pendingReviewBefore = countAttemptsByStatus(jt, id, 4);
        int completedBefore = countAttemptsByStatus(jt, id, 5);
        List<Long> activeAttemptIds = jt.queryForList("""
                SELECT a.id
                FROM exam_attempt a
                WHERE a.exam_id = ? AND a.status = 1
                ORDER BY a.id
                """, Long.class, id);

        int forcedSubmitted = 0;
        int completedByFinalize = 0;
        int pendingReviewByFinalize = 0;
        List<String> failures = new ArrayList<>();
        for (Long attemptId : activeAttemptIds) {
            try {
                Map<String, Object> attempt = loadAttemptForSubmit(jt, attemptId, null);
                Map<String, Object> result = finalizeAttempt(jt, attempt, loadDraftAnswerMap(jt, attemptId),
                        "FORCED", "Bulk finalized by teacher or administrator", false, null);
                forcedSubmitted++;
                int status = ((Number) result.getOrDefault("status", 0)).intValue();
                if (status == 4) {
                    pendingReviewByFinalize++;
                } else if (status == 5) {
                    completedByFinalize++;
                }
            } catch (RuntimeException ex) {
                failures.add("Attempt #" + attemptId + ": " + ex.getMessage());
            }
        }

        Map<String, Object> readiness = buildScoreReleaseReadiness(jt, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", id);
        result.put("scoreReleaseStatus", releaseStatus == null ? 0 : releaseStatus);
        result.put("activeBefore", activeBefore);
        result.put("pendingReviewBefore", pendingReviewBefore);
        result.put("completedBefore", completedBefore);
        result.put("eligibleActiveAttempts", activeAttemptIds.size());
        result.put("forcedSubmittedAttempts", forcedSubmitted);
        result.put("completedByFinalize", completedByFinalize);
        result.put("pendingReviewByFinalize", pendingReviewByFinalize);
        result.put("failedAttempts", failures.size());
        result.put("failureMessages", failures.stream().limit(10).toList());
        result.put("activeAfter", readiness.get("activeAttemptCount"));
        result.put("pendingReviewAfter", readiness.get("pendingReviewAttemptCount"));
        result.put("completedAfter", readiness.get("completedAttemptCount"));
        result.put("nonFinalAfter", readiness.get("nonFinalStartedAttemptCount"));
        result.put("scoreReleaseReady", readiness.get("scoreReleaseReady"));
        result.put("scoreReleaseBlockers", readiness.get("scoreReleaseBlockers"));
        result.put("blockerDetails", readiness.get("blockerDetails"));
        return result;
    }

    @Transactional
    public Map<String, Object> publishScores(Long id, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        lockExamForScoreReleaseTransition(jt, id);
        Integer statusFrom = currentScoreReleaseStatus(jt, id);
        if (statusFrom != null && statusFrom == 1) {
            throw new IllegalStateException("Scores have already been published");
        }
        requireExamReadyForScoreRelease(jt, id);
        Integer pendingReviews = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE exam_id = ? AND status = 4
                """, Integer.class, id);
        if (pendingReviews != null && pendingReviews > 0) {
            throw new IllegalStateException("Scores cannot be published while reviews are pending");
        }
        Integer unscoredCompletedAttempts = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE exam_id = ? AND status = 5 AND score IS NULL
                """, Integer.class, id);
        if (unscoredCompletedAttempts != null && unscoredCompletedAttempts > 0) {
            throw new IllegalStateException("Scores cannot be published while completed attempts have missing scores");
        }
        Integer completedAttempts = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE exam_id = ? AND status = 5 AND score IS NOT NULL
                """, Integer.class, id);
        if (completedAttempts == null || completedAttempts == 0) {
            throw new IllegalStateException("No completed attempts are available for score release");
        }
        jt.update("""
                INSERT INTO score_release (exam_id, status, published_by, published_at, revoked_by, revoked_at,
                                           publish_note, revoke_reason, note)
                VALUES (?, 1, ?, NOW(), NULL, NULL, NULL, NULL, NULL)
                ON DUPLICATE KEY UPDATE status = 1, published_by = VALUES(published_by),
                    published_at = NOW(), revoked_by = NULL, revoked_at = NULL,
                    publish_note = VALUES(publish_note), revoke_reason = NULL, note = NULL
                """, id, user.getId());

        List<Map<String, Object>> completedAttemptRows = jt.queryForList("""
                SELECT id AS attemptId, user_id AS userId
                FROM exam_attempt
                WHERE exam_id = ? AND status = 5 AND score IS NOT NULL
                """, id);
        String examName = jt.queryForObject("SELECT exam_name FROM exam WHERE id = ?", String.class, id);
        Set<Long> notifiedStudentIds = new LinkedHashSet<>();
        for (Map<String, Object> attemptRow : completedAttemptRows) {
            Long attemptId = numberValue(attemptRow.get("attemptId"));
            Long studentId = numberValue(attemptRow.get("userId"));
            if (attemptId == null || studentId == null) {
                continue;
            }
            notifiedStudentIds.add(studentId);
            notificationService.send(studentId, "Score released: " + examName,
                    "Your exam score has been released. Please check the results page.", "SCORE",
                    studentResultLink(attemptId), "EXAM_ATTEMPT", attemptId);
        }
        Long scoreReleaseLogId = recordScoreReleaseLog(jt, id, SCORE_RELEASE_ACTION_PUBLISH, statusFrom, 1, null, user.getId(),
                completedAttempts, notifiedStudentIds.size(), completedAttemptRows.size());
        Map<String, Object> state = new HashMap<>(scoreReleaseState(jt, id));
        state.put("completedAttempts", completedAttempts);
        state.put("unscoredCompletedAttempts", 0);
        state.put("pendingReviewAttempts", 0);
        state.put("pendingAnswerReviewCount", 0);
        state.put("activeAttempts", 0);
        state.put("scoreReleaseReady", 1);
        state.put("scoreReleaseBlockers", "");
        state.put("notifiedStudents", notifiedStudentIds.size());
        state.put("notifiedAttempts", completedAttemptRows.size());
        state.put("scoreReleaseLogId", scoreReleaseLogId);
        return state;
    }

    @Transactional
    public Map<String, Object> revokeScores(Long id, ScoreRevokeRequest request, AuthUser user) {
        id = requirePositiveExamId(id);
        requireOwnedExam(id, user);
        JdbcTemplate jt = requireJdbcTemplate();
        lockExamForScoreReleaseTransition(jt, id);
        String reason = scoreRevokeReason(request);
        Integer statusFrom = currentScoreReleaseStatus(jt, id);
        if (statusFrom == null || statusFrom != 1) {
            throw new IllegalStateException("Scores have not been published");
        }
        List<Map<String, Object>> visibleAttemptRows = jt.queryForList("""
                SELECT a.id AS attemptId, a.user_id AS userId
                FROM exam_attempt a
                JOIN score_release sr ON sr.exam_id = a.exam_id AND sr.status = 1
                WHERE a.exam_id = ? AND a.status = 5 AND a.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = a.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """, id);
        jt.update("""
                INSERT INTO score_release (exam_id, status, revoked_by, revoked_at, revoke_reason, note)
                VALUES (?, 0, ?, NOW(), ?, ?)
                ON DUPLICATE KEY UPDATE status = 0, revoked_by = VALUES(revoked_by), revoked_at = NOW(),
                    revoke_reason = VALUES(revoke_reason), note = VALUES(note)
                """, id, user.getId(), reason, reason);
        String examName = jt.queryForObject("SELECT exam_name FROM exam WHERE id = ?", String.class, id);
        Set<Long> notifiedStudentIds = new LinkedHashSet<>();
        String revokeContent = "Your exam score release has been revoked. Reason: " + reason;
        for (Map<String, Object> attemptRow : visibleAttemptRows) {
            Long attemptId = numberValue(attemptRow.get("attemptId"));
            Long studentId = numberValue(attemptRow.get("userId"));
            if (attemptId == null || studentId == null) {
                continue;
            }
            notifiedStudentIds.add(studentId);
            notificationService.send(studentId, "Score revoked: " + examName,
                    revokeContent, "SCORE_REVOKED",
                    studentResultLink(attemptId), "EXAM_ATTEMPT", attemptId);
        }
        Long scoreReleaseLogId = recordScoreReleaseLog(jt, id, SCORE_RELEASE_ACTION_REVOKE, statusFrom, 0, reason, user.getId(),
                visibleAttemptRows.size(), notifiedStudentIds.size(), visibleAttemptRows.size());
        Map<String, Object> state = new HashMap<>(scoreReleaseState(jt, id));
        state.put("visibleAttemptsBeforeRevoke", visibleAttemptRows.size());
        state.put("notifiedStudents", notifiedStudentIds.size());
        state.put("notifiedAttempts", visibleAttemptRows.size());
        state.put("revokeReason", reason);
        state.put("scoreReleaseLogId", scoreReleaseLogId);
        return state;
    }

    private Map<String, Object> scoreReleaseState(JdbcTemplate jt, Long examId) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT exam_id AS examId, status, published_by AS publishedBy, published_at AS publishedAt,
                       revoked_by AS revokedBy, revoked_at AS revokedAt,
                       COALESCE(publish_note, note) AS publishNote,
                       COALESCE(revoke_reason, note) AS revokeReason,
                       note
                FROM score_release
                WHERE exam_id = ?
                """, examId);
        if (rows.isEmpty()) {
            Map<String, Object> state = new HashMap<>();
            state.put("examId", examId);
            state.put("status", 0);
            return state;
        }
        return rows.get(0);
    }

    private Map<String, Object> buildScoreReleaseReadiness(JdbcTemplate jt, Long examId) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.id AS examId, e.exam_name AS examName, e.status,
                       e.start_time AS startTime, e.end_time AS endTime,
                       COALESCE(sr.status, 0) AS scoreReleaseStatus,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                       (SELECT COUNT(*)
                        FROM answer_record ar
                        JOIN exam_attempt a ON a.id = ar.attempt_id
                        WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0) AS startedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 0) AS pendingScoreAppealCount,
                       (SELECT COUNT(*)
                        FROM score_appeal sa
                        LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                        WHERE ((a.id IS NOT NULL AND a.exam_id = e.id)
                            OR (a.id IS NULL AND sa.exam_id = e.id))
                          AND sa.status = 1
                          AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                FROM exam e
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE e.id = ? AND e.deleted = 0
                """, examId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        Map<String, Object> readiness = new HashMap<>(rows.get(0));
        LocalDateTime now = LocalDateTime.now();
        boolean ended = scoreReleaseExamEnded(readiness, now);
        List<String> blockers = scoreReleaseBlockers(readiness, now);
        readiness.put("ended", ended ? 1 : 0);
        readiness.put("ready", blockers.isEmpty() ? 1 : 0);
        readiness.put("scoreReleaseReady", blockers.isEmpty() ? 1 : 0);
        readiness.put("blockers", blockers);
        readiness.put("scoreReleaseBlockers", String.join(",", blockers));
        readiness.put("blockerDetails", scoreReleaseBlockerDetails(readiness, blockers));
        return readiness;
    }

    private List<Map<String, Object>> scoreReleaseBlockerDetails(Map<String, Object> exam, List<String> blockers) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (String blocker : blockers) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("code", blocker);
            detail.put("message", scoreReleaseBlockerMessage(blocker));
            detail.put("count", scoreReleaseBlockerCount(exam, blocker));
            detail.put("action", scoreReleaseBlockerAction(blocker));
            details.add(detail);
        }
        return details;
    }

    private long scoreReleaseBlockerCount(Map<String, Object> exam, String blocker) {
        return switch (blocker) {
            case "ALREADY_RELEASED" -> 1L;
            case "EXAM_NOT_ENDED" -> 1L;
            case "ACTIVE_ATTEMPTS" -> longValue(exam.get("activeAttemptCount"), 0L);
            case "PENDING_REVIEW" -> longValue(exam.get("pendingReviewAttemptCount"), 0L);
            case "PENDING_REVIEW_ANSWERS" -> longValue(exam.get("pendingAnswerReviewCount"), 0L);
            case "NON_FINAL_ATTEMPTS" -> longValue(exam.get("nonFinalStartedAttemptCount"), 0L);
            case "PENDING_APPEALS" -> longValue(exam.get("pendingScoreAppealCount"), 0L);
            case "OPEN_RECHECK" -> longValue(exam.get("openRecheckAppealCount"), 0L);
            case "UNSCORED_COMPLETED" -> longValue(exam.get("unscoredCompletedAttemptCount"), 0L);
            case "NO_COMPLETED_ATTEMPTS" -> longValue(exam.get("completedAttemptCount"), 0L);
            default -> 0L;
        };
    }

    private String scoreReleaseBlockerMessage(String blocker) {
        return switch (blocker) {
            case "ALREADY_RELEASED" -> "Scores have already been published";
            case "EXAM_NOT_ENDED" -> "Scores can only be published after the exam has ended";
            case "ACTIVE_ATTEMPTS" -> "Scores cannot be published while attempts are in progress";
            case "PENDING_REVIEW" -> "Scores cannot be published while reviews are pending";
            case "PENDING_REVIEW_ANSWERS" -> "Scores cannot be published while answers are still waiting for review";
            case "NON_FINAL_ATTEMPTS" -> "Scores cannot be published while started attempts are not finalized";
            case "PENDING_APPEALS" -> "Scores cannot be published while score appeals are pending";
            case "OPEN_RECHECK" -> "Scores cannot be published while recheck appeals are open";
            case "UNSCORED_COMPLETED" -> "Scores cannot be published while completed attempts have missing scores";
            case "NO_COMPLETED_ATTEMPTS" -> "No completed attempts are available for score release";
            default -> "Scores are not ready to publish";
        };
    }

    private String scoreReleaseBlockerAction(String blocker) {
        return switch (blocker) {
            case "ALREADY_RELEASED" -> "Use revoke before republishing.";
            case "EXAM_NOT_ENDED" -> "Wait until the exam ends or close it manually.";
            case "ACTIVE_ATTEMPTS" -> "Wait for active students or force-submit through monitoring.";
            case "PENDING_REVIEW", "PENDING_REVIEW_ANSWERS" -> "Finish manual review and scoring.";
            case "NON_FINAL_ATTEMPTS" -> "Finalize all started attempts.";
            case "PENDING_APPEALS" -> "Reply to pending score appeals.";
            case "OPEN_RECHECK" -> "Complete required score rechecks.";
            case "UNSCORED_COMPLETED" -> "Fill missing scores for completed attempts.";
            case "NO_COMPLETED_ATTEMPTS" -> "Wait for at least one finalized scored attempt.";
            default -> "Check exam attempts, review progress, and appeals.";
        };
    }

    private void lockExamForScoreReleaseTransition(JdbcTemplate jt, Long id) {
        List<Long> ids = jt.queryForList("""
                SELECT id
                FROM exam
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """, Long.class, id);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
    }

    private int countMissingCompletedScores(JdbcTemplate jt, Long examId) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE exam_id = ? AND status = 5 AND score IS NULL
                """, Integer.class, examId);
        return count == null ? 0 : count;
    }

    private int countMissingCompletedScoresWithPendingReview(JdbcTemplate jt, Long examId) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt a
                WHERE a.exam_id = ?
                  AND a.status = 5
                  AND a.score IS NULL
                  AND EXISTS (
                    SELECT 1 FROM answer_record ar
                    WHERE ar.attempt_id = a.id AND ar.review_status = 0
                  )
                """, Integer.class, examId);
        return count == null ? 0 : count;
    }

    private int countMissingCompletedScoresWithoutAnswers(JdbcTemplate jt, Long examId) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt a
                WHERE a.exam_id = ?
                  AND a.status = 5
                  AND a.score IS NULL
                  AND NOT EXISTS (SELECT 1 FROM answer_record ar WHERE ar.attempt_id = a.id)
                """, Integer.class, examId);
        return count == null ? 0 : count;
    }

    private int countAttemptsByStatus(JdbcTemplate jt, Long examId, int status) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE exam_id = ? AND status = ?
                """, Integer.class, examId, status);
        return count == null ? 0 : count;
    }

    private BigDecimal sumAttemptScore(JdbcTemplate jt, Long attemptId) {
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
        return totalScore == null ? BigDecimal.ZERO : totalScore;
    }

    private void enrichExamLifecycleHealth(List<Map<String, Object>> exams) {
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> exam : exams) {
            exam.putAll(examLifecycleHealthForRow(exam, now));
        }
    }

    private Map<String, Object> examLifecycleHealthForRow(Map<String, Object> exam, LocalDateTime now) {
        long status = longValue(exam.get("status"), -1L);
        long scoreReleaseStatus = longValue(exam.get("scoreReleaseStatus"), 0L);
        long activeAttempts = longValue(exam.get("activeAttemptCount"), 0L);
        long nonFinalAttempts = longValue(exam.get("nonFinalStartedAttemptCount"), 0L);
        long pendingReviews = longValue(exam.get("pendingReviewAttemptCount"), 0L);
        long pendingAnswers = longValue(exam.get("pendingAnswerReviewCount"), 0L);
        long pendingAppeals = longValue(exam.get("pendingScoreAppealCount"), 0L);
        long openRechecks = longValue(exam.get("openRecheckAppealCount"), 0L);
        long unscoredCompleted = longValue(exam.get("unscoredCompletedAttemptCount"), 0L);
        long completedAttempts = longValue(exam.get("completedAttemptCount"), 0L);
        long candidateSnapshots = longValue(exam.get("candidateSnapshotCount"), 0L);
        long questionSnapshots = longValue(exam.get("questionSnapshotCount"), 0L);
        long offlineMonitor = longValue(exam.get("offlineMonitorCount"), 0L);
        long highRiskMonitor = longValue(exam.get("highRiskMonitorCount"), 0L);
        long staleDrafts = longValue(exam.get("staleDraftCount"), 0L);
        long timeoutPressure = longValue(exam.get("timeoutPressureCount"), 0L);
        long deadlinePassedActive = longValue(exam.get("deadlinePassedActiveCount"), 0L);
        LocalDateTime startTime = localDateTimeValue(exam.get("startTime"));
        boolean startedWindow = startTime == null || !startTime.isAfter(now);
        boolean ended = scoreReleaseExamEnded(exam, now);

        if (status == EXAM_STATUS_PENDING_APPROVAL) {
            List<String> blockers = new ArrayList<>();
            blockers.add(startedWindow ? "APPROVAL_START_PASSED" : "APPROVAL_PENDING");
            if (longValue(exam.get("targetCount"), 0L) == 0L) {
                blockers.add("NO_TARGET");
            }
            return lifecycleHealth(startedWindow ? "APPROVAL_START_PASSED" : "APPROVAL_PENDING",
                    "APPROVAL",
                    startedWindow ? "HIGH" : "WARN",
                    startedWindow ? "Approve, reject, or reschedule before students are affected"
                            : "Approve or reject the submitted exam",
                    "APPROVAL",
                    true,
                    blockers);
        }

        if (status == EXAM_STATUS_REJECTED) {
            return lifecycleHealth("REJECTED", "APPROVAL", "WARN",
                    "Edit and resubmit the rejected exam",
                    "EDIT",
                    true,
                    List.of("EXAM_REJECTED"));
        }

        if (status == EXAM_STATUS_PUBLISHED && (candidateSnapshots == 0L || questionSnapshots == 0L)) {
            List<String> blockers = new ArrayList<>();
            if (candidateSnapshots == 0L) {
                blockers.add("CANDIDATE_SNAPSHOT_MISSING");
            }
            if (questionSnapshots == 0L) {
                blockers.add("QUESTION_SNAPSHOT_MISSING");
            }
            return lifecycleHealth("SNAPSHOT_RISK", "RISK", "HIGH",
                    "Open snapshot evidence and repair publication data",
                    "SNAPSHOT",
                    true,
                    blockers);
        }

        if (scoreReleaseStatus == 1L) {
            return lifecycleHealth("SCORE_RELEASED", "RELEASED", "OK",
                    "Review score release logs or export released scores",
                    "SCORE_LOGS",
                    false,
                    List.of());
        }

        if (status == EXAM_STATUS_PUBLISHED && !ended) {
            List<String> blockers = new ArrayList<>();
            if (deadlinePassedActive > 0L) {
                blockers.add("DEADLINE_PASSED_ACTIVE");
            }
            if (offlineMonitor > 0L) {
                blockers.add("OFFLINE_MONITOR");
            }
            if (highRiskMonitor > 0L) {
                blockers.add("HIGH_RISK_MONITOR");
            }
            if (staleDrafts > 0L) {
                blockers.add("STALE_DRAFTS");
            }
            if (!blockers.isEmpty()) {
                return lifecycleHealth("MONITOR_RISK", "RISK",
                        deadlinePassedActive > 0L || highRiskMonitor > 0L ? "HIGH" : "WARN",
                        "Open live monitor and handle risky active attempts",
                        "MONITOR",
                        true,
                        blockers);
            }
            if (activeAttempts > 0L) {
                return lifecycleHealth(timeoutPressure > 0L ? "TIMEOUT_PRESSURE" : "RUNNING_HEALTHY",
                        "RUNNING",
                        timeoutPressure > 0L ? "WARN" : "INFO",
                        timeoutPressure > 0L ? "Watch attempts near timeout" : "Monitor active attempts",
                        "MONITOR",
                        timeoutPressure > 0L,
                        timeoutPressure > 0L ? List.of("TIMEOUT_PRESSURE") : List.of());
            }
            if (startedWindow) {
                return lifecycleHealth("RUNNING_NO_ACTIVE", "RUNNING", "INFO",
                        "Exam window is open; watch candidate entry",
                        "MONITOR",
                        false,
                        List.of());
            }
            return lifecycleHealth("WAITING_TO_START", "WAITING", "INFO",
                    "Exam is published and waiting for the start time",
                    "SNAPSHOT",
                    false,
                    List.of());
        }

        if (ended || status == EXAM_STATUS_CLOSED) {
            if (activeAttempts > 0L || nonFinalAttempts > 0L || deadlinePassedActive > 0L) {
                List<String> blockers = new ArrayList<>();
                if (activeAttempts > 0L) {
                    blockers.add("ACTIVE_ATTEMPTS");
                }
                if (nonFinalAttempts > 0L) {
                    blockers.add("NON_FINAL_ATTEMPTS");
                }
                if (deadlinePassedActive > 0L) {
                    blockers.add("DEADLINE_PASSED_ACTIVE");
                }
                return lifecycleHealth("FINALIZE_REQUIRED", "RISK", "HIGH",
                        "Finalize or force-submit non-final attempts",
                        "MONITOR",
                        true,
                        blockers);
            }
            if (pendingReviews > 0L || pendingAnswers > 0L) {
                List<String> blockers = new ArrayList<>();
                if (pendingReviews > 0L) {
                    blockers.add("PENDING_REVIEW");
                }
                if (pendingAnswers > 0L) {
                    blockers.add("PENDING_REVIEW_ANSWERS");
                }
                return lifecycleHealth("REVIEW_REQUIRED", "REVIEW", "HIGH",
                        "Open review queue and complete manual grading",
                        "REVIEW",
                        true,
                        blockers);
            }
            if (openRechecks > 0L) {
                return lifecycleHealth("RECHECK_REQUIRED", "REVIEW", "HIGH",
                        "Close required score rechecks before publishing scores",
                        "RECHECK",
                        true,
                        List.of("OPEN_RECHECK"));
            }
            if (pendingAppeals > 0L) {
                return lifecycleHealth("APPEAL_REQUIRED", "REVIEW", "WARN",
                        "Reply to pending score appeals",
                        "APPEALS",
                        true,
                        List.of("PENDING_APPEALS"));
            }
            if (unscoredCompleted > 0L) {
                return lifecycleHealth("SCORE_MISSING", "REVIEW", "HIGH",
                        "Investigate completed attempts missing scores",
                        "READINESS",
                        true,
                        List.of("UNSCORED_COMPLETED"));
            }
            if (completedAttempts == 0L) {
                return lifecycleHealth("NO_COMPLETED_ATTEMPTS", "SCORE", "WARN",
                        "No completed attempts are available for score release",
                        "SNAPSHOT",
                        true,
                        List.of("NO_COMPLETED_ATTEMPTS"));
            }
            List<String> scoreBlockers = scoreReleaseBlockers(exam, now).stream()
                    .filter(blocker -> !"ALREADY_RELEASED".equals(blocker))
                    .toList();
            if (scoreBlockers.isEmpty()) {
                return lifecycleHealth("SCORE_READY", "SCORE", "INFO",
                        "Publish scores after final confirmation",
                        "READINESS",
                        true,
                        List.of());
            }
            return lifecycleHealth("SCORE_BLOCKED", "SCORE", "WARN",
                    scoreReleaseBlockerAction(scoreBlockers.get(0)),
                    "READINESS",
                    true,
                    scoreBlockers);
        }

        return lifecycleHealth("UNKNOWN", "RISK", "WARN",
                "Open the lifecycle timeline for manual inspection",
                "TIMELINE",
                true,
                List.of("UNKNOWN_STATE"));
    }

    private Map<String, Object> lifecycleHealth(String state, String group, String severity, String nextAction,
                                                String nextActionType, boolean actionRequired,
                                                List<String> blockers) {
        Map<String, Object> health = new HashMap<>();
        health.put("lifecycleState", state);
        health.put("lifecycleGroup", group);
        health.put("lifecycleSeverity", severity);
        health.put("lifecycleNextAction", nextAction);
        health.put("lifecycleNextActionType", nextActionType);
        health.put("lifecycleActionRequired", actionRequired ? 1 : 0);
        health.put("lifecycleRisk", "HIGH".equals(severity) || "RISK".equals(group) ? 1 : 0);
        health.put("lifecycleBlockers", blockers);
        health.put("lifecycleBlockerCodes", String.join(",", blockers));
        return health;
    }

    private void appendScoreReleaseReadiness(List<Map<String, Object>> exams) {
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> exam : exams) {
            List<String> blockers = scoreReleaseBlockers(exam, now);
            exam.put("scoreReleaseReady", blockers.isEmpty() ? 1 : 0);
            exam.put("scoreReleaseBlockers", String.join(",", blockers));
        }
    }

    private List<String> scoreReleaseBlockers(Map<String, Object> exam, LocalDateTime now) {
        List<String> blockers = new ArrayList<>();
        if (longValue(exam.get("scoreReleaseStatus"), 0L) == 1L) {
            blockers.add("ALREADY_RELEASED");
        }
        if (!scoreReleaseExamEnded(exam, now)) {
            blockers.add("EXAM_NOT_ENDED");
        }
        if (longValue(exam.get("activeAttemptCount"), 0L) > 0L) {
            blockers.add("ACTIVE_ATTEMPTS");
        }
        if (longValue(exam.get("pendingReviewAttemptCount"), 0L) > 0L) {
            blockers.add("PENDING_REVIEW");
        }
        if (longValue(exam.get("pendingAnswerReviewCount"), 0L) > 0L) {
            blockers.add("PENDING_REVIEW_ANSWERS");
        }
        if (longValue(exam.get("nonFinalStartedAttemptCount"), 0L) > 0L) {
            blockers.add("NON_FINAL_ATTEMPTS");
        }
        if (longValue(exam.get("pendingScoreAppealCount"), 0L) > 0L) {
            blockers.add("PENDING_APPEALS");
        }
        if (longValue(exam.get("openRecheckAppealCount"), 0L) > 0L) {
            blockers.add("OPEN_RECHECK");
        }
        if (longValue(exam.get("unscoredCompletedAttemptCount"), 0L) > 0L) {
            blockers.add("UNSCORED_COMPLETED");
        }
        if (longValue(exam.get("completedAttemptCount"), 0L) == 0L) {
            blockers.add("NO_COMPLETED_ATTEMPTS");
        }
        return blockers;
    }

    private boolean scoreReleaseExamEnded(Map<String, Object> exam, LocalDateTime now) {
        if (longValue(exam.get("status"), -1L) == EXAM_STATUS_CLOSED) {
            return true;
        }
        LocalDateTime endTime = localDateTimeValue(exam.get("endTime"));
        return endTime != null && !endTime.isAfter(now);
    }

    private Integer currentScoreReleaseStatus(JdbcTemplate jt, Long examId) {
        List<Integer> statuses = jt.queryForList(
                "SELECT status FROM score_release WHERE exam_id = ?", Integer.class, examId);
        if (statuses.isEmpty() || statuses.get(0) == null) {
            return 0;
        }
        return statuses.get(0);
    }

    private int currentExamStatus(JdbcTemplate jt, Long id) {
        id = requirePositiveExamId(id);
        List<Integer> statuses = jt.queryForList(
                "SELECT status FROM exam WHERE id = ? AND deleted = 0", Integer.class, id);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        return statuses.get(0) == null ? 0 : statuses.get(0);
    }

    private int currentExamStatusForUpdate(JdbcTemplate jt, Long id) {
        id = requirePositiveExamId(id);
        List<Integer> statuses = jt.queryForList("""
                SELECT status
                FROM exam
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """, Integer.class, id);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        return statuses.get(0) == null ? 0 : statuses.get(0);
    }

    private void requireExamEditable(JdbcTemplate jt, Long id) {
        Map<String, Object> state = examLifecycleState(jt, id);
        int status = ((Number) state.get("status")).intValue();
        if (status != EXAM_STATUS_PENDING_APPROVAL && status != EXAM_STATUS_PUBLISHED
                && status != EXAM_STATUS_REJECTED) {
            throw new IllegalStateException("Only pending, rejected or published future exams can be edited");
        }
        if (((Number) state.get("startedAttemptCount")).intValue() > 0) {
            throw new IllegalStateException("Exam cannot be edited after a student has entered");
        }
        if (status == EXAM_STATUS_PUBLISHED && ((Number) state.get("startedByTime")).intValue() == 1) {
            throw new IllegalStateException("Exam cannot be edited after start time");
        }
    }

    private void validateExamUpdateScore(JdbcTemplate jt, Long id, BigDecimal passScore) {
        if (passScore == null) {
            return;
        }
        BigDecimal totalScore = jt.queryForObject("""
                SELECT COALESCE((SELECT SUM(eqs.score)
                                 FROM exam_question_snapshot eqs
                                 WHERE eqs.exam_id = e.id),
                                p.total_score, 0)
                FROM exam e
                JOIN paper p ON p.id = e.paper_id AND p.deleted = 0
                WHERE e.id = ? AND e.deleted = 0
                """, BigDecimal.class, id);
        if (totalScore == null) {
            throw new IllegalArgumentException("Exam paper total score is unavailable");
        }
        if (passScore.compareTo(totalScore) > 0) {
            throw new IllegalArgumentException("Pass score cannot be greater than exam total score");
        }
    }

    private void requireExamDeletable(JdbcTemplate jt, Long id) {
        Map<String, Object> state = examLifecycleState(jt, id);
        int status = ((Number) state.get("status")).intValue();
        if (((Number) state.get("startedAttemptCount")).intValue() > 0) {
            throw new IllegalStateException("Exam cannot be deleted after a student has entered");
        }
        if (status == EXAM_STATUS_PUBLISHED && ((Number) state.get("startedByTime")).intValue() == 1) {
            throw new IllegalStateException("Exam cannot be deleted after start time");
        }
    }

    private void requireExamReadyForScoreRelease(JdbcTemplate jt, Long id) {
        Map<String, Object> state = examLifecycleState(jt, id);
        int status = ((Number) state.get("status")).intValue();
        int endedByTime = ((Number) state.get("endedByTime")).intValue();
        if (status != EXAM_STATUS_CLOSED && endedByTime != 1) {
            throw new IllegalStateException("Scores can only be published after the exam has ended");
        }
        if (((Number) state.get("activeAttemptCount")).intValue() > 0) {
            throw new IllegalStateException("Scores cannot be published while attempts are in progress");
        }
        if (((Number) state.get("nonFinalStartedAttemptCount")).intValue() > 0) {
            throw new IllegalStateException("Scores cannot be published while started attempts are not finalized");
        }
        Integer pendingAnswerReviews = jt.queryForObject("""
                SELECT COUNT(*)
                FROM answer_record ar
                JOIN exam_attempt a ON a.id = ar.attempt_id
                WHERE a.exam_id = ? AND ar.review_status = 0
                """, Integer.class, id);
        if (pendingAnswerReviews != null && pendingAnswerReviews > 0) {
            throw new IllegalStateException("Scores cannot be published while answers are still waiting for review");
        }
        Integer pendingAppeals = jt.queryForObject("""
                SELECT COUNT(*)
                FROM score_appeal sa
                LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                WHERE ((a.id IS NOT NULL AND a.exam_id = ?)
                    OR (a.id IS NULL AND sa.exam_id = ?))
                  AND sa.status = 0
                """, Integer.class, id, id);
        if (pendingAppeals != null && pendingAppeals > 0) {
            throw new IllegalStateException("Scores cannot be published while score appeals are pending");
        }
        Integer openRecheckAppeals = jt.queryForObject("""
                SELECT COUNT(*)
                FROM score_appeal sa
                LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                WHERE ((a.id IS NOT NULL AND a.exam_id = ?)
                    OR (a.id IS NULL AND sa.exam_id = ?))
                  AND sa.status = 1
                  AND sa.handling_result = 'RECHECK_REQUIRED'
                """, Integer.class, id, id);
        if (openRecheckAppeals != null && openRecheckAppeals > 0) {
            throw new IllegalStateException("Scores cannot be published while recheck appeals are open");
        }
    }

    private Map<String, Object> examLifecycleState(JdbcTemplate jt, Long id) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.status,
                       CASE WHEN e.start_time IS NOT NULL AND e.start_time <= NOW() THEN 1 ELSE 0 END AS startedByTime,
                       CASE WHEN e.end_time IS NOT NULL AND e.end_time <= NOW() THEN 1 ELSE 0 END AS endedByTime,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0) AS startedAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                       (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount
                FROM exam e
                WHERE e.id = ? AND e.deleted = 0
                """, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        return rows.get(0);
    }

    private ExamPublishPlan validateExamPublishPlan(JdbcTemplate jt, ExamRequest request, AuthUser creator) {
        validateExamRequest(request);
        validateExamSchedule(request);
        Map<String, Object> paper = validateExamPaper(jt, request.getPaperId());
        BigDecimal totalScore = (BigDecimal) paper.getOrDefault("totalScore", BigDecimal.ZERO);
        if (request.getPassScore() != null && request.getPassScore().compareTo(totalScore) > 0) {
            throw new IllegalArgumentException("Pass score cannot be greater than paper total score");
        }
        List<TargetSpec> targets = normalizeExamTargets(request, creator);
        Map<Long, TargetSpec> studentSources = resolveExamStudentSources(jt, targets);
        if (studentSources.isEmpty()) {
            throw new IllegalArgumentException("Exam target resolves to zero active students");
        }
        int questionCount = ((Number) paper.get("questionCount")).intValue();
        return new ExamPublishPlan(paper, targets, studentSources, questionCount, totalScore);
    }

    private ExamPublishPlan loadExamPublishPlanForRepair(JdbcTemplate jt, Long examId, Long paperId, AuthUser user) {
        Map<String, Object> paper = validateExamPaper(jt, paperId);
        List<Map<String, Object>> targetRows = jt.queryForList("""
                SELECT target_type AS targetType, target_id AS targetId, target_code AS targetCode
                FROM exam_target
                WHERE exam_id = ?
                ORDER BY id
                """, examId);
        if (targetRows.isEmpty()) {
            throw new IllegalStateException("Exam has no target range to repair candidate snapshots");
        }
        List<TargetSpec> targets = new ArrayList<>();
        for (Map<String, Object> row : targetRows) {
            String targetType = stringValue(row.get("targetType"));
            Long targetId = numberValue(row.get("targetId"));
            if (targetType == null || targetId == null) {
                continue;
            }
            TargetSpec target = new TargetSpec(targetType, targetId, stringValue(row.get("targetCode")));
            validateExamTarget(target, user);
            targets.add(target);
        }
        if (targets.isEmpty()) {
            throw new IllegalStateException("Exam target range is invalid");
        }
        Map<Long, TargetSpec> studentSources = resolveExamStudentSources(jt, targets);
        if (studentSources.isEmpty()) {
            throw new IllegalStateException("Exam target resolves to zero active students");
        }
        int questionCount = ((Number) paper.get("questionCount")).intValue();
        BigDecimal totalScore = (BigDecimal) paper.getOrDefault("totalScore", BigDecimal.ZERO);
        return new ExamPublishPlan(paper, targets, studentSources, questionCount, totalScore);
    }

    private void validateExamSchedule(ExamRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        long windowSeconds = Duration.between(request.getStartTime(), request.getEndTime()).getSeconds();
        if (request.getDurationMinutes() != null && request.getDurationMinutes() * 60L > windowSeconds) {
            throw new IllegalArgumentException("Duration cannot be longer than the exam time window");
        }
    }

    private Map<String, Object> validateExamPaper(JdbcTemplate jt, Long paperId) {
        paperId = requirePositiveExamPublishId(paperId, "paperId");
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT p.id AS paperId, p.paper_name AS paperName, p.total_score AS totalScore,
                       p.status, s.subject_name AS subjectName,
                       (SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) AS questionCount
                FROM paper p
                JOIN edu_subject s ON s.id = p.subject_id
                WHERE p.id = ? AND p.deleted = 0
                """, paperId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Published paper is required");
        }
        Map<String, Object> paper = rows.get(0);
        if (((Number) paper.get("status")).intValue() != 1) {
            throw new IllegalArgumentException("Only published papers can be used to publish an exam");
        }
        int questionCount = ((Number) paper.get("questionCount")).intValue();
        if (questionCount == 0) {
            throw new IllegalArgumentException("Paper must contain at least one question");
        }
        Integer invalidQuestions = jt.queryForObject("""
                SELECT COUNT(*)
                FROM paper_question pq
                LEFT JOIN question q ON q.id = pq.question_id
                WHERE pq.paper_id = ?
                  AND (q.id IS NULL OR q.deleted = 1 OR q.status <> 1 OR q.review_status <> 'APPROVED')
                """, Integer.class, paperId);
        if (invalidQuestions != null && invalidQuestions > 0) {
            throw new IllegalArgumentException("Paper contains unavailable or unapproved questions");
        }
        Integer missingSnapshots = jt.queryForObject("""
                SELECT COUNT(*)
                FROM paper_question pq
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                WHERE pq.paper_id = ? AND (pq.question_version_id IS NULL OR qv.id IS NULL)
                """, Integer.class, paperId);
        if (missingSnapshots != null && missingSnapshots > 0) {
            throw new IllegalArgumentException("Paper question version snapshots are incomplete");
        }
        return paper;
    }

    private Map<String, Object> preflightResult(ExamPublishPlan plan) {
        Map<String, Object> result = new HashMap<>();
        result.put("paper", plan.paper);
        result.put("targetCount", plan.targets.size());
        result.put("candidateCount", plan.studentSources.size());
        result.put("questionCount", plan.questionCount);
        result.put("totalScore", plan.totalScore);
        result.put("warnings", preflightWarnings(plan));
        result.put("targets", plan.targets.stream().map(this::targetSummary).toList());
        return result;
    }

    private List<String> preflightWarnings(ExamPublishPlan plan) {
        List<String> warnings = new ArrayList<>();
        if (plan.studentSources.size() < plan.targets.size()) {
            warnings.add("Some targets resolve to the same student or have no additional students");
        }
        return warnings;
    }

    private Map<String, Object> targetSummary(TargetSpec target) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("targetType", target.targetType);
        summary.put("targetId", target.targetId);
        summary.put("targetCode", target.targetCode);
        return summary;
    }

    private void validateExamRequest(ExamRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Exam request is required");
        }
        requirePositiveExamPublishId(request.getPaperId(), "paperId");
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
                targets.add(new TargetSpec("CLASS", requirePositiveExamPublishId(id, "classId"), ""));
            }
        }
        if (request.getClassCourseIds() != null) {
            for (Long id : request.getClassCourseIds()) {
                targets.add(new TargetSpec("CLASS_COURSE", requirePositiveExamPublishId(id, "classCourseId"), ""));
            }
        }
        if (request.getStudentUserIds() != null) {
            for (Long id : request.getStudentUserIds()) {
                targets.add(new TargetSpec("USER", requirePositiveExamPublishId(id, "studentUserId"), ""));
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

    private Long requirePositiveExamPublishId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }

    private Map<Long, TargetSpec> resolveExamStudentSources(JdbcTemplate jt, List<TargetSpec> targets) {
        Map<Long, TargetSpec> studentSources = new HashMap<>();
        for (TargetSpec target : targets) {
            for (Long studentId : resolveTargetStudents(jt, target)) {
                studentSources.putIfAbsent(studentId, target);
            }
        }
        return studentSources;
    }

    private List<Long> resolveTargetStudents(JdbcTemplate jt, TargetSpec target) {
        return switch (target.targetType) {
            case "CLASS" -> jt.queryForList("""
                    SELECT DISTINCT scm.student_user_id
                    FROM student_class_membership scm
                    JOIN sys_user u ON u.id = scm.student_user_id AND u.deleted = 0 AND u.status = 1
                    WHERE scm.class_id = ? AND scm.deleted = 0 AND scm.status = 1
                    """, Long.class, target.targetId);
            case "CLASS_COURSE" -> jt.queryForList("""
                    SELECT DISTINCT sce.student_user_id
                    FROM student_course_enrollment sce
                    JOIN sys_user u ON u.id = sce.student_user_id AND u.deleted = 0 AND u.status = 1
                    WHERE sce.class_course_id = ? AND sce.deleted = 0 AND sce.status = 1
                    """, Long.class, target.targetId);
            case "USER" -> List.of(target.targetId);
            default -> List.of();
        };
    }

    private void syncStudentAttempts(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Set<Long> examIds = new LinkedHashSet<>();
        examIds.addAll(jt.queryForList("""
                SELECT DISTINCT e.id
                FROM exam_candidate_snapshot ecs
                JOIN exam e ON e.id = ecs.exam_id
                WHERE e.deleted = 0 AND e.status = ? AND ecs.user_id = ?
                """, Long.class, EXAM_STATUS_PUBLISHED, user.getId()));
        examIds.addAll(jt.queryForList("""
                SELECT DISTINCT e.id
                FROM exam e
                JOIN exam_target et ON et.exam_id = e.id
                WHERE e.deleted = 0 AND e.status = ? AND et.target_type = 'USER' AND et.target_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id
                  )
                """, Long.class, EXAM_STATUS_PUBLISHED, user.getId()));
        addEligibleExamsByTargets(jt, examIds, "CLASS", teachingScopeService.visibleClassIds(user));
        addEligibleExamsByTargets(jt, examIds, "CLASS_COURSE", teachingScopeService.visibleClassCourseIds(user));
        for (Long examId : examIds) {
            insertCandidateSnapshotIfMissing(jt, examId, user.getId(), "LEGACY", null);
            insertAttemptIfMissing(jt, examId, user.getId());
        }
    }

    private void addEligibleExamsByTargets(JdbcTemplate jt, Set<Long> examIds, String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", targetIds.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>();
        params.add(EXAM_STATUS_PUBLISHED);
        params.add(targetType);
        params.addAll(targetIds);
        examIds.addAll(jt.queryForList("""
                SELECT DISTINCT e.id
                FROM exam e
                JOIN exam_target et ON et.exam_id = e.id
                WHERE e.deleted = 0 AND e.status = ? AND et.target_type = ? AND et.target_id IN ( 
                """
                + placeholders +
                """
                )
                  AND NOT EXISTS (
                      SELECT 1 FROM exam_candidate_snapshot ecs WHERE ecs.exam_id = e.id
                  )
                """, Long.class, params.toArray()));
    }

    private void createPaperSnapshot(JdbcTemplate jt, Long examId, Long paperId) {
        jt.update("DELETE FROM exam_question_option_snapshot WHERE exam_id = ?", examId);
        jt.update("DELETE FROM exam_question_snapshot WHERE exam_id = ?", examId);
        jt.update("""
                INSERT INTO exam_question_snapshot
                    (exam_id, paper_id, question_id, knowledge_point_id, question_type, stem, correct_answer, analysis, score, sort_order)
                SELECT ?, ?, q.id,
                       COALESCE(qv.knowledge_point_id, q.knowledge_point_id),
                       COALESCE(qv.question_type, q.question_type),
                       COALESCE(qv.stem, q.stem),
                       COALESCE(qv.correct_answer, q.correct_answer),
                       COALESCE(qv.analysis, q.analysis),
                       pq.score, pq.sort_order
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                WHERE pq.paper_id = ?
                """, examId, paperId, paperId);
        jt.update("""
                INSERT INTO exam_question_option_snapshot
                    (exam_id, question_id, option_label, option_content, sort_order)
                SELECT ?, pq.question_id,
                       COALESCE(qvo.option_label, qo.option_label),
                       COALESCE(qvo.option_content, qo.option_content),
                       COALESCE(qvo.sort_order, qo.sort_order)
                FROM paper_question pq
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                LEFT JOIN question_version_option qvo ON qvo.question_version_id = qv.id
                LEFT JOIN question_option qo ON qo.question_id = pq.question_id AND qv.id IS NULL
                WHERE pq.paper_id = ?
                  AND COALESCE(qvo.option_label, qo.option_label) IS NOT NULL
                """, examId, paperId);
    }

    private List<Map<String, Object>> loadExamQuestions(JdbcTemplate jt, Long examId, Long paperId) {
        if (hasQuestionSnapshot(jt, examId)) {
            return jt.queryForList("""
                    SELECT question_id AS id, question_id AS questionId, question_type AS questionType,
                           stem, NULL AS difficulty, score, sort_order AS sortOrder
                    FROM exam_question_snapshot
                    WHERE exam_id = ?
                    ORDER BY sort_order, id
                    """, examId);
        }
        return jt.queryForList("""
                SELECT q.id, q.id AS questionId,
                       COALESCE(qv.question_type, q.question_type) AS questionType,
                       COALESCE(qv.stem, q.stem) AS stem,
                       COALESCE(qv.difficulty, q.difficulty) AS difficulty,
                       pq.score, pq.sort_order AS sortOrder
                FROM paper_question pq
                JOIN question q ON pq.question_id = q.id
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                WHERE pq.paper_id = ?
                ORDER BY pq.sort_order
                """, paperId);
    }

    private List<Map<String, Object>> loadExamQuestionOptions(JdbcTemplate jt, Long examId, Long questionId) {
        if (hasQuestionSnapshot(jt, examId)) {
            return jt.queryForList("""
                    SELECT option_label AS optionLabel, option_content AS optionContent,
                           sort_order AS sortOrder
                    FROM exam_question_option_snapshot
                    WHERE exam_id = ? AND question_id = ?
                    ORDER BY sort_order
                    """, examId, questionId);
        }
        return jt.queryForList("""
                SELECT option_label AS optionLabel, option_content AS optionContent,
                       sort_order AS sortOrder
                FROM question_option
                WHERE question_id = ?
                ORDER BY sort_order
                """, questionId);
    }

    private List<Map<String, Object>> loadCandidateSnapshotForAudit(JdbcTemplate jt, Long examId) {
        List<Map<String, Object>> candidates = jt.queryForList("""
                SELECT user_id AS userId, source_type AS sourceType, source_id AS sourceId,
                       real_name AS realName, student_no AS studentNo, class_name AS className,
                       created_at AS createdAt,
                       (
                         SELECT a.id
                         FROM exam_attempt a
                         WHERE a.exam_id = exam_candidate_snapshot.exam_id
                           AND a.user_id = exam_candidate_snapshot.user_id
                           AND a.status = 1
                         ORDER BY a.attempt_no DESC
                         LIMIT 1
                       ) AS activeAttemptId,
                       (
                         SELECT a.status
                         FROM exam_attempt a
                         WHERE a.exam_id = exam_candidate_snapshot.exam_id
                           AND a.user_id = exam_candidate_snapshot.user_id
                         ORDER BY a.attempt_no DESC
                         LIMIT 1
                       ) AS latestAttemptStatus,
                       (
                         SELECT a.submit_type
                         FROM exam_attempt a
                         WHERE a.exam_id = exam_candidate_snapshot.exam_id
                           AND a.user_id = exam_candidate_snapshot.user_id
                         ORDER BY a.attempt_no DESC
                         LIMIT 1
                       ) AS submitType
                FROM exam_candidate_snapshot
                WHERE exam_id = ?
                ORDER BY class_name, student_no, real_name, user_id
                """, examId);
        if (!candidates.isEmpty()) {
            return candidates;
        }
        return jt.queryForList("""
                SELECT DISTINCT a.user_id AS userId, 'LEGACY' AS sourceType, NULL AS sourceId,
                       u.real_name AS realName, sp.student_no AS studentNo, c.class_name AS className,
                       a.created_at AS createdAt,
                       CASE WHEN a.status = 1 THEN a.id ELSE NULL END AS activeAttemptId,
                       a.status AS latestAttemptStatus,
                       a.submit_type AS submitType
                FROM exam_attempt a
                JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE a.exam_id = ?
                ORDER BY c.class_name, sp.student_no, u.real_name, a.user_id
                """, examId);
    }

    private List<Map<String, Object>> loadQuestionSnapshotForAudit(JdbcTemplate jt, Long examId, Long paperId) {
        List<Map<String, Object>> questions = jt.queryForList("""
                SELECT question_id AS questionId, question_type AS questionType, stem,
                       correct_answer AS correctAnswer, analysis, score, sort_order AS sortOrder
                FROM exam_question_snapshot
                WHERE exam_id = ?
                ORDER BY sort_order, id
                """, examId);
        if (!questions.isEmpty() || paperId == null) {
            return questions;
        }
        return jt.queryForList("""
                SELECT q.id AS questionId, q.question_type AS questionType, q.stem,
                       q.correct_answer AS correctAnswer, q.analysis, pq.score, pq.sort_order AS sortOrder
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                WHERE pq.paper_id = ?
                ORDER BY pq.sort_order, pq.id
                """, paperId);
    }

    private List<Map<String, Object>> loadQuestionSnapshotForExport(JdbcTemplate jt, Long examId, Long paperId) {
        List<Map<String, Object>> questions = jt.queryForList("""
                SELECT eqs.question_id AS questionId, eqs.question_type AS questionType,
                       eqs.score, eqs.sort_order AS sortOrder, eqs.created_at AS createdAt,
                       (
                         SELECT COUNT(eqos.id)
                         FROM exam_question_option_snapshot eqos
                         WHERE eqos.exam_id = eqs.exam_id
                           AND eqos.question_id = eqs.question_id
                       ) AS optionCount
                FROM exam_question_snapshot eqs
                WHERE eqs.exam_id = ?
                ORDER BY eqs.sort_order, eqs.id
                """, examId);
        if (!questions.isEmpty() || paperId == null) {
            return questions;
        }
        return jt.queryForList("""
                SELECT q.id AS questionId, q.question_type AS questionType,
                       pq.score, pq.sort_order AS sortOrder, NULL AS createdAt,
                       (
                         SELECT COUNT(qo.id)
                         FROM question_option qo
                         WHERE qo.question_id = q.id
                       ) AS optionCount
                FROM paper_question pq
                JOIN question q ON q.id = pq.question_id
                WHERE pq.paper_id = ?
                ORDER BY pq.sort_order, pq.id
                """, paperId);
    }

    private Map<String, Object> loadAttemptForSubmit(JdbcTemplate jt, Long attemptId, Long userId) {
        attemptId = requirePositiveAttemptId(attemptId);
        List<Object> params = new ArrayList<>();
        params.add(attemptId);
        String userSql = "";
        if (userId != null) {
            userSql = " AND a.user_id = ?";
            params.add(userId);
        }
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT a.*, e.status AS examStatus, e.deleted AS examDeleted,
                       CASE WHEN e.start_time IS NULL OR e.start_time <= NOW() THEN 1 ELSE 0 END AS examStarted,
                       TIMESTAMPDIFF(SECOND, NOW(),
                         CASE
                           WHEN e.duration_minutes IS NULL OR a.start_time IS NULL THEN e.end_time
                           WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                           WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                             THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                           ELSE e.end_time
                         END
                       ) AS secondsUntilDeadline
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.id = ?
                """ + userSql + " FOR UPDATE", params.toArray());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Attempt not found");
        }
        return rows.get(0);
    }

    private boolean validateSubmitWindow(Map<String, Object> attempt, boolean force) {
        if (force) {
            return true;
        }
        if (!attemptExamOpen(attempt)) {
            throw new IllegalStateException("Exam is not open for submission");
        }
        Object remainingValue = attempt.get("secondsUntilDeadline");
        if (remainingValue == null) {
            return false;
        }
        long secondsUntilDeadline = ((Number) remainingValue).longValue();
        if (secondsUntilDeadline < -SUBMIT_GRACE_SECONDS) {
            throw new IllegalStateException("Exam submission deadline has passed");
        }
        return secondsUntilDeadline <= 0;
    }

    private boolean attemptExamOpen(Map<String, Object> attempt) {
        return ((Number) attempt.get("examDeleted")).intValue() == 0
                && ((Number) attempt.get("examStatus")).intValue() == EXAM_STATUS_PUBLISHED;
    }

    private Map<Long, String> loadDraftAnswerMap(JdbcTemplate jt, Long attemptId) {
        String rawDraft = stringValue(loadBestDraftState(jt, attemptId).get("answers"));
        if (rawDraft == null || rawDraft.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(rawDraft, new TypeReference<>() {});
            Map<Long, String> result = new HashMap<>();
            Set<Long> expectedQuestionIds = expectedQuestionIdsForAttempt(jt, attemptId);
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                Long questionId;
                try {
                    questionId = Long.valueOf(entry.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (expectedQuestionIds.contains(questionId) && isRecoverableDraftAnswerValue(entry.getValue())) {
                    result.put(questionId, normalizeDraftAnswer(entry.getValue()));
                }
            }
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> attemptRecoveryState(JdbcTemplate jt, Long attemptId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> attempts = jt.queryForList("""
                SELECT rules_confirmed_at AS rulesConfirmedAt,
                       last_heartbeat_at AS lastHeartbeatAt, last_draft_saved_at AS lastDraftSavedAt,
                       draft_version AS draftVersion, submit_token AS submitToken,
                       submit_payload_hash AS submitPayloadHash
                FROM exam_attempt
                WHERE id = ?
                """, attemptId);
        if (!attempts.isEmpty()) {
            result.putAll(attempts.get(0));
        }
        Map<String, Object> draft = loadBestDraftState(jt, attemptId);
        result.put("draftRevision", draft.getOrDefault("revision", 0));
        result.put("draftClientDraftId", draft.get("clientDraftId"));
        result.put("draftSavedAt", draft.get("updatedAt"));
        result.put("draftSavedCount", draft.getOrDefault("savedCount", 0));
        result.put("draftSource", draft.getOrDefault("source", "DB"));
        return result;
    }

    private Map<String, Object> loadDraftState(JdbcTemplate jt, Long attemptId) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT answers, client_draft_id AS clientDraftId, revision, saved_count AS savedCount,
                       updated_at AS updatedAt
                FROM exam_answer_draft
                WHERE attempt_id = ?
                """, attemptId);
        if (rows.isEmpty()) {
            return Map.of("revision", 0, "savedCount", 0, "source", "DB");
        }
        Map<String, Object> result = new HashMap<>(rows.get(0));
        result.put("source", "DB");
        return result;
    }

    private Map<String, Object> loadBestDraftState(JdbcTemplate jt, Long attemptId) {
        Map<String, Object> dbDraft = loadDraftState(jt, attemptId);
        Map<String, Object> cacheDraft = examDraftCacheService.get(attemptId);
        if (cacheDraft.isEmpty()) {
            return dbDraft;
        }
        long cacheRevision = longValue(cacheDraft.get("revision"), 0L);
        long dbRevision = longValue(dbDraft.get("revision"), 0L);
        if (cacheRevision < dbRevision) {
            return dbDraft;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("answers", cacheDraft.get("answers"));
        result.put("clientDraftId", cacheDraft.get("clientDraftId"));
        result.put("revision", cacheRevision);
        result.put("savedCount", dbDraft.getOrDefault("savedCount", 0));
        result.put("updatedAt", cacheDraft.getOrDefault("savedAt", dbDraft.get("updatedAt")));
        result.put("source", "REDIS");
        result.put("cachedAt", cacheDraft.get("cachedAt"));
        return result;
    }

    private boolean flushRedisDraftForAttempt(JdbcTemplate jt, Long attemptId) {
        if (!examDraftCacheService.available()) {
            return false;
        }
        Map<String, Object> cacheDraft = examDraftCacheService.get(attemptId);
        if (!Boolean.TRUE.equals(cacheDraft.get("dirty"))) {
            return false;
        }
        String answers = stringValue(cacheDraft.get("answers"));
        String safeAnswers;
        String clientDraftId;
        try {
            safeAnswers = validateDraftAnswersJson(jt, attemptId, answers);
            clientDraftId = normalizeClientDraftId(stringValue(cacheDraft.get("clientDraftId")));
        } catch (IllegalArgumentException ex) {
            examDraftCacheService.delete(attemptId);
            examDraftCacheService.markFlushSkipped();
            examDraftCacheService.recordFlushRun(1, 0, 1, 0);
            return false;
        }
        long revision = longValue(cacheDraft.get("revision"), 0L);
        Map<String, Object> dbDraft = loadDraftState(jt, attemptId);
        long dbRevision = longValue(dbDraft.get("revision"), 0L);
        if (revision < dbRevision) {
            examDraftCacheService.markFlushSkipped();
            examDraftCacheService.recordFlushRun(1, 0, 1, 0);
            return false;
        }
        jt.update("""
                INSERT INTO exam_answer_draft (attempt_id, answers, client_draft_id, revision, saved_count)
                VALUES (?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                  answers = IF(VALUES(revision) >= revision, VALUES(answers), answers),
                  client_draft_id = IF(VALUES(revision) >= revision, VALUES(client_draft_id), client_draft_id),
                  saved_count = IF(VALUES(revision) >= revision, saved_count + 1, saved_count),
                  updated_at = IF(VALUES(revision) >= revision, CURRENT_TIMESTAMP, updated_at),
                  revision = IF(VALUES(revision) >= revision, VALUES(revision), revision)
                """, attemptId, safeAnswers, clientDraftId, revision);
        jt.update("""
                UPDATE exam_attempt
                SET last_draft_saved_at = NOW(), draft_version = ?
                WHERE id = ?
                """, revision, attemptId);
        Map<String, Object> after = loadDraftState(jt, attemptId);
        examDraftCacheService.markClean(attemptId, safeAnswers, clientDraftId, revision, after.get("updatedAt"));
        examDraftCacheService.recordFlushRun(1, 1, 0, 0);
        return true;
    }

    private Map<String, Object> submittedAttemptResult(JdbcTemplate jt, Map<String, Object> attempt, boolean alreadySubmitted) {
        return submittedAttemptResult(jt, attempt, alreadySubmitted, null);
    }

    private Map<String, Object> submittedAttemptResult(JdbcTemplate jt, Map<String, Object> attempt,
                                                       boolean alreadySubmitted, String requestSubmitToken) {
        return submittedAttemptResult(jt, attempt, alreadySubmitted, requestSubmitToken, null);
    }

    private Map<String, Object> submittedAttemptResult(JdbcTemplate jt, Map<String, Object> attempt,
                                                       boolean alreadySubmitted, String requestSubmitToken,
                                                       String requestPayloadHash) {
        Long attemptId = ((Number) attempt.get("id")).longValue();
        Long examId = ((Number) attempt.get("exam_id")).longValue();
        Long userId = ((Number) attempt.get("user_id")).longValue();
        cleanupFinalizedAttemptState(jt, attemptId, examId, userId);
        String normalizedRequestToken = normalizeSubmitToken(requestSubmitToken);
        Map<String, Object> replayed = loadSubmitResponse(jt, attemptId, normalizedRequestToken);
        if (!replayed.isEmpty()) {
            replayed.put("alreadySubmitted", alreadySubmitted);
            replayed.put("responseReplayed", true);
            replayed.put("message", alreadySubmitted ? "Already submitted" : replayed.getOrDefault("message", "Submitted"));
            appendSubmittedAnswerStats(jt, attemptId, examId, replayed);
            appendForcedSubmitFlags(replayed);
            appendSubmitPayloadMismatch(replayed, requestPayloadHash);
            return replayed;
        }
        Map<String, Object> current = jt.queryForMap("""
                SELECT id, status, submit_type AS submitType, submit_token AS submitToken,
                       submit_payload_hash AS submitPayloadHash, submit_time AS submitTime
                FROM exam_attempt
                WHERE id = ?
                """, attemptId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", alreadySubmitted ? "Already submitted" : "Submitted");
        result.put("status", current.get("status"));
        result.put("submitType", current.get("submitType"));
        result.put("scoreVisible", false);
        result.put("scoreVisibility", "PENDING_RELEASE");
        result.put("submitToken", current.get("submitToken"));
        result.put("submitPayloadHash", current.get("submitPayloadHash"));
        result.put("submitTime", current.get("submitTime"));
        result.put("alreadySubmitted", alreadySubmitted);
        result.put("responseReplayed", false);
        appendSubmittedAnswerStats(jt, attemptId, examId, result);
        appendForcedSubmitFlags(result);
        if (normalizedRequestToken != null && current.get("submitToken") != null
                && !normalizedRequestToken.equals(String.valueOf(current.get("submitToken")))) {
            result.put("submitTokenMismatch", true);
        }
        appendSubmitPayloadMismatch(result, requestPayloadHash);
        return result;
    }

    private void appendSubmitPayloadMismatch(Map<String, Object> result, String requestPayloadHash) {
        Object storedPayloadHash = result.get("submitPayloadHash");
        if (requestPayloadHash != null && storedPayloadHash != null
                && !requestPayloadHash.equals(String.valueOf(storedPayloadHash))) {
            result.put("submitPayloadMismatch", true);
        }
    }

    private void appendSubmittedAnswerStats(JdbcTemplate jt, Long attemptId, Long examId, Map<String, Object> result) {
        Map<String, Object> stats = jt.queryForMap("""
                SELECT COUNT(*) AS questionCount,
                       COALESCE(SUM(CASE
                         WHEN ar.answer_content IS NOT NULL AND TRIM(ar.answer_content) <> '' THEN 1
                         ELSE 0
                       END), 0) AS answeredCount
                FROM (
                  SELECT DISTINCT eqs.question_id
                  FROM exam_question_snapshot eqs
                  WHERE eqs.exam_id = ?
                  UNION ALL
                  SELECT DISTINCT pq.question_id
                  FROM exam e
                  JOIN paper_question pq ON pq.paper_id = e.paper_id
                  WHERE e.id = ?
                    AND NOT EXISTS (
                      SELECT 1
                      FROM exam_question_snapshot snapshot_check
                      WHERE snapshot_check.exam_id = e.id
                    )
                ) expected
                LEFT JOIN answer_record ar ON ar.attempt_id = ? AND ar.question_id = expected.question_id
                """, examId, examId, attemptId);
        int questionCount = (int) longValue(stats.get("questionCount"), 0L);
        int answeredCount = (int) longValue(stats.get("answeredCount"), 0L);
        result.put("questionCount", questionCount);
        result.put("answeredCount", answeredCount);
        result.put("unansweredCount", Math.max(0, questionCount - answeredCount));
    }

    private void cleanupFinalizedAttemptState(JdbcTemplate jt, Long attemptId, Long examId, Long userId) {
        jt.update("DELETE FROM exam_answer_draft WHERE attempt_id = ?", attemptId);
        examDraftCacheService.delete(attemptId);
        markMonitorSessionSubmitted(jt, attemptId, examId, userId);
    }

    private Map<String, Object> loadSubmitResponse(JdbcTemplate jt, Long attemptId, String requestSubmitToken) {
        try {
            List<Map<String, Object>> rows;
            if (requestSubmitToken == null) {
                rows = jt.queryForList("""
                        SELECT response_json
                        FROM exam_submit_response
                        WHERE attempt_id = ?
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """, attemptId);
            } else {
                rows = jt.queryForList("""
                        SELECT response_json
                        FROM exam_submit_response
                        WHERE attempt_id = ? AND submit_token = ?
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """, attemptId, requestSubmitToken);
            }
            if (rows.isEmpty()) {
                return Map.of();
            }
            String raw = stringValue(rows.get(0).get("response_json"));
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            return redactSubmitScore(new HashMap<>(OBJECT_MAPPER.readValue(raw, new TypeReference<>() {})));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void storeSubmitResponse(JdbcTemplate jt, Long attemptId, String submitToken,
                                     String payloadHash, Map<String, Object> response) {
        try {
            String responseJson = OBJECT_MAPPER.writeValueAsString(redactSubmitScore(response));
            jt.update("""
                    INSERT INTO exam_submit_response (attempt_id, submit_token, submit_payload_hash, response_json)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      submit_token = COALESCE(VALUES(submit_token), submit_token),
                      submit_payload_hash = VALUES(submit_payload_hash),
                      response_json = VALUES(response_json),
                      updated_at = CURRENT_TIMESTAMP
                    """, attemptId, submitToken, payloadHash, responseJson);
        } catch (Exception ex) {
            // The attempt itself is the source of truth; response replay is a resilience enhancement.
        }
    }

    private Map<String, Object> redactSubmitScore(Map<String, Object> response) {
        Map<String, Object> redacted = new HashMap<>(response);
        redacted.remove("score");
        redacted.put("scoreVisible", false);
        redacted.put("scoreVisibility", "PENDING_RELEASE");
        return redacted;
    }

    private String answerPayloadHash(Map<Long, String> answers, List<Map<String, Object>> paperQuestions) {
        try {
            Map<Long, String> sorted = new TreeMap<>();
            for (Map<String, Object> question : paperQuestions) {
                Long questionId = ((Number) question.get("questionId")).longValue();
                sorted.put(questionId, canonicalSubmittedAnswerForHash(answers == null ? null : answers.get(questionId)));
            }
            String payload = OBJECT_MAPPER.writeValueAsString(sorted);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return null;
        }
    }

    private String canonicalSubmittedAnswerForHash(String answer) {
        if (isBlankAnswer(answer)) {
            return "";
        }
        return answer;
    }

    private String normalizeDraftAnswer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .sorted()
                    .reduce("", String::concat);
        }
        return String.valueOf(value);
    }

    private void touchMonitorSession(JdbcTemplate jt, Long attemptId, Long examId, Long userId, String status) {
        jt.update("""
                INSERT INTO exam_monitor_session (attempt_id, exam_id, user_id, status, last_heartbeat_at)
                VALUES (?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                    exam_id = VALUES(exam_id),
                    user_id = VALUES(user_id),
                    status = CASE WHEN status = 'SUBMITTED' THEN status ELSE VALUES(status) END,
                    last_heartbeat_at = NOW(),
                    updated_at = CURRENT_TIMESTAMP
                """, attemptId, examId, userId, status);
    }

    private void markMonitorSessionSubmitted(JdbcTemplate jt, Long attemptId, Long examId, Long userId) {
        jt.update("""
                INSERT INTO exam_monitor_session (attempt_id, exam_id, user_id, status, last_heartbeat_at)
                VALUES (?, ?, ?, 'SUBMITTED', NOW())
                ON DUPLICATE KEY UPDATE
                    exam_id = VALUES(exam_id),
                    user_id = VALUES(user_id),
                    status = 'SUBMITTED',
                    last_heartbeat_at = NOW(),
                    updated_at = CURRENT_TIMESTAMP
                """, attemptId, examId, userId);
    }

    private List<Map<String, Object>> loadQuestionsForSubmit(JdbcTemplate jt, Long attemptId) {
        Long examId = jt.queryForObject("SELECT exam_id FROM exam_attempt WHERE id = ?", Long.class, attemptId);
        if (examId != null && hasQuestionSnapshot(jt, examId)) {
            return jt.queryForList("""
                    SELECT eqs.question_id AS questionId, eqs.question_type AS questionType,
                           eqs.correct_answer AS correctAnswer, eqs.score
                    FROM exam_question_snapshot eqs
                    WHERE eqs.exam_id = ?
                    ORDER BY eqs.sort_order, eqs.id
                    """, examId);
        }
        return jt.queryForList("""
                SELECT q.id AS questionId,
                       COALESCE(qv.question_type, q.question_type) AS questionType,
                       COALESCE(qv.correct_answer, q.correct_answer) AS correctAnswer,
                       pq.score
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN paper_question pq ON pq.paper_id = e.paper_id
                JOIN question q ON q.id = pq.question_id
                LEFT JOIN question_version qv ON qv.id = pq.question_version_id
                WHERE ea.id = ?
                ORDER BY pq.sort_order, pq.id
                """, attemptId);
    }

    private boolean hasQuestionSnapshot(JdbcTemplate jt, Long examId) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_question_snapshot
                WHERE exam_id = ?
                """, Integer.class, examId);
        return count != null && count > 0;
    }

    private long snapshotCount(JdbcTemplate jt, String tableName, Long examId) {
        if (!"exam_candidate_snapshot".equals(tableName) && !"exam_question_snapshot".equals(tableName)) {
            throw new IllegalArgumentException("Unsupported snapshot table");
        }
        Long count = jt.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE exam_id = ?",
                Long.class, examId);
        return count == null ? 0L : count;
    }

    private int insertCandidateSnapshotIfMissing(JdbcTemplate jt, Long examId, Long studentId,
                                                 String sourceType, Long sourceId) {
        return jt.update("""
                INSERT IGNORE INTO exam_candidate_snapshot
                    (exam_id, user_id, source_type, source_id, real_name, student_no, class_name)
                SELECT ?, u.id, ?, ?, u.real_name, sp.student_no, c.class_name
                FROM sys_user u
                LEFT JOIN student_profile sp ON sp.user_id = u.id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE u.id = ? AND u.deleted = 0 AND u.status = 1
                """, examId, sourceType, sourceId, studentId);
    }

    private void requireCandidateSnapshotAccess(JdbcTemplate jt, Long examId, Long studentId) {
        Integer snapshotCount = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_candidate_snapshot
                WHERE exam_id = ?
                """, Integer.class, examId);
        if (snapshotCount == null || snapshotCount == 0) {
            return;
        }
        Integer allowed = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_candidate_snapshot
                WHERE exam_id = ? AND user_id = ?
                """, Integer.class, examId, studentId);
        if (allowed == null || allowed == 0) {
            throw new IllegalStateException("Student is not in the published exam candidate snapshot");
        }
    }

    private int insertAttemptIfMissing(JdbcTemplate jt, Long examId, Long studentId) {
        return jt.update("""
                INSERT INTO exam_attempt (exam_id, user_id, attempt_no, status)
                VALUES (?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE id = id
                """, examId, studentId);
    }

    private void createNextAttemptIfAllowed(JdbcTemplate jt, Map<String, Object> currentAttempt) {
        Long examId = ((Number) currentAttempt.get("exam_id")).longValue();
        Long userId = ((Number) currentAttempt.get("user_id")).longValue();
        int attemptNo = currentAttempt.get("attempt_no") == null ? 1 : ((Number) currentAttempt.get("attempt_no")).intValue();
        Map<String, Object> limit = jt.queryForMap("""
                SELECT max_attempts, end_time
                FROM exam
                WHERE id = ? AND deleted = 0
                """, examId);
        int maxAttempts = limit.get("max_attempts") == null ? 1 : ((Number) limit.get("max_attempts")).intValue();
        if (attemptNo >= maxAttempts) {
            return;
        }
        Integer stillOpen = jt.queryForObject("""
                SELECT CASE WHEN end_time IS NULL OR end_time > NOW() THEN 1 ELSE 0 END
                FROM exam
                WHERE id = ? AND deleted = 0
                """, Integer.class, examId);
        if (stillOpen == null || stillOpen == 0) {
            return;
        }
        int nextNo = attemptNo + 1;
        jt.update("""
                INSERT INTO exam_attempt (exam_id, user_id, attempt_no, status)
                VALUES (?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE id = id
                """, examId, userId, nextNo);
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
        id = requirePositiveExamId(id);
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

    private boolean isApprovalReminderInCooldown(JdbcTemplate jt, int cooldownHours) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_approval_reminder_log
                WHERE status = 'SENT'
                  AND created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)
                """, Integer.class, cooldownHours);
        return count != null && count > 0;
    }

    private boolean isApprovalReminderScheduleIntervalActive(JdbcTemplate jt, int intervalMinutes) {
        Integer count = jt.queryForObject("""
                SELECT COUNT(*)
                FROM exam_approval_reminder_log
                WHERE trigger_source = 'SCHEDULE'
                  AND created_at >= DATE_SUB(NOW(), INTERVAL ? MINUTE)
                """, Integer.class, intervalMinutes);
        return count != null && count > 0;
    }

    private Map<String, Object> latestSentApprovalReminder(JdbcTemplate jt) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT id, created_at
                FROM exam_approval_reminder_log
                WHERE status = 'SENT'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Long recordApprovalReminderLog(JdbcTemplate jt, Long triggeredBy, int overdueHours, int cooldownHours,
                                           int overdueExamCount, int recipientCount, String status,
                                           String triggerSource, String nodeId, long durationMs, String message) {
        jt.update("""
                INSERT INTO exam_approval_reminder_log
                  (triggered_by, overdue_hours, cooldown_hours, overdue_exam_count, recipient_count,
                   status, trigger_source, node_id, duration_ms, message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, triggeredBy, overdueHours, cooldownHours, overdueExamCount, recipientCount,
                status, triggerSource, blankToNull(nodeId), durationMs, message);
        return lastInsertId(jt);
    }

    private Map<String, Object> approvalReminderResult(boolean sent, boolean enabled, int overdueHours,
                                                       int cooldownHours, boolean cooldownActive,
                                                       int overdueExamCount, int adminCount, String status,
                                                       String message, Object lastReminderAt,
                                                       String triggerSource, int scheduleIntervalMinutes,
                                                       String nodeId, long durationMs, Long reminderLogId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sent", sent);
        result.put("enabled", enabled);
        result.put("overdueHours", overdueHours);
        result.put("cooldownHours", cooldownHours);
        result.put("cooldownActive", cooldownActive);
        result.put("overdueExamCount", overdueExamCount);
        result.put("adminCount", adminCount);
        result.put("status", status);
        result.put("message", message);
        result.put("lastReminderAt", lastReminderAt);
        result.put("triggerSource", triggerSource);
        result.put("scheduleIntervalMinutes", scheduleIntervalMinutes);
        result.put("nodeId", nodeId);
        result.put("durationMs", durationMs);
        result.put("reminderLogId", reminderLogId);
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, System.currentTimeMillis() - startedAt);
    }

    private String normalizeObjectiveAnswer(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                cleaned.append(Character.toUpperCase(ch));
            }
        }
        char[] chars = cleaned.toString().toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    private String normalizeFillBlank(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private boolean isBlankAnswer(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int configNumber(JdbcTemplate jt, String key, int fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private boolean configBoolean(JdbcTemplate jt, String key, boolean fallback) {
        String value = configValue(jt, key);
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> fallback;
        };
    }

    private String configValue(JdbcTemplate jt, String key) {
        List<String> rows = jt.queryForList("""
                SELECT config_value
                FROM system_config
                WHERE config_key = ?
                """, String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int queryInt(JdbcTemplate jt, String sql) {
        Integer value = jt.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<Long> queryIdsForParents(JdbcTemplate jt, String sqlTemplate, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jt.queryForList(sqlTemplate.formatted(placeholders(ids.size())), Long.class, ids.toArray());
    }

    private int updateByIds(JdbcTemplate jt, String sqlTemplate, List<Long> ids, Object... leadingParams) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Object> params = new ArrayList<>();
        if (leadingParams != null) {
            params.addAll(Arrays.asList(leadingParams));
        }
        params.addAll(ids);
        return jt.update(sqlTemplate.formatted(placeholders(ids.size())), params.toArray());
    }

    private String placeholders(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }

    private Long lastInsertId(JdbcTemplate jt) {
        return jt.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String firstNonBlank(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private Object[] withLeadingParams(List<Object> params, Object... leading) {
        List<Object> merged = new ArrayList<>();
        if (leading != null) {
            merged.addAll(Arrays.asList(leading));
        }
        if (params != null) {
            merged.addAll(params);
        }
        return merged.toArray();
    }

    private void validateExamWindow(Map<String, Object> exam) {
        Integer active = requireJdbcTemplate().queryForObject("""
                SELECT CASE
                         WHEN e.deleted = 0
                          AND e.status = ?
                          AND (e.start_time IS NULL OR e.start_time <= NOW())
                          AND (e.end_time IS NULL OR e.end_time > NOW())
                         THEN 1 ELSE 0 END
                FROM exam e
                WHERE e.id = ?
                """, Integer.class, EXAM_STATUS_PUBLISHED, ((Number) exam.get("examId")).longValue());
        if (active == null || active == 0) {
            throw new IllegalStateException("Exam is not open for taking");
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String scoreRevokeReason(ScoreRevokeRequest request) {
        String reason = request == null ? null : trim(request.getReason());
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Score revoke reason is required");
        }
        if (reason.length() > MAX_SCORE_REVOKE_REASON_LENGTH) {
            throw new IllegalArgumentException("Score revoke reason must be 500 characters or less");
        }
        return reason;
    }

    private String normalizeApprovalNote(String note) {
        String normalized = trim(note);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > MAX_APPROVAL_NOTE_LENGTH) {
            throw new IllegalArgumentException("Approval note must be 1000 characters or less");
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String normalizeSubmitToken(String submitToken) {
        String trimmed = trim(submitToken);
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > MAX_SUBMIT_TOKEN_LENGTH) {
            throw new IllegalArgumentException("submitToken must be at most 80 characters");
        }
        return trimmed;
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

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String studentResultLink(Long attemptId) {
        return "/student/results?attemptId=" + attemptId;
    }

    private String studentExamLink(Long attemptId) {
        return "/student/exams?attemptId=" + attemptId;
    }

    private String teacherExamLink(Long examId) {
        return "/exam-tasks?examId=" + examId;
    }

    private String approvalReminderLink(Long reminderLogId) {
        return "/exam-approvals?reminderLogId=" + reminderLogId;
    }

    private String safeExportName(String value) {
        String normalized = value == null ? "exam" : value.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            normalized = "exam";
        }
        return normalized.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
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

    private static class ExamPublishPlan {
        private final Map<String, Object> paper;
        private final List<TargetSpec> targets;
        private final Map<Long, TargetSpec> studentSources;
        private final int questionCount;
        private final BigDecimal totalScore;

        private ExamPublishPlan(Map<String, Object> paper, List<TargetSpec> targets,
                                Map<Long, TargetSpec> studentSources, int questionCount, BigDecimal totalScore) {
            this.paper = paper;
            this.targets = targets;
            this.studentSources = studentSources;
            this.questionCount = questionCount;
            this.totalScore = totalScore;
        }
    }

    private static class PublishNotificationStats {
        private final int candidateCount;
        private final int notifiedStudentCount;
        private final int notifiedAttemptCount;

        private PublishNotificationStats(int candidateCount, int notifiedStudentCount, int notifiedAttemptCount) {
            this.candidateCount = candidateCount;
            this.notifiedStudentCount = notifiedStudentCount;
            this.notifiedAttemptCount = notifiedAttemptCount;
        }
    }

    private static class SubmissionAnswerStats {
        private final int questionCount;
        private final int answeredCount;
        private final int unansweredCount;

        private SubmissionAnswerStats(int questionCount, int answeredCount, int unansweredCount) {
            this.questionCount = questionCount;
            this.answeredCount = answeredCount;
            this.unansweredCount = unansweredCount;
        }
    }

    private static class MapKeySet {
        private final Set<String> data = new LinkedHashSet<>();

        private boolean add(String key) {
            return data.add(key);
        }
    }
}
