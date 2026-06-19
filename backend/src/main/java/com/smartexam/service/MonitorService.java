package com.smartexam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.common.PageResult;
import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.monitor.CheatEventRequest;
import com.smartexam.dto.monitor.MonitorActionRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MonitorService {

    private static final int MAX_MONITOR_TEXT_LENGTH = 1000;
    private static final int MONITOR_INCIDENT_EVENT_LIMIT = 50;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MONITOR_LATE_EVENT_GRACE_SECONDS = 300;
    private static final Set<String> ALLOWED_MONITOR_EVENT_TYPES = Set.of(
            "VISIBILITY_HIDDEN",
            "WINDOW_BLUR",
            "COPY",
            "PASTE",
            "FULLSCREEN_EXIT",
            "PAGE_UNLOAD_ATTEMPT",
            "HISTORY_BACK_ATTEMPT",
            "CONTEXT_MENU",
            "NETWORK_OFFLINE",
            "NETWORK_ONLINE",
            "HEARTBEAT_FAILED"
    );

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;
    private final ExamService examService;
    private final NotificationService notificationService;

    public MonitorService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                          TeachingScopeService teachingScopeService,
                          ExamService examService,
                          NotificationService notificationService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
        this.examService = examService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void recordCheatEvent(CheatEventRequest request, AuthUser user) {
        recordCheatEvents(List.of(request), user);
    }

    @Transactional
    public Map<String, Object> recordCheatEvents(List<CheatEventRequest> requests, AuthUser user) {
        if (requests == null || requests.isEmpty()) {
            return Map.of("accepted", 0, "duplicates", 0);
        }
        if (requests.size() > 100) {
            throw new IllegalArgumentException("At most 100 monitor events can be reported at once");
        }
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<Long, Map<String, Object>> attemptCache = new HashMap<>();
        int accepted = 0;
        int duplicates = 0;
        for (CheatEventRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Monitor event cannot be null");
            }
            Long attemptId = requireAttemptId(request.getAttemptId());
            String eventType = normalizeEventType(request.getEventType());
            String clientEventId = normalizeClientEventId(request.getClientEventId());
            String extraInfo = normalizeMonitorText(request.getExtraInfo(), "extraInfo");
            Timestamp clientEventTime = requireClientEventTime(request.getClientEventTime());
            Map<String, Object> attempt = attemptCache.computeIfAbsent(attemptId,
                    cachedAttemptId -> loadStudentMonitorAttempt(jdbcTemplate, cachedAttemptId, user.getId()));
            requireMonitorEventReportableAttempt(attempt, clientEventTime);
            int riskScore = riskWeight(eventType);
            int inserted = jdbcTemplate.update("""
                    INSERT IGNORE INTO cheat_event (
                        attempt_id, exam_id, user_id, event_type, risk_score, extra_info, client_event_id, client_event_time
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    attemptId,
                    ((Number) attempt.get("exam_id")).longValue(),
                    ((Number) attempt.get("user_id")).longValue(),
                    eventType,
                    riskScore,
                    extraInfo,
                    clientEventId,
                    clientEventTime);
            if (inserted > 0) {
                accepted++;
                upsertMonitorSession(jdbcTemplate,
                        attemptId,
                        ((Number) attempt.get("exam_id")).longValue(),
                        ((Number) attempt.get("user_id")).longValue(),
                        monitorStatusForAttemptEvent(attempt, eventType),
                        eventType,
                        riskScore,
                        true);
            } else {
                duplicates++;
            }
        }
        return Map.of("accepted", accepted, "duplicates", duplicates);
    }

    public List<Map<String, Object>> getCheatEvents(Long attemptId, String eventType, String startFrom,
                                                    String startTo, Integer minRiskScore, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireAttemptAccess(jdbcTemplate, attemptId, user);
        List<Object> params = new ArrayList<>();
        params.add(attemptId);
        StringBuilder where = new StringBuilder(" WHERE attempt_id = ?");
        appendCheatEventFilters(where, params, eventType, startFrom, startTo, minRiskScore);
        return jdbcTemplate.queryForList("""
                SELECT id,
                       attempt_id AS attemptId,
                       exam_id AS examId,
                       user_id AS userId,
                       event_type AS eventType,
                       risk_score AS riskScore,
                       extra_info AS extraInfo,
                       client_event_id AS clientEventId,
                       client_event_time AS clientEventTime,
                       event_time AS eventTime
                FROM cheat_event
                """ + where + """
                ORDER BY event_time DESC, id DESC
                """, params.toArray());
    }

    public ExportFile exportCheatEvents(Long attemptId, String eventType, String startFrom, String startTo,
                                        Integer minRiskScore, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> attempt = requireAttemptAccess(jdbcTemplate, attemptId, user);
        List<Map<String, Object>> events = getCheatEvents(attemptId, eventType, startFrom, startTo, minRiskScore, user);
        List<String> headers = List.of("考试", "学生", "学号", "班级", "事件类型", "客户端事件ID",
                "客户端时间", "服务端时间", "附加信息", "事件ID", "答卷ID");
        headers = List.of("Exam", "Student", "Student No", "Class", "Event Type", "Risk Score",
                "Client Event ID", "Client Event Time", "Server Event Time", "Extra Info", "Event ID", "Attempt ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> event : events) {
            rows.add(List.of(
                    emptyIfNull(attempt.get("exam_name")),
                    emptyIfNull(attempt.get("real_name")),
                    emptyIfNull(attempt.get("student_no")),
                    emptyIfNull(attempt.get("class_name")),
                    monitorEventTypeText(stringValue(event.get("eventType"))),
                    emptyIfNull(event.get("riskScore")),
                    emptyIfNull(event.get("clientEventId")),
                    emptyIfNull(event.get("clientEventTime")),
                    emptyIfNull(event.get("eventTime")),
                    emptyIfNull(event.get("extraInfo")),
                    emptyIfNull(event.get("id")),
                    emptyIfNull(event.get("attemptId"))
            ));
        }
        String filename = safeExportName(stringValue(attempt.get("exam_name"))) + "-attempt-" + attemptId
                + "-monitor-events-" + LocalDate.now() + ".csv";
        return new ExportFile(filename, CsvExport.build(headers, rows));
    }

    public List<Map<String, Object>> listExamMonitorSessions(Long examId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        requireExamMonitorAccess(jdbcTemplate, examId, user);
        int warningThreshold = configInt(jdbcTemplate, "monitor.riskWarningThreshold", 8);
        int highThreshold = configInt(jdbcTemplate, "monitor.riskHighThreshold", 20);
        List<Object> params = new ArrayList<>();
        params.add(highThreshold);
        params.add(warningThreshold);
        params.add(warningThreshold);
        params.add(highThreshold);
        params.add(examId);
        String studentScopeSql = appendSessionStudentScope(jdbcTemplate, examId, user, params);
        return jdbcTemplate.queryForList("""
                SELECT s.id,
                       s.attempt_id AS attemptId,
                       a.exam_id AS examId,
                       a.user_id AS userId,
                       CASE
                         WHEN a.status >= 2 THEN 'SUBMITTED'
                         WHEN s.last_heartbeat_at IS NULL THEN s.status
                         WHEN s.last_heartbeat_at < DATE_SUB(NOW(), INTERVAL 90 SECOND) THEN 'OFFLINE'
                         ELSE s.status
                       END AS status,
                       s.last_heartbeat_at AS lastHeartbeatAt,
                       s.last_event_at AS lastEventAt,
                       s.event_count AS eventCount,
                       s.risk_score AS riskScore,
                       CASE
                         WHEN s.risk_score >= ? THEN 'HIGH'
                         WHEN s.risk_score >= ? THEN 'WARNING'
                         ELSE 'NORMAL'
                       END AS riskLevel,
                       (
                         SELECT ma.action_type
                         FROM exam_monitor_action ma
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionType,
                       (
                         SELECT ma.note
                         FROM exam_monitor_action ma
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionNote,
                       (
                         SELECT ma.handled_at
                         FROM exam_monitor_action ma
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestHandledAt,
                       (
                         SELECT handler.real_name
                         FROM exam_monitor_action ma
                         JOIN sys_user handler ON handler.id = ma.handled_by
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestHandlerName,
                       (
                         SELECT ma.notification_sent
                         FROM exam_monitor_action ma
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionNotificationSent,
                       (
                         SELECT ma.notification_id
                         FROM exam_monitor_action ma
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionNotificationId,
                       (
                         SELECT n.is_read
                         FROM exam_monitor_action ma
                         LEFT JOIN notification n ON n.id = ma.notification_id
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionNotificationRead,
                       (
                         SELECT n.created_at
                         FROM exam_monitor_action ma
                         LEFT JOIN notification n ON n.id = ma.notification_id
                         WHERE ma.session_id = s.id
                         ORDER BY ma.handled_at DESC, ma.id DESC
                         LIMIT 1
                       ) AS latestActionNotificationCreatedAt,
                       ? AS warningThreshold,
                       ? AS highThreshold,
                       s.last_event_type AS lastEventType,
                       a.status AS attemptStatus,
                       a.attempt_no AS attemptNo,
                       a.start_time AS startTime,
                       a.rules_confirmed_at AS rulesConfirmedAt,
                       a.submit_time AS submitTime,
                       a.submit_type AS submitType,
                       a.last_draft_saved_at AS lastDraftSavedAt,
                       a.draft_version AS draftRevision,
                       CASE
                         WHEN a.start_time IS NULL THEN e.end_time
                         WHEN e.duration_minutes IS NULL THEN e.end_time
                         WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                         WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                           THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                         ELSE e.end_time
                       END AS deadlineAt,
                       CASE
                         WHEN a.status >= 2 THEN 0
                         WHEN (
                           CASE
                             WHEN a.start_time IS NULL THEN e.end_time
                             WHEN e.duration_minutes IS NULL THEN e.end_time
                             WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                               THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             ELSE e.end_time
                           END
                         ) IS NULL THEN NULL
                         ELSE TIMESTAMPDIFF(SECOND, NOW(), (
                           CASE
                             WHEN a.start_time IS NULL THEN e.end_time
                             WHEN e.duration_minutes IS NULL THEN e.end_time
                             WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                               THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             ELSE e.end_time
                           END
                         ))
                       END AS remainingSeconds,
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
                         SELECT COUNT(DISTINCT ar.question_id)
                         FROM answer_record ar
                         JOIN exam_question_snapshot eqs_answered
                           ON eqs_answered.exam_id = e.id
                          AND eqs_answered.question_id = ar.question_id
                         WHERE ar.attempt_id = a.id
                           AND ar.answer_content IS NOT NULL
                           AND TRIM(ar.answer_content) <> ''
                       ) ELSE (
                         SELECT COUNT(DISTINCT ar.question_id)
                         FROM answer_record ar
                         JOIN paper_question pq_answered
                           ON pq_answered.paper_id = e.paper_id
                          AND pq_answered.question_id = ar.question_id
                         WHERE ar.attempt_id = a.id
                           AND ar.answer_content IS NOT NULL
                           AND TRIM(ar.answer_content) <> ''
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
                           SELECT COUNT(DISTINCT ar.question_id)
                           FROM answer_record ar
                           JOIN exam_question_snapshot eqs_answered
                             ON eqs_answered.exam_id = e.id
                            AND eqs_answered.question_id = ar.question_id
                           WHERE ar.attempt_id = a.id
                             AND ar.answer_content IS NOT NULL
                             AND TRIM(ar.answer_content) <> ''
                         ) ELSE (
                           SELECT COUNT(DISTINCT ar.question_id)
                           FROM answer_record ar
                           JOIN paper_question pq_answered
                             ON pq_answered.paper_id = e.paper_id
                            AND pq_answered.question_id = ar.question_id
                           WHERE ar.attempt_id = a.id
                             AND ar.answer_content IS NOT NULL
                             AND TRIM(ar.answer_content) <> ''
                         ) END
                       ) AS unansweredCount,
                       e.exam_name AS examName,
                       u.real_name AS realName,
                       sp.student_no AS studentNo,
                       COALESCE(ecs.class_name, c.class_name) AS className
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                JOIN exam e ON e.id = a.exam_id
                JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN exam_candidate_snapshot ecs ON ecs.exam_id = a.exam_id AND ecs.user_id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE a.exam_id = ?
                """ + studentScopeSql + """
                ORDER BY
                  CASE
                    WHEN a.status = 1 AND s.risk_score > 0 THEN 0
                    WHEN a.status = 1 THEN 1
                    ELSE 2
                  END,
                  s.risk_score DESC,
                  s.last_event_at DESC,
                  s.last_heartbeat_at DESC,
                  s.id DESC
                """, params.toArray());
    }

    public ExportFile exportExamMonitorSessions(Long examId, String sessionStatus, Integer minRiskScore,
                                                String latestNotificationStatus, String rulesConfirmationStatus,
                                                String latestActionType, AuthUser user) {
        List<Map<String, Object>> sessions = filterMonitorSessionsForExport(
                listExamMonitorSessions(examId, user), sessionStatus, minRiskScore, latestNotificationStatus,
                rulesConfirmationStatus, latestActionType);
        String examName = sessions.isEmpty()
                ? loadExamName(requireJdbcTemplate(), examId)
                : stringValue(sessions.get(0).get("examName"));
        List<String> headers = List.of("考试", "学生", "学号", "班级", "状态", "风险等级", "风险分",
                "事件数", "最后事件", "最后心跳", "最近处置", "处置人", "处置时间",
                "交卷状态", "交卷方式", "交卷时间", "答卷ID", "会话ID");
        List<List<Object>> rows = new ArrayList<>();
        headers = withLatestActionNotificationHeaders(headers);
        headers.add(13, "Rules Reminder Status");
        headers.add(21, "Rules Confirmed At");
        headers.add("Question Count");
        headers.add("Answered Count");
        headers.add("Unanswered Count");
        headers.add("Last Draft Saved At");
        headers.add("Draft Revision");
        headers.add("Deadline At");
        headers.add("Remaining Seconds");
        for (Map<String, Object> session : sessions) {
            rows.add(List.of(
                    emptyIfNull(session.get("examName")),
                    emptyIfNull(session.get("realName")),
                    emptyIfNull(session.get("studentNo")),
                    emptyIfNull(session.get("className")),
                    monitorSessionStatusText(stringValue(session.get("status"))),
                    monitorRiskLevelText(stringValue(session.get("riskLevel"))),
                    emptyIfNull(session.get("riskScore")),
                    emptyIfNull(session.get("eventCount")),
                    monitorEventTypeText(stringValue(session.get("lastEventType"))),
                    emptyIfNull(session.get("lastHeartbeatAt")),
                    monitorActionTypeText(stringValue(session.get("latestActionType"))),
                    emptyIfNull(session.get("latestHandlerName")),
                    emptyIfNull(session.get("latestHandledAt")),
                    rulesReminderStatusText(session),
                    latestActionNotificationSentText(session),
                    emptyIfNull(session.get("latestActionNotificationId")),
                    monitorNotificationReadText(session.get("latestActionNotificationRead")),
                    emptyIfNull(session.get("latestActionNotificationCreatedAt")),
                    attemptStatusText(session.get("attemptStatus")),
                    submitTypeText(stringValue(session.get("submitType"))),
                    emptyIfNull(session.get("submitTime")),
                    emptyIfNull(session.get("rulesConfirmedAt")),
                    emptyIfNull(session.get("attemptId")),
                    emptyIfNull(session.get("id")),
                    emptyIfNull(session.get("questionCount")),
                    emptyIfNull(session.get("answeredCount")),
                    emptyIfNull(session.get("unansweredCount")),
                    emptyIfNull(session.get("lastDraftSavedAt")),
                    emptyIfNull(session.get("draftRevision")),
                    emptyIfNull(session.get("deadlineAt")),
                    emptyIfNull(session.get("remainingSeconds"))
            ));
        }
        return new ExportFile(safeExportName(examName) + "-monitor-sessions-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private List<Map<String, Object>> filterMonitorSessionsForExport(List<Map<String, Object>> sessions,
                                                                      String sessionStatus,
                                                                      Integer minRiskScore,
                                                                      String latestNotificationStatus,
                                                                      String rulesConfirmationStatus,
                                                                      String latestActionType) {
        String normalizedStatus = normalizeSessionExportStatus(sessionStatus);
        int safeMinRiskScore = normalizeMinRiskScore(minRiskScore, "minRiskScore");
        String normalizedNotificationStatus = normalizeLatestNotificationStatus(latestNotificationStatus);
        String normalizedRulesConfirmationStatus = normalizeRulesConfirmationStatus(rulesConfirmationStatus);
        String normalizedLatestActionType = normalizeLatestActionType(latestActionType);
        if (normalizedStatus == null && safeMinRiskScore < 0
                && normalizedNotificationStatus == null && normalizedRulesConfirmationStatus == null
                && normalizedLatestActionType == null) {
            return sessions;
        }
        return sessions.stream()
                .filter(session -> normalizedStatus == null
                        || normalizedStatus.equals(String.valueOf(session.get("status"))))
                .filter(session -> safeMinRiskScore < 0
                        || intValue(session.get("riskScore"), 0) >= safeMinRiskScore)
                .filter(session -> normalizedNotificationStatus == null
                        || latestNotificationStatusMatches(session, normalizedNotificationStatus))
                .filter(session -> normalizedRulesConfirmationStatus == null
                        || rulesConfirmationStatusMatches(session, normalizedRulesConfirmationStatus))
                .filter(session -> normalizedLatestActionType == null
                        || normalizedLatestActionType.equals(String.valueOf(session.get("latestActionType"))))
                .toList();
    }

    private String normalizeSessionExportStatus(String sessionStatus) {
        String normalized = sessionStatus == null ? "" : sessionStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return null;
        }
        if (Set.of("ONLINE", "OFFLINE", "SUBMITTED").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported monitor session status: " + sessionStatus);
    }

    private String normalizeLatestNotificationStatus(String latestNotificationStatus) {
        String normalized = latestNotificationStatus == null ? "" : latestNotificationStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return null;
        }
        if (Set.of("SENT", "UNREAD", "READ", "NONE").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported latest notification status: " + latestNotificationStatus);
    }

    private String normalizeRulesConfirmationStatus(String rulesConfirmationStatus) {
        String normalized = rulesConfirmationStatus == null ? "" : rulesConfirmationStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return null;
        }
        if (Set.of("CONFIRMED", "MISSING").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported rules confirmation status: " + rulesConfirmationStatus);
    }

    private String normalizeLatestActionType(String latestActionType) {
        String normalized = latestActionType == null ? "" : latestActionType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "ALL".equals(normalized)) {
            return null;
        }
        if (Set.of("ACKNOWLEDGE", "WARN", "RULES_REMINDER", "FORCE_SUBMIT", "NOTE").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported latest monitor action type: " + latestActionType);
    }

    private boolean latestNotificationStatusMatches(Map<String, Object> session, String normalizedNotificationStatus) {
        boolean sent = intValue(session.get("latestActionNotificationSent"), 0) == 1;
        boolean read = intValue(session.get("latestActionNotificationRead"), 0) == 1;
        return switch (normalizedNotificationStatus) {
            case "SENT" -> sent;
            case "UNREAD" -> sent && !read;
            case "READ" -> sent && read;
            case "NONE" -> !sent;
            default -> true;
        };
    }

    private boolean rulesConfirmationStatusMatches(Map<String, Object> session, String normalizedRulesConfirmationStatus) {
        boolean confirmed = session.get("rulesConfirmedAt") != null
                && !String.valueOf(session.get("rulesConfirmedAt")).isBlank();
        return switch (normalizedRulesConfirmationStatus) {
            case "CONFIRMED" -> confirmed;
            case "MISSING" -> !confirmed;
            default -> true;
        };
    }

    private String rulesReminderStatusText(Map<String, Object> session) {
        if (!"RULES_REMINDER".equals(stringValue(session.get("latestActionType")))) {
            return "";
        }
        boolean confirmed = session.get("rulesConfirmedAt") != null
                && !String.valueOf(session.get("rulesConfirmedAt")).isBlank();
        return confirmed ? "Confirmed after reminder" : "Pending confirmation";
    }

    private String loadExamName(JdbcTemplate jdbcTemplate, Long examId) {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT exam_name
                FROM exam
                WHERE id = ? AND deleted = 0
                """, String.class, examId);
        return rows.isEmpty() ? ("exam-" + examId) : rows.get(0);
    }

    private String monitorSessionStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "ONLINE" -> "在线";
            case "OFFLINE" -> "离线";
            case "SUBMITTED" -> "已交卷";
            default -> status == null ? "" : status;
        };
    }

    private String monitorRiskLevelText(String riskLevel) {
        return switch (riskLevel == null ? "" : riskLevel) {
            case "HIGH" -> "高风险";
            case "WARNING" -> "预警";
            case "NORMAL" -> "正常";
            default -> riskLevel == null ? "" : riskLevel;
        };
    }

    private String monitorEventTypeText(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case "VISIBILITY_HIDDEN" -> "切屏";
            case "WINDOW_BLUR" -> "窗口失焦";
            case "COPY" -> "复制";
            case "PASTE" -> "粘贴";
            case "FULLSCREEN_EXIT" -> "退出全屏";
            case "PAGE_UNLOAD_ATTEMPT" -> "尝试离开页面";
            case "HISTORY_BACK_ATTEMPT" -> "尝试返回上一页";
            case "CONTEXT_MENU" -> "打开右键菜单";
            case "NETWORK_OFFLINE" -> "网络断开";
            case "NETWORK_ONLINE" -> "网络恢复";
            case "HEARTBEAT_FAILED" -> "心跳失败";
            default -> eventType == null ? "" : eventType;
        };
    }

    private String monitorActionTypeText(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case "ACKNOWLEDGE" -> "已关注";
            case "WARN" -> "提醒学生";
            case "RULES_REMINDER" -> "Rules reminder";
            case "NOTE" -> "备注";
            case "FORCE_SUBMIT" -> "强制交卷";
            default -> actionType == null ? "" : actionType;
        };
    }

    private List<String> withMonitorNotificationHeader(List<String> headers) {
        List<String> exportHeaders = new ArrayList<>(headers);
        exportHeaders.add(7, "Notification Sent");
        exportHeaders.add(8, "Notification ID");
        exportHeaders.add(9, "Notification Read");
        exportHeaders.add(10, "Notification Created At");
        return exportHeaders;
    }

    private List<String> withLatestActionNotificationHeaders(List<String> headers) {
        List<String> exportHeaders = new ArrayList<>(headers);
        exportHeaders.add(13, "Latest Notification Sent");
        exportHeaders.add(14, "Latest Notification ID");
        exportHeaders.add(15, "Latest Notification Read");
        exportHeaders.add(16, "Latest Notification Created At");
        return exportHeaders;
    }

    private String monitorNotificationSentText(Object value) {
        return intValue(value, 0) == 1 ? "YES" : "NO";
    }

    private String latestActionNotificationSentText(Map<String, Object> session) {
        if (session.get("latestActionType") == null) {
            return "";
        }
        return monitorNotificationSentText(session.get("latestActionNotificationSent"));
    }

    private String monitorNotificationReadText(Object value) {
        if (value == null) {
            return "";
        }
        return intValue(value, 0) == 1 ? "READ" : "UNREAD";
    }

    private String attemptStatusText(Object status) {
        if (status == null) {
            return "";
        }
        int value = Integer.parseInt(String.valueOf(status));
        return switch (value) {
            case 0 -> "未开始";
            case 1 -> "进行中";
            case 2 -> "待批阅";
            case 3 -> "批阅中";
            case 4 -> "已批阅";
            case 5 -> "已完成";
            default -> String.valueOf(status);
        };
    }

    private String submitTypeText(String submitType) {
        return switch (submitType == null ? "" : submitType) {
            case "MANUAL" -> "手动交卷";
            case "AUTO" -> "自动交卷";
            case "FORCED" -> "强制交卷";
            default -> submitType == null ? "" : submitType;
        };
    }

    @Transactional
    public Map<String, Object> createMonitorAction(Long sessionId, MonitorActionRequest request, AuthUser user) {
        request = requireMonitorActionRequest(request);
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> session = loadSessionForAction(jdbcTemplate, sessionId, user);
        String actionType = normalizeActionType(request.getActionType());
        validateMonitorActionState(actionType, session);
        String note = normalizeMonitorText(request.getNote(), "note");
        Map<String, Object> action = insertMonitorAction(jdbcTemplate, sessionId, session, actionType, note, user.getId());
        Long notificationId = sendMonitorActionNotification(session, actionType, note);
        boolean notificationSent = notificationId != null;
        updateMonitorActionNotification(jdbcTemplate, action, notificationId);
        action.put("notificationSent", notificationSent);
        action.put("notificationId", notificationId);
        return action;
    }

    @Transactional
    public Map<String, Object> forceSubmitMonitorSession(Long sessionId, String note, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> session = loadSessionForAction(jdbcTemplate, sessionId, user);
        Long attemptId = ((Number) session.get("attempt_id")).longValue();
        Map<String, Object> submitResult = examService.forceSubmitAttempt(attemptId, user);
        String submitType = submitResult.get("submitType") == null ? "" : String.valueOf(submitResult.get("submitType"));
        if (!"FORCED".equalsIgnoreCase(submitType)) {
            throw new IllegalStateException("FORCE_SUBMIT action requires a completed forced submission");
        }
        String actionNote = normalizeMonitorText(note, "note");
        if (actionNote == null || actionNote.isBlank()) {
            actionNote = "Forced submission from monitor session";
        }
        Map<String, Object> action = latestMonitorAction(jdbcTemplate, sessionId, "FORCE_SUBMIT");
        boolean alreadyRecorded = !action.isEmpty();
        boolean notificationSent = false;
        Long notificationId = null;
        if (!alreadyRecorded) {
            action = insertMonitorAction(jdbcTemplate, sessionId, session, "FORCE_SUBMIT", actionNote, user.getId());
            notificationId = sendMonitorActionNotification(session, "FORCE_SUBMIT", actionNote);
            notificationSent = notificationId != null;
            updateMonitorActionNotification(jdbcTemplate, action, notificationId);
            action.put("notificationSent", notificationSent);
            action.put("notificationId", notificationId);
        } else {
            notificationSent = intValue(action.get("notificationSent"), 0) == 1;
            notificationId = nullableLong(action.get("notificationId"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("attemptId", attemptId);
        result.put("sessionId", sessionId);
        result.put("submit", submitResult);
        result.put("action", action);
        result.put("actionAlreadyRecorded", alreadyRecorded);
        result.put("notificationSent", notificationSent);
        result.put("notificationId", notificationId);
        return result;
    }

    private Long sendMonitorActionNotification(Map<String, Object> session, String actionType, String note) {
        Long studentUserId = ((Number) session.get("user_id")).longValue();
        Long attemptId = ((Number) session.get("attempt_id")).longValue();
        String examName = session.get("exam_name") == null ? "current exam" : String.valueOf(session.get("exam_name"));
        String safeNote = note == null || note.isBlank() ? "" : note.trim();
        String attemptLink = monitorAttemptLink(attemptId);
        if ("RULES_REMINDER".equals(actionType)) {
            String content = safeNote.isBlank()
                    ? "Please confirm the exam rules before continuing " + examName + "."
                    : safeNote;
            return notificationService.sendAndReturnId(studentUserId, "Exam rules confirmation reminder", content,
                    "MONITOR_RULES_REMINDER", "/student/exams?notice=rules&attemptId=" + attemptId,
                    "EXAM_ATTEMPT", attemptId);
        }
        if ("WARN".equals(actionType)) {
            String content = safeNote.isBlank()
                    ? "Your teacher has sent an exam monitor reminder for " + examName + ". Please follow exam rules."
                    : safeNote;
            return notificationService.sendAndReturnId(studentUserId, "Exam monitor reminder", content, "MONITOR_WARNING",
                    attemptLink, "EXAM_ATTEMPT", attemptId);
        }
        if ("FORCE_SUBMIT".equals(actionType)) {
            String content = safeNote.isBlank()
                    ? "Your answer sheet for " + examName + " has been force-submitted by the monitor."
                    : safeNote;
            return notificationService.sendAndReturnId(studentUserId, "Exam force-submitted", content, "MONITOR_FORCE_SUBMIT",
                    attemptLink, "EXAM_ATTEMPT", attemptId);
        }
        return null;
    }

    private String monitorAttemptLink(Long attemptId) {
        return "/student/exams?attemptId=" + attemptId;
    }

    private void updateMonitorActionNotification(JdbcTemplate jdbcTemplate,
                                                 Map<String, Object> action,
                                                 Long notificationId) {
        Object actionId = action.get("id");
        if (actionId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE exam_monitor_action
                SET notification_sent = ?, notification_id = ?
                WHERE id = ?
                """, notificationId == null ? 0 : 1, notificationId, actionId);
    }

    private Map<String, Object> insertMonitorAction(JdbcTemplate jdbcTemplate,
                                                    Long sessionId,
                                                    Map<String, Object> session,
                                                    String actionType,
                                                    String note,
                                                    Long handlerId) {
        Map<String, Object> ownership = resolveMonitorActionOwnership(jdbcTemplate, sessionId, session);
        jdbcTemplate.update("""
                INSERT INTO exam_monitor_action (
                    session_id, attempt_id, exam_id, user_id, action_type, note, handled_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                ((Number) ownership.get("attempt_id")).longValue(),
                ((Number) ownership.get("exam_id")).longValue(),
                ((Number) ownership.get("user_id")).longValue(),
                actionType,
                note,
                handlerId);
        return jdbcTemplate.queryForMap("""
                SELECT ma.id,
                       ma.session_id AS sessionId,
                       ma.attempt_id AS attemptId,
                       ma.exam_id AS examId,
                       ma.user_id AS userId,
                       ma.action_type AS actionType,
                       ma.note,
                       ma.notification_sent AS notificationSent,
                       ma.notification_id AS notificationId,
                       n.is_read AS notificationRead,
                       n.created_at AS notificationCreatedAt,
                       ma.handled_by AS handledBy,
                       handler.real_name AS handlerName,
                       ma.handled_at AS handledAt
                FROM exam_monitor_action ma
                LEFT JOIN sys_user handler ON handler.id = ma.handled_by
                LEFT JOIN notification n ON n.id = ma.notification_id
                WHERE ma.id = LAST_INSERT_ID()
                """);
    }

    private Map<String, Object> resolveMonitorActionOwnership(JdbcTemplate jdbcTemplate,
                                                              Long sessionId,
                                                              Map<String, Object> fallback) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.attempt_id, a.exam_id, a.user_id
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                WHERE s.id = ?
                """, sessionId);
        return rows.isEmpty() ? fallback : rows.get(0);
    }

    private Map<String, Object> latestMonitorAction(JdbcTemplate jdbcTemplate, Long sessionId, String actionType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ma.id,
                       ma.session_id AS sessionId,
                       ma.attempt_id AS attemptId,
                       ma.exam_id AS examId,
                       ma.user_id AS userId,
                       ma.action_type AS actionType,
                       ma.note,
                       ma.notification_sent AS notificationSent,
                       ma.notification_id AS notificationId,
                       n.is_read AS notificationRead,
                       n.created_at AS notificationCreatedAt,
                       ma.handled_by AS handledBy,
                       handler.real_name AS handlerName,
                       ma.handled_at AS handledAt
                FROM exam_monitor_action ma
                LEFT JOIN sys_user handler ON handler.id = ma.handled_by
                LEFT JOIN notification n ON n.id = ma.notification_id
                WHERE ma.session_id = ? AND ma.action_type = ?
                ORDER BY ma.handled_at DESC, ma.id DESC
                LIMIT 1
                """, sessionId, actionType);
        return rows.isEmpty() ? Map.of() : new HashMap<>(rows.get(0));
    }

    public List<Map<String, Object>> listMonitorActions(Long sessionId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        loadSessionForAction(jdbcTemplate, sessionId, user);
        return jdbcTemplate.queryForList("""
                SELECT ma.id,
                       ma.session_id AS sessionId,
                       ma.attempt_id AS attemptId,
                       ma.exam_id AS examId,
                       ma.user_id AS userId,
                       ma.action_type AS actionType,
                       ma.note,
                       ma.notification_sent AS notificationSent,
                       ma.notification_id AS notificationId,
                       n.is_read AS notificationRead,
                       n.created_at AS notificationCreatedAt,
                       ma.handled_by AS handledBy,
                       handler.real_name AS handlerName,
                       ma.handled_at AS handledAt
                FROM exam_monitor_action ma
                LEFT JOIN sys_user handler ON handler.id = ma.handled_by
                LEFT JOIN notification n ON n.id = ma.notification_id
                WHERE ma.session_id = ?
                ORDER BY ma.handled_at DESC, ma.id DESC
                """, sessionId);
    }

    public Map<String, Object> getMonitorAttemptIncident(Long sessionId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> actionSession = loadSessionForAction(jdbcTemplate, sessionId, user);
        Long attemptId = ((Number) actionSession.get("attempt_id")).longValue();
        Long examId = ((Number) actionSession.get("exam_id")).longValue();
        Map<String, Object> session = monitorIncidentSessionSnapshot(jdbcTemplate, sessionId, examId, actionSession, user);
        Map<String, Object> attempt = loadIncidentAttemptEvidence(jdbcTemplate, attemptId);
        Map<String, Object> draft = loadIncidentDraft(jdbcTemplate, attemptId);
        Map<String, Object> answerStats = loadIncidentAnswerStats(jdbcTemplate, attemptId, examId);
        List<Map<String, Object>> incidentEvents = latestMonitorEvents(jdbcTemplate, attemptId, MONITOR_INCIDENT_EVENT_LIMIT);
        List<Map<String, Object>> incidentActions = listMonitorActions(sessionId, user);
        Map<String, Object> forceSubmitEvidence = forceSubmitEvidence(attempt, incidentActions);
        Map<String, Object> health = buildIncidentHealth(session, attempt, draft, answerStats, forceSubmitEvidence);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("attempt", attempt);
        result.put("health", health);
        result.put("draft", draft);
        result.put("answerStats", answerStats);
        result.put("forceSubmitEvidence", forceSubmitEvidence);
        result.put("events", incidentEvents);
        result.put("actions", incidentActions);
        result.put("eventLimit", MONITOR_INCIDENT_EVENT_LIMIT);
        return result;
    }

    private Map<String, Object> monitorIncidentSessionSnapshot(JdbcTemplate jdbcTemplate, Long sessionId, Long examId,
                                                               Map<String, Object> actionSession, AuthUser user) {
        return listExamMonitorSessions(examId, user).stream()
                .filter(row -> sameId(row.get("id"), sessionId))
                .findFirst()
                .map(LinkedHashMap::new)
                .orElseGet(() -> fallbackIncidentSessionSnapshot(sessionId, actionSession));
    }

    private Map<String, Object> fallbackIncidentSessionSnapshot(Long sessionId, Map<String, Object> session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sessionId);
        result.put("attemptId", session.get("attempt_id"));
        result.put("examId", session.get("exam_id"));
        result.put("userId", session.get("user_id"));
        result.put("examName", session.get("exam_name"));
        result.put("realName", session.get("real_name"));
        result.put("studentNo", session.get("student_no"));
        result.put("className", session.get("class_name"));
        result.put("attemptStatus", session.get("attempt_status"));
        result.put("rulesConfirmedAt", session.get("rulesConfirmedAt"));
        result.put("submitType", session.get("submit_type"));
        return result;
    }

    private Map<String, Object> loadIncidentAttemptEvidence(JdbcTemplate jdbcTemplate, Long attemptId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id AS attemptId,
                       a.exam_id AS examId,
                       a.user_id AS userId,
                       a.attempt_no AS attemptNo,
                       a.status AS attemptStatus,
                       a.start_time AS startTime,
                       a.rules_confirmed_at AS rulesConfirmedAt,
                       a.submit_time AS submitTime,
                       a.submit_type AS submitType,
                       a.submit_reason AS submitReason,
                       a.last_heartbeat_at AS lastHeartbeatAt,
                       a.last_draft_saved_at AS lastDraftSavedAt,
                       a.draft_version AS draftRevision,
                       a.submit_payload_hash AS submitPayloadHash,
                       e.exam_name AS examName,
                       e.start_time AS examStartTime,
                       e.end_time AS examEndTime,
                       e.duration_minutes AS durationMinutes,
                       CASE
                         WHEN a.start_time IS NULL THEN e.end_time
                         WHEN e.duration_minutes IS NULL THEN e.end_time
                         WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                         WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                           THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                         ELSE e.end_time
                       END AS deadlineAt,
                       CASE
                         WHEN a.status >= 2 THEN 0
                         WHEN (
                           CASE
                             WHEN a.start_time IS NULL THEN e.end_time
                             WHEN e.duration_minutes IS NULL THEN e.end_time
                             WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                               THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             ELSE e.end_time
                           END
                         ) IS NULL THEN NULL
                         ELSE TIMESTAMPDIFF(SECOND, NOW(), (
                           CASE
                             WHEN a.start_time IS NULL THEN e.end_time
                             WHEN e.duration_minutes IS NULL THEN e.end_time
                             WHEN e.end_time IS NULL THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             WHEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < e.end_time
                               THEN DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)
                             ELSE e.end_time
                           END
                         ))
                       END AS remainingSeconds
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                WHERE a.id = ?
                """, attemptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Attempt not found");
        }
        return new LinkedHashMap<>(rows.get(0));
    }

    private Map<String, Object> loadIncidentDraft(JdbcTemplate jdbcTemplate, Long attemptId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT client_draft_id AS clientDraftId,
                       revision,
                       saved_count AS savedCount,
                       updated_at AS updatedAt,
                       answers
                FROM exam_answer_draft
                WHERE attempt_id = ?
                """, attemptId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            result.put("exists", false);
            result.put("source", "NONE");
            result.put("answerKeyCount", 0);
            result.put("filledAnswerCount", 0);
            return result;
        }
        Map<String, Object> row = rows.get(0);
        result.put("exists", true);
        result.put("source", "DB");
        result.put("clientDraftId", row.get("clientDraftId"));
        result.put("revision", row.get("revision"));
        result.put("savedCount", row.get("savedCount"));
        result.put("updatedAt", row.get("updatedAt"));
        result.putAll(draftAnswerCounters(stringValue(row.get("answers"))));
        return result;
    }

    private Map<String, Object> loadIncidentAnswerStats(JdbcTemplate jdbcTemplate, Long attemptId, Long examId) {
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS questionCount,
                       COALESCE(SUM(CASE WHEN ar.id IS NOT NULL THEN 1 ELSE 0 END), 0) AS recordedCount,
                       COALESCE(SUM(CASE
                         WHEN ar.answer_content IS NOT NULL AND TRIM(ar.answer_content) <> '' THEN 1
                         ELSE 0
                       END), 0) AS answeredCount,
                       COALESCE(SUM(CASE WHEN ar.review_status = 1 THEN 1 ELSE 0 END), 0) AS reviewedCount,
                       COALESCE(SUM(CASE WHEN ar.id IS NOT NULL AND ar.review_status = 0 THEN 1 ELSE 0 END), 0) AS pendingReviewCount
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionCount", questionCount);
        result.put("recordedCount", longValue(stats.get("recordedCount"), 0L));
        result.put("answeredCount", answeredCount);
        result.put("unansweredCount", Math.max(0, questionCount - answeredCount));
        result.put("reviewedCount", longValue(stats.get("reviewedCount"), 0L));
        result.put("pendingReviewCount", longValue(stats.get("pendingReviewCount"), 0L));
        return result;
    }

    private List<Map<String, Object>> latestMonitorEvents(JdbcTemplate jdbcTemplate, Long attemptId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT id,
                       attempt_id AS attemptId,
                       exam_id AS examId,
                       user_id AS userId,
                       event_type AS eventType,
                       risk_score AS riskScore,
                       extra_info AS extraInfo,
                       client_event_id AS clientEventId,
                       client_event_time AS clientEventTime,
                       event_time AS eventTime
                FROM cheat_event
                WHERE attempt_id = ?
                ORDER BY event_time DESC, id DESC
                LIMIT ?
                """, attemptId, Math.max(1, limit));
    }

    private Map<String, Object> forceSubmitEvidence(Map<String, Object> attempt, List<Map<String, Object>> actions) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> latestForceAction = actions.stream()
                .filter(action -> "FORCE_SUBMIT".equals(stringValue(action.get("actionType"))))
                .findFirst()
                .map(LinkedHashMap::new)
                .orElse(null);
        boolean submitForced = "FORCED".equalsIgnoreCase(stringValue(attempt.get("submitType")));
        result.put("exists", submitForced || latestForceAction != null);
        result.put("submitForced", submitForced);
        result.put("submitTime", attempt.get("submitTime"));
        result.put("submitReason", attempt.get("submitReason"));
        result.put("submitPayloadHash", attempt.get("submitPayloadHash"));
        result.put("action", latestForceAction);
        return result;
    }

    private Map<String, Object> buildIncidentHealth(Map<String, Object> session,
                                                    Map<String, Object> attempt,
                                                    Map<String, Object> draft,
                                                    Map<String, Object> answerStats,
                                                    Map<String, Object> forceSubmitEvidence) {
        List<Map<String, Object>> findings = new ArrayList<>();
        int warningThreshold = intValue(session.get("warningThreshold"), 8);
        int highThreshold = intValue(session.get("highThreshold"), 20);
        int riskScore = intValue(session.get("riskScore"), 0);
        int attemptStatus = intValue(attempt.get("attemptStatus"), intValue(session.get("attemptStatus"), -1));
        String sessionStatus = stringValue(session.get("status"));

        if (riskScore >= highThreshold) {
            addIncidentFinding(findings, "HIGH", "HIGH_RISK", "Risk score has reached the high-risk threshold.");
        } else if (riskScore >= warningThreshold) {
            addIncidentFinding(findings, "WARNING", "RISK_WARNING", "Risk score has reached the warning threshold.");
        }
        if (attemptStatus == 1 && "OFFLINE".equals(sessionStatus)) {
            addIncidentFinding(findings, "HIGH", "ACTIVE_OFFLINE", "Student is offline while the attempt is still active.");
        }
        Long remainingSeconds = nullableLong(attempt.get("remainingSeconds"));
        if (attemptStatus == 1 && remainingSeconds != null) {
            if (remainingSeconds <= 0) {
                addIncidentFinding(findings, "HIGH", "DEADLINE_PASSED", "Server deadline has passed but the attempt is still active.");
            } else if (remainingSeconds <= 300) {
                addIncidentFinding(findings, "WARNING", "TIME_CRITICAL", "Attempt has five minutes or less remaining.");
            }
        }
        if (attemptStatus <= 1 && attempt.get("rulesConfirmedAt") == null) {
            addIncidentFinding(findings, "WARNING", "RULES_NOT_CONFIRMED", "Student has not confirmed the exam rules.");
        }
        if (attemptStatus == 1 && !Boolean.TRUE.equals(draft.get("exists"))) {
            addIncidentFinding(findings, "INFO", "NO_SERVER_DRAFT", "No server-side draft snapshot has been recorded yet.");
        }
        if (attemptStatus >= 2 && longValue(answerStats.get("unansweredCount"), 0L) > 0) {
            addIncidentFinding(findings, "WARNING", "SUBMITTED_WITH_UNANSWERED", "Submitted answer sheet still has unanswered questions.");
        }
        if (attemptStatus >= 2 && longValue(answerStats.get("pendingReviewCount"), 0L) > 0) {
            addIncidentFinding(findings, "INFO", "PENDING_REVIEW", "Subjective or recheck items still need review.");
        }
        if (Boolean.TRUE.equals(forceSubmitEvidence.get("exists"))) {
            addIncidentFinding(findings, "HIGH", "FORCE_SUBMITTED", "Attempt has force-submit evidence recorded.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", incidentHealthLevel(findings));
        result.put("findings", findings);
        result.put("summary", findings.isEmpty()
                ? "No active monitor incident findings."
                : findings.size() + " monitor incident finding(s) need attention.");
        return result;
    }

    private Map<String, Object> draftAnswerCounters(String answers) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answerKeyCount", 0);
        result.put("filledAnswerCount", 0);
        if (answers == null || answers.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(answers, new TypeReference<Map<String, Object>>() {});
            int filled = 0;
            for (Object value : parsed.values()) {
                if (isFilledDraftAnswer(value)) {
                    filled++;
                }
            }
            result.put("answerKeyCount", parsed.size());
            result.put("filledAnswerCount", filled);
        } catch (Exception ignored) {
            result.put("parseError", true);
        }
        return result;
    }

    private boolean isFilledDraftAnswer(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.trim().isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::isFilledDraftAnswer);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::isFilledDraftAnswer);
        }
        return true;
    }

    private void addIncidentFinding(List<Map<String, Object>> findings, String severity, String code, String message) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("severity", severity);
        finding.put("code", code);
        finding.put("message", message);
        findings.add(finding);
    }

    private String incidentHealthLevel(List<Map<String, Object>> findings) {
        boolean hasHigh = findings.stream().anyMatch(finding -> "HIGH".equals(finding.get("severity")));
        if (hasHigh) {
            return "HIGH";
        }
        boolean hasWarning = findings.stream().anyMatch(finding -> "WARNING".equals(finding.get("severity")));
        if (hasWarning) {
            return "WARNING";
        }
        boolean hasInfo = findings.stream().anyMatch(finding -> "INFO".equals(finding.get("severity")));
        return hasInfo ? "INFO" : "NORMAL";
    }

    public ExportFile exportMonitorActions(Long sessionId, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        Map<String, Object> session = loadSessionForAction(jdbcTemplate, sessionId, user);
        List<Map<String, Object>> actions = listMonitorActions(sessionId, user);
        List<String> headers = List.of("考试", "学生", "学号", "班级", "处置类型", "处置人", "处置时间",
                "处置说明", "处置ID", "会话ID", "答卷ID", "考试ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            rows.add(List.of(
                    emptyIfNull(session.get("exam_name")),
                    emptyIfNull(session.get("real_name")),
                    emptyIfNull(session.get("student_no")),
                    emptyIfNull(session.get("class_name")),
                    monitorActionTypeText(stringValue(action.get("actionType"))),
                    emptyIfNull(action.get("handlerName")),
                    emptyIfNull(action.get("handledAt")),
                    monitorNotificationSentText(action.get("notificationSent")),
                    emptyIfNull(action.get("notificationId")),
                    monitorNotificationReadText(action.get("notificationRead")),
                    emptyIfNull(action.get("notificationCreatedAt")),
                    emptyIfNull(action.get("note")),
                    emptyIfNull(action.get("id")),
                    emptyIfNull(action.get("sessionId")),
                    emptyIfNull(action.get("attemptId")),
                    emptyIfNull(action.get("examId"))
            ));
        }
        String filename = safeExportName(stringValue(session.get("exam_name"))) + "-session-" + sessionId
                + "-monitor-actions-" + LocalDate.now() + ".csv";
        return new ExportFile(filename, CsvExport.build(withMonitorNotificationHeader(headers), rows));
    }

    private void appendCheatEventFilters(StringBuilder where, List<Object> params, String eventType,
                                         String startFrom, String startTo, Integer minRiskScore) {
        String normalizedEventType = eventType == null ? null : eventType.trim().toUpperCase(Locale.ROOT);
        if (normalizedEventType != null && !normalizedEventType.isBlank() && !"ALL".equals(normalizedEventType)) {
            if (!ALLOWED_MONITOR_EVENT_TYPES.contains(normalizedEventType)) {
                throw new IllegalArgumentException("Unsupported monitor event type: " + eventType);
            }
            where.append(" AND event_type = ?");
            params.add(normalizedEventType);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND event_time >= ?");
            params.add(parseMonitorFilterTime(startFrom, "startFrom"));
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND event_time <= ?");
            params.add(parseMonitorFilterTime(startTo, "startTo"));
        }
        if (minRiskScore != null) {
            if (minRiskScore < 0) {
                throw new IllegalArgumentException("minRiskScore cannot be negative");
            }
            where.append(" AND risk_score >= ?");
            params.add(minRiskScore);
        }
    }

    public PageResult<Map<String, Object>> getOperationLogs(int page, int size, Long logId, String keyword,
                                                            String action, String target,
                                                            String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendOperationLogFilters(where, params, logId, keyword, action, target, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log l" + where,
                Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.operator_id, l.operator_name, l.action, l.target, l.detail, l.ip, l.created_at
                FROM operation_log l
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportOperationLogs(Long logId, String keyword, String action, String target,
                                          String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendOperationLogFilters(where, params, logId, keyword, action, target, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.operator_id, l.operator_name, l.action, l.target, l.detail, l.ip, l.created_at
                FROM operation_log l
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("时间", "操作人", "动作", "对象", "详情", "IP", "操作人ID", "日志ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    emptyIfNull(log.get("created_at")),
                    emptyIfNull(log.get("operator_name")),
                    emptyIfNull(log.get("action")),
                    emptyIfNull(log.get("target")),
                    emptyIfNull(log.get("detail")),
                    emptyIfNull(log.get("ip")),
                    emptyIfNull(log.get("operator_id")),
                    emptyIfNull(log.get("id"))
            ));
        }
        return new ExportFile("operation-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private void appendOperationLogFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                           String action, String target,
                                           String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (l.operator_name LIKE CONCAT('%', ?, '%')
                      OR l.action LIKE CONCAT('%', ?, '%')
                      OR l.target LIKE CONCAT('%', ?, '%')
                      OR l.detail LIKE CONCAT('%', ?, '%')
                      OR l.ip LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND l.action LIKE CONCAT('%', ?, '%')");
            params.add(action.trim());
        }
        if (target != null && !target.isBlank()) {
            where.append(" AND l.target LIKE CONCAT('%', ?, '%')");
            params.add(target.trim());
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    public PageResult<Map<String, Object>> getLoginAuditLogs(int page, int size, Long logId, String keyword,
                                                             String action, Long operatorId, Boolean success,
                                                             String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE (l.target IN ('认证', '璁よ瘉'))");
        appendLoginAuditFilters(where, params, logId, keyword, action, operatorId, success, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log l" + where,
                Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.operator_id AS operatorId, l.operator_name AS operatorName,
                       l.action, l.target, l.detail, l.ip, l.created_at AS createdAt,
                       CASE
                         WHEN UPPER(l.action) LIKE '%FAILED%' OR UPPER(l.action) LIKE '%FAILURE%' OR l.action LIKE '%失败%' THEN 0
                         ELSE 1
                       END AS success
                FROM operation_log l
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportLoginAuditLogs(Long logId, String keyword, String action, Long operatorId,
                                           Boolean success, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE (l.target IN ('认证', '璁よ瘉'))");
        appendLoginAuditFilters(where, params, logId, keyword, action, operatorId, success, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.operator_id AS operatorId, l.operator_name AS operatorName,
                       l.action, l.target, l.detail, l.ip, l.created_at AS createdAt,
                       CASE
                         WHEN UPPER(l.action) LIKE '%FAILED%' OR UPPER(l.action) LIKE '%FAILURE%' OR l.action LIKE '%失败%' THEN 0
                         ELSE 1
                       END AS success
                FROM operation_log l
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("Login Log ID", "Time", "Account/User", "Action",
                "Result", "Detail", "IP", "Operator ID", "Target");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    emptyIfNull(log.get("id")),
                    emptyIfNull(log.get("createdAt")),
                    emptyIfNull(log.get("operatorName")),
                    emptyIfNull(log.get("action")),
                    loginAuditSuccessText(log.get("success")),
                    emptyIfNull(log.get("detail")),
                    emptyIfNull(log.get("ip")),
                    emptyIfNull(log.get("operatorId")),
                    emptyIfNull(log.get("target"))
            ));
        }
        return new ExportFile("login-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private void appendLoginAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                         String action, Long operatorId, Boolean success,
                                         String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (l.operator_name LIKE CONCAT('%', ?, '%')
                      OR l.action LIKE CONCAT('%', ?, '%')
                      OR l.detail LIKE CONCAT('%', ?, '%')
                      OR l.ip LIKE CONCAT('%', ?, '%')
                      OR CAST(l.operator_id AS CHAR) LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND l.action LIKE CONCAT('%', ?, '%')");
            params.add(action.trim());
        }
        if (operatorId != null && operatorId > 0) {
            where.append(" AND l.operator_id = ?");
            params.add(operatorId);
        }
        if (success != null) {
            String failedPredicate = "(UPPER(l.action) LIKE '%FAILED%' OR UPPER(l.action) LIKE '%FAILURE%' OR l.action LIKE '%失败%')";
            where.append(success ? " AND NOT " + failedPredicate : " AND " + failedPredicate);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String loginAuditSuccessText(Object success) {
        if (success == null) {
            return "";
        }
        String value = String.valueOf(success);
        return "1".equals(value) || "true".equalsIgnoreCase(value) ? "SUCCESS" : "FAILED";
    }

    public PageResult<Map<String, Object>> getAiUsageLogs(int page, int size, String scene, Boolean success,
                                                          String keyword, String startFrom, String startTo,
                                                          AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        ensureAiUsageLogTable(jdbcTemplate);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendAiUsageLogFilters(where, params, scene, success, keyword, startFrom, startTo, user);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_usage_log l
                LEFT JOIN sys_user u ON u.id = l.user_id
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id,
                       l.user_id AS userId,
                       u.real_name AS userName,
                       l.scene,
                       l.prompt,
                       l.`response` AS response,
                       l.`success` AS success,
                       l.error_message AS errorMessage,
                       l.created_at AS createdAt
                FROM ai_usage_log l
                LEFT JOIN sys_user u ON u.id = l.user_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportAiUsageLogs(String scene, Boolean success, String keyword,
                                        String startFrom, String startTo, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        ensureAiUsageLogTable(jdbcTemplate);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendAiUsageLogFilters(where, params, scene, success, keyword, startFrom, startTo, user);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id,
                       l.user_id AS userId,
                       COALESCE(u.real_name, u.username, CAST(l.user_id AS CHAR)) AS userName,
                       l.scene,
                       l.prompt,
                       l.`response` AS response,
                       l.`success` AS success,
                       l.error_message AS errorMessage,
                       l.created_at AS createdAt
                FROM ai_usage_log l
                LEFT JOIN sys_user u ON u.id = l.user_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("时间", "用户", "场景", "结果", "提示词", "响应", "错误", "用户ID", "日志ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            rows.add(List.of(
                    emptyIfNull(log.get("createdAt")),
                    emptyIfNull(log.get("userName")),
                    aiUsageSceneText(stringValue(log.get("scene"))),
                    aiUsageSuccessText(log.get("success")),
                    emptyIfNull(log.get("prompt")),
                    emptyIfNull(log.get("response")),
                    emptyIfNull(log.get("errorMessage")),
                    emptyIfNull(log.get("userId")),
                    emptyIfNull(log.get("id"))
            ));
        }
        return new ExportFile("ai-usage-log-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private void appendAiUsageLogFilters(StringBuilder where, List<Object> params, String scene, Boolean success,
                                         String keyword, String startFrom, String startTo, AuthUser user) {
        if (user == null || !user.hasRole("ADMIN")) {
            where.append(" AND l.user_id = ?");
            params.add(user == null ? null : user.getId());
        }
        if (scene != null && !scene.isBlank()) {
            where.append(" AND l.scene = ?");
            params.add(scene.trim());
        }
        if (success != null) {
            where.append(" AND l.`success` = ?");
            params.add(Boolean.TRUE.equals(success) ? 1 : 0);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (u.real_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%')
                      OR l.scene LIKE CONCAT('%', ?, '%')
                      OR l.prompt LIKE CONCAT('%', ?, '%')
                      OR l.`response` LIKE CONCAT('%', ?, '%')
                      OR l.error_message LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String aiUsageSceneText(String scene) {
        return switch (scene == null ? "" : scene) {
            case "QUESTION_GENERATE" -> "AI 出题";
            case "QUESTION_IMPORT" -> "题目文档识别";
            case "MATERIAL_GENERATE" -> "课程材料生成";
            case "WRONG_QUESTION_EXPLAIN" -> "错题讲解";
            case "SUGGEST_REVIEW" -> "复习建议";
            default -> scene == null ? "" : scene;
        };
    }

    private String aiUsageSuccessText(Object success) {
        if (success == null) {
            return "";
        }
        String value = String.valueOf(success);
        return "1".equals(value) || "true".equalsIgnoreCase(value) ? "成功" : "失败";
    }

    public PageResult<Map<String, Object>> getScoreReleaseAuditLogs(int page, int size, Long logId, String keyword,
                                                                     String action, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendScoreReleaseAuditFilters(where, params, logId, keyword, action, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM score_release_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.exam_id AS examId, e.exam_name AS examName,
                       p.paper_name AS paperName,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.visible_attempt_count AS visibleAttemptCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM score_release_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportScoreReleaseAuditLogs(Long logId, String keyword, String action, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendScoreReleaseAuditFilters(where, params, logId, keyword, action, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.exam_id AS examId, e.exam_name AS examName,
                       p.paper_name AS paperName,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.visible_attempt_count AS visibleAttemptCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM score_release_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("时间", "动作", "考试", "试卷", "状态流转",
                "处理人", "影响答卷", "通知学生", "通知答卷", "说明/原因", "考试ID", "日志ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            List<Object> row = new ArrayList<>();
            row.add(log.get("createdAt"));
            row.add(scoreReleaseAuditActionText(stringValue(log.get("action"))));
            row.add(log.get("examName"));
            row.add(log.get("paperName"));
            row.add(scoreReleaseAuditStatusText(log.get("statusFrom")) + " -> "
                    + scoreReleaseAuditStatusText(log.get("statusTo")));
            row.add(log.get("actorName"));
            row.add(log.get("visibleAttemptCount"));
            row.add(log.get("notifiedStudentCount"));
            row.add(log.get("notifiedAttemptCount"));
            row.add(log.get("note"));
            row.add(log.get("examId"));
            row.add(log.get("id"));
            rows.add(row);
        }
        return new ExportFile("score-release-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public PageResult<Map<String, Object>> getExamApprovalAuditLogs(int page, int size, Long logId, String keyword,
                                                                     String action, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendExamApprovalAuditFilters(where, params, logId, keyword, action, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM exam_approval_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.exam_id AS examId, e.exam_name AS examName,
                       p.paper_name AS paperName,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.candidate_count AS candidateCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM exam_approval_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportExamApprovalAuditLogs(Long logId, String keyword, String action, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendExamApprovalAuditFilters(where, params, logId, keyword, action, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.exam_id AS examId, e.exam_name AS examName,
                       p.paper_name AS paperName,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.note, l.actor_id AS actorId,
                       COALESCE(u.real_name, u.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.candidate_count AS candidateCount,
                       l.notified_student_count AS notifiedStudentCount,
                       l.notified_attempt_count AS notifiedAttemptCount,
                       l.created_at AS createdAt
                FROM exam_approval_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN paper p ON p.id = e.paper_id
                LEFT JOIN sys_user u ON u.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("Time", "Action", "Exam", "Paper", "Status Transition",
                "Actor", "Candidate Count", "Notified Students", "Notified Attempts", "Note", "Exam ID", "Log ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            List<Object> row = new ArrayList<>();
            row.add(log.get("createdAt"));
            row.add(examApprovalAuditActionText(stringValue(log.get("action"))));
            row.add(log.get("examName"));
            row.add(log.get("paperName"));
            row.add(examApprovalAuditStatusText(log.get("statusFrom")) + " -> "
                    + examApprovalAuditStatusText(log.get("statusTo")));
            row.add(log.get("actorName"));
            row.add(log.get("candidateCount"));
            row.add(log.get("notifiedStudentCount"));
            row.add(log.get("notifiedAttemptCount"));
            row.add(log.get("note"));
            row.add(log.get("examId"));
            row.add(log.get("id"));
            rows.add(row);
        }
        return new ExportFile("exam-approval-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public PageResult<Map<String, Object>> getApprovalReminderAuditLogs(int page, int size, Long logId,
                                                                         String keyword, String status,
                                                                         String triggerSource,
                                                                         String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendApprovalReminderAuditFilters(where, params, logId, keyword, status, triggerSource, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM exam_approval_reminder_log l
                LEFT JOIN sys_user u ON u.id = l.triggered_by
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.triggered_by AS triggeredBy,
                       CASE WHEN l.triggered_by = 0 THEN 'System scheduler'
                            ELSE COALESCE(u.real_name, u.username, CAST(l.triggered_by AS CHAR)) END AS triggeredByName,
                       l.overdue_hours AS overdueHours, l.cooldown_hours AS cooldownHours,
                       l.overdue_exam_count AS overdueExamCount, l.recipient_count AS recipientCount,
                       l.status, l.trigger_source AS triggerSource, l.node_id AS nodeId,
                       l.duration_ms AS durationMs, l.message, l.created_at AS createdAt
                FROM exam_approval_reminder_log l
                LEFT JOIN sys_user u ON u.id = l.triggered_by
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportApprovalReminderAuditLogs(Long logId, String keyword, String status,
                                                       String triggerSource, String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendApprovalReminderAuditFilters(where, params, logId, keyword, status, triggerSource, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.triggered_by AS triggeredBy,
                       CASE WHEN l.triggered_by = 0 THEN 'System scheduler'
                            ELSE COALESCE(u.real_name, u.username, CAST(l.triggered_by AS CHAR)) END AS triggeredByName,
                       l.overdue_hours AS overdueHours, l.cooldown_hours AS cooldownHours,
                       l.overdue_exam_count AS overdueExamCount, l.recipient_count AS recipientCount,
                       l.status, l.trigger_source AS triggerSource, l.node_id AS nodeId,
                       l.duration_ms AS durationMs, l.message, l.created_at AS createdAt
                FROM exam_approval_reminder_log l
                LEFT JOIN sys_user u ON u.id = l.triggered_by
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("Reminder Log ID", "Time", "Status", "Trigger Source",
                "Triggered By", "Overdue Hours", "Cooldown Hours", "Overdue Exam Count",
                "Recipient Count", "Node ID", "Duration Ms", "Message");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            List<Object> row = new ArrayList<>();
            row.add(log.get("id"));
            row.add(log.get("createdAt"));
            row.add(log.get("status"));
            row.add(log.get("triggerSource"));
            row.add(log.get("triggeredByName"));
            row.add(log.get("overdueHours"));
            row.add(log.get("cooldownHours"));
            row.add(log.get("overdueExamCount"));
            row.add(log.get("recipientCount"));
            row.add(log.get("nodeId"));
            row.add(log.get("durationMs"));
            row.add(log.get("message"));
            rows.add(row);
        }
        return new ExportFile("approval-reminder-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public PageResult<Map<String, Object>> getScoreAppealAuditLogs(int page, int size, Long logId, String keyword,
                                                                    String action, String handlingResult,
                                                                    String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendScoreAppealAuditFilters(where, params, logId, keyword, action, handlingResult, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM score_appeal_log l
                JOIN score_appeal sa ON sa.id = l.appeal_id
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.appeal_id AS appealId, l.attempt_id AS attemptId,
                       l.exam_id AS examId, e.exam_name AS examName,
                       l.question_id AS questionId, COALESCE(eqs.stem, q.stem) AS questionStem,
                       l.user_id AS userId,
                       COALESCE(student.real_name, student.username, CAST(l.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.handling_result AS handlingResult, l.note,
                       l.actor_id AS actorId,
                       COALESCE(actor.real_name, actor.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.created_at AS createdAt
                FROM score_appeal_log l
                JOIN score_appeal sa ON sa.id = l.appeal_id
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN student_profile sp ON sp.user_id = l.user_id AND sp.deleted = 0
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportScoreAppealAuditLogs(Long logId, String keyword, String action, String handlingResult,
                                                 String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendScoreAppealAuditFilters(where, params, logId, keyword, action, handlingResult, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.appeal_id AS appealId, l.attempt_id AS attemptId,
                       l.exam_id AS examId, e.exam_name AS examName,
                       l.question_id AS questionId, COALESCE(eqs.stem, q.stem) AS questionStem,
                       l.user_id AS userId,
                       COALESCE(student.real_name, student.username, CAST(l.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo,
                       l.action, l.status_from AS statusFrom, l.status_to AS statusTo,
                       l.handling_result AS handlingResult, l.note,
                       l.actor_id AS actorId,
                       COALESCE(actor.real_name, actor.username, CAST(l.actor_id AS CHAR)) AS actorName,
                       l.created_at AS createdAt
                FROM score_appeal_log l
                JOIN score_appeal sa ON sa.id = l.appeal_id
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN student_profile sp ON sp.user_id = l.user_id AND sp.deleted = 0
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("时间", "动作", "考试", "学生", "学号", "申诉对象",
                "状态流转", "处理结果", "操作人", "说明", "申诉ID", "答卷ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            List<Object> row = new ArrayList<>();
            row.add(log.get("createdAt"));
            row.add(scoreAppealAuditActionText(stringValue(log.get("action"))));
            row.add(log.get("examName"));
            row.add(log.get("studentName"));
            row.add(log.get("studentNo"));
            row.add(log.get("questionStem") == null ? "整张试卷" : log.get("questionStem"));
            row.add(scoreAppealAuditStatusText(log.get("statusFrom")) + " -> "
                    + scoreAppealAuditStatusText(log.get("statusTo")));
            row.add(scoreAppealAuditHandlingResultText(stringValue(log.get("handlingResult"))));
            row.add(log.get("actorName"));
            row.add(log.get("note"));
            row.add(log.get("appealId"));
            row.add(log.get("attemptId"));
            rows.add(row);
        }
        return new ExportFile("score-appeal-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public PageResult<Map<String, Object>> getReviewScoreAuditLogs(int page, int size, Long logId, String keyword,
                                                                    Long examId, Long reviewerId,
                                                                    String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendReviewScoreAuditFilters(where, params, logId, keyword, examId, reviewerId, startFrom, startTo);

        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM review_score_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN student_profile sp ON sp.user_id = l.user_id AND sp.deleted = 0
                LEFT JOIN sys_user reviewer ON reviewer.id = l.reviewer_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id, l.attempt_id AS attemptId, l.answer_record_id AS answerRecordId,
                       l.question_id AS questionId, COALESCE(eqs.stem, q.stem) AS questionStem,
                       l.exam_id AS examId, e.exam_name AS examName,
                       l.user_id AS userId,
                       COALESCE(student.real_name, student.username, CAST(l.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo,
                       l.old_score AS oldScore, l.new_score AS newScore, l.max_score AS maxScore,
                       l.comment, l.reviewer_id AS reviewerId,
                       COALESCE(reviewer.real_name, reviewer.username, CAST(l.reviewer_id AS CHAR)) AS reviewerName,
                       l.created_at AS createdAt
                FROM review_score_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN student_profile sp ON sp.user_id = l.user_id AND sp.deleted = 0
                LEFT JOIN sys_user reviewer ON reviewer.id = l.reviewer_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportReviewScoreAuditLogs(Long logId, String keyword, Long examId, Long reviewerId,
                                                 String startFrom, String startTo) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE e.deleted = 0");
        appendReviewScoreAuditFilters(where, params, logId, keyword, examId, reviewerId, startFrom, startTo);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                SELECT l.id, l.attempt_id AS attemptId, l.answer_record_id AS answerRecordId,
                       l.question_id AS questionId, COALESCE(eqs.stem, q.stem) AS questionStem,
                       l.exam_id AS examId, e.exam_name AS examName,
                       l.user_id AS userId,
                       COALESCE(student.real_name, student.username, CAST(l.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo,
                       l.old_score AS oldScore, l.new_score AS newScore, l.max_score AS maxScore,
                       l.comment, l.reviewer_id AS reviewerId,
                       COALESCE(reviewer.real_name, reviewer.username, CAST(l.reviewer_id AS CHAR)) AS reviewerName,
                       l.created_at AS createdAt
                FROM review_score_log l
                JOIN exam e ON e.id = l.exam_id
                JOIN sys_user student ON student.id = l.user_id
                LEFT JOIN student_profile sp ON sp.user_id = l.user_id AND sp.deleted = 0
                LEFT JOIN sys_user reviewer ON reviewer.id = l.reviewer_id
                LEFT JOIN question q ON q.id = l.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = l.question_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());

        List<String> headers = List.of("Time", "Exam", "Student", "Student No", "Question",
                "Old Score", "New Score", "Max Score", "Reviewer", "Comment",
                "Attempt ID", "Answer Record ID", "Question ID", "Log ID");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            List<Object> row = new ArrayList<>();
            row.add(log.get("createdAt"));
            row.add(log.get("examName"));
            row.add(log.get("studentName"));
            row.add(log.get("studentNo"));
            row.add(log.get("questionStem"));
            row.add(log.get("oldScore"));
            row.add(log.get("newScore"));
            row.add(log.get("maxScore"));
            row.add(log.get("reviewerName"));
            row.add(log.get("comment"));
            row.add(log.get("attemptId"));
            row.add(log.get("answerRecordId"));
            row.add(log.get("questionId"));
            row.add(log.get("id"));
            rows.add(row);
        }
        return new ExportFile("review-score-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private void appendReviewScoreAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                               Long examId, Long reviewerId,
                                               String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (e.exam_name LIKE CONCAT('%', ?, '%')
                      OR student.real_name LIKE CONCAT('%', ?, '%')
                      OR student.username LIKE CONCAT('%', ?, '%')
                      OR sp.student_no LIKE CONCAT('%', ?, '%')
                      OR reviewer.real_name LIKE CONCAT('%', ?, '%')
                      OR reviewer.username LIKE CONCAT('%', ?, '%')
                      OR COALESCE(eqs.stem, q.stem) LIKE CONCAT('%', ?, '%')
                      OR l.comment LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (examId != null && examId > 0) {
            where.append(" AND l.exam_id = ?");
            params.add(examId);
        }
        if (reviewerId != null && reviewerId > 0) {
            where.append(" AND l.reviewer_id = ?");
            params.add(reviewerId);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private void appendScoreReleaseAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                                String action, String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (e.exam_name LIKE CONCAT('%', ?, '%')
                      OR p.paper_name LIKE CONCAT('%', ?, '%')
                      OR u.real_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String normalizedAction = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        if ("PUBLISH".equals(normalizedAction) || "REVOKE".equals(normalizedAction)) {
            where.append(" AND l.action = ?");
            params.add(normalizedAction);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private void appendExamApprovalAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                                String action, String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (e.exam_name LIKE CONCAT('%', ?, '%')
                      OR p.paper_name LIKE CONCAT('%', ?, '%')
                      OR u.real_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%')
                      OR l.note LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String normalizedAction = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        if ("SUBMIT".equals(normalizedAction)
                || "RESUBMIT".equals(normalizedAction)
                || "APPROVE".equals(normalizedAction)
                || "REJECT".equals(normalizedAction)
                || "DIRECT_PUBLISH".equals(normalizedAction)) {
            where.append(" AND l.action = ?");
            params.add(normalizedAction);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String scoreReleaseAuditActionText(String action) {
        return switch (action == null ? "" : action) {
            case "PUBLISH" -> "发布";
            case "REVOKE" -> "撤回";
            default -> action == null ? "" : action;
        };
    }

    private String scoreReleaseAuditStatusText(Object status) {
        if (status == null) {
            return "-";
        }
        int value = Integer.parseInt(String.valueOf(status));
        return switch (value) {
            case 0 -> "未发布/已撤回";
            case 1 -> "已发布";
            default -> String.valueOf(status);
        };
    }

    private String examApprovalAuditActionText(String action) {
        return switch (action == null ? "" : action) {
            case "SUBMIT" -> "Submit approval";
            case "RESUBMIT" -> "Resubmit";
            case "APPROVE" -> "Approve";
            case "REJECT" -> "Reject";
            case "DIRECT_PUBLISH" -> "Direct publish";
            default -> action == null ? "" : action;
        };
    }

    private String examApprovalAuditStatusText(Object status) {
        if (status == null) {
            return "-";
        }
        int value = Integer.parseInt(String.valueOf(status));
        return switch (value) {
            case 0 -> "Pending approval";
            case 1 -> "Published";
            case 2 -> "Closed";
            case 3 -> "Rejected";
            default -> String.valueOf(status);
        };
    }

    private void appendApprovalReminderAuditFilters(StringBuilder where, List<Object> params, Long logId,
                                                    String keyword, String status, String triggerSource,
                                                    String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (u.real_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%')
                      OR l.status LIKE CONCAT('%', ?, '%')
                      OR l.trigger_source LIKE CONCAT('%', ?, '%')
                      OR l.node_id LIKE CONCAT('%', ?, '%')
                      OR l.message LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if ("SENT".equals(normalizedStatus)
                || "SKIPPED_DISABLED".equals(normalizedStatus)
                || "SKIPPED_EMPTY".equals(normalizedStatus)
                || "SKIPPED_NO_RECIPIENT".equals(normalizedStatus)
                || "SKIPPED_COOLDOWN".equals(normalizedStatus)
                || "SKIPPED_SCHEDULE_DISABLED".equals(normalizedStatus)
                || "SKIPPED_SCHEDULE_INTERVAL".equals(normalizedStatus)) {
            where.append(" AND l.status = ?");
            params.add(normalizedStatus);
        }
        String normalizedTriggerSource = triggerSource == null ? null : triggerSource.trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(normalizedTriggerSource) || "SCHEDULE".equals(normalizedTriggerSource)) {
            where.append(" AND l.trigger_source = ?");
            params.add(normalizedTriggerSource);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private void appendScoreAppealAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                               String action, String handlingResult,
                                               String startFrom, String startTo) {
        if (logId != null && logId > 0) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (e.exam_name LIKE CONCAT('%', ?, '%')
                      OR student.real_name LIKE CONCAT('%', ?, '%')
                      OR student.username LIKE CONCAT('%', ?, '%')
                      OR actor.real_name LIKE CONCAT('%', ?, '%')
                      OR actor.username LIKE CONCAT('%', ?, '%')
                      OR COALESCE(eqs.stem, q.stem) LIKE CONCAT('%', ?, '%')
                      OR l.note LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String normalizedAction = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        if ("SUBMIT".equals(normalizedAction)
                || "REPLY".equals(normalizedAction)
                || "RECHECK_OPEN".equals(normalizedAction)
                || "CLOSE_RECHECK".equals(normalizedAction)) {
            where.append(" AND l.action = ?");
            params.add(normalizedAction);
        }
        String normalizedHandlingResult = handlingResult == null ? null : handlingResult.trim().toUpperCase(Locale.ROOT);
        if ("MAINTAINED".equals(normalizedHandlingResult)
                || "RECHECK_REQUIRED".equals(normalizedHandlingResult)
                || "ADJUSTED_OFFLINE".equals(normalizedHandlingResult)) {
            where.append(" AND l.handling_result = ?");
            params.add(normalizedHandlingResult);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String scoreAppealAuditActionText(String action) {
        return switch (action == null ? "" : action) {
            case "SUBMIT" -> "提交申诉";
            case "REPLY" -> "处理回复";
            case "RECHECK_OPEN" -> "重开复核";
            case "CLOSE_RECHECK" -> "完成复核";
            default -> action == null ? "" : action;
        };
    }

    private String scoreAppealAuditStatusText(Object status) {
        if (status == null) {
            return "-";
        }
        int value = Integer.parseInt(String.valueOf(status));
        return switch (value) {
            case 0 -> "待处理";
            case 1 -> "已回复";
            case 2 -> "已关闭";
            default -> String.valueOf(status);
        };
    }

    private String scoreAppealAuditHandlingResultText(String value) {
        return switch (value == null ? "" : value) {
            case "MAINTAINED" -> "维持原分";
            case "RECHECK_REQUIRED" -> "需要复核";
            case "ADJUSTED_OFFLINE" -> "已线下调整";
            default -> value == null ? "" : value;
        };
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object emptyIfNull(Object value) {
        return value == null ? "" : value;
    }

    private String safeExportName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "export";
        }
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Map<String, Object> loadStudentMonitorAttempt(JdbcTemplate jdbcTemplate, Long attemptId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, exam_id, user_id, status, start_time, submit_time
                FROM exam_attempt
                WHERE id = ? AND user_id = ? AND status <> 0
                """, attemptId, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Cannot record monitor event for this exam attempt");
        }
        return rows.get(0);
    }

    private void requireMonitorEventReportableAttempt(Map<String, Object> attempt, Timestamp clientEventTime) {
        int status = ((Number) attempt.get("status")).intValue();
        LocalDateTime eventTime = clientEventTime == null ? null : clientEventTime.toLocalDateTime();
        LocalDateTime startTime = localDateTimeValue(attempt.get("start_time"));
        if (status == 1) {
            if (eventTime != null) {
                requireMonitorEventAfterAttemptStart(startTime, eventTime);
                LocalDateTime latestReportableTime = LocalDateTime.now().plusSeconds(MONITOR_LATE_EVENT_GRACE_SECONDS);
                if (eventTime.isAfter(latestReportableTime)) {
                    throw new IllegalArgumentException("Active attempt monitor event time is too far in the future");
                }
            }
            return;
        }
        if (status < 2) {
            throw new IllegalArgumentException("Cannot record monitor event for this exam attempt");
        }
        if (clientEventTime == null) {
            throw new IllegalArgumentException("Submitted attempt monitor events require clientEventTime");
        }
        requireMonitorEventAfterAttemptStart(startTime, eventTime);
        LocalDateTime submitTime = localDateTimeValue(attempt.get("submit_time"));
        if (submitTime == null) {
            throw new IllegalArgumentException("Cannot record monitor event for this exam attempt");
        }
        if (eventTime.isAfter(submitTime.plusSeconds(MONITOR_LATE_EVENT_GRACE_SECONDS))) {
            throw new IllegalArgumentException("Monitor event time is outside the late reporting window");
        }
    }

    private void requireMonitorEventAfterAttemptStart(LocalDateTime startTime, LocalDateTime eventTime) {
        if (startTime != null && eventTime.isBefore(startTime.minusSeconds(60))) {
            throw new IllegalArgumentException("Monitor event time is outside the attempt window");
        }
    }

    private Map<String, Object> loadSessionForAction(JdbcTemplate jdbcTemplate, Long sessionId, AuthUser user) {
        sessionId = requireSessionId(sessionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.id,
                       s.attempt_id,
                       a.exam_id,
                       a.user_id,
                       e.created_by,
                       e.exam_name,
                       u.real_name,
                       sp.student_no,
                       COALESCE(ecs.class_name, c.class_name) AS class_name,
                       a.status AS attempt_status,
                       a.rules_confirmed_at AS rulesConfirmedAt,
                       a.submit_type
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN exam_candidate_snapshot ecs ON ecs.exam_id = a.exam_id AND ecs.user_id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE s.id = ?
                """, sessionId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Monitor session not found");
        }
        Map<String, Object> session = rows.get(0);
        Long examId = ((Number) session.get("exam_id")).longValue();
        Long studentUserId = ((Number) session.get("user_id")).longValue();
        requireExamMonitorAccess(jdbcTemplate, examId, user);
        if (user != null && (user.hasRole("ADMIN") || isExamCreator(jdbcTemplate, examId, user))) {
            return session;
        }
        if (teachingScopeService.canAccessStudent(user, studentUserId)) {
            return session;
        }
        throw new IllegalArgumentException("No permission to handle this monitor session");
    }

    private void validateMonitorActionState(String actionType, Map<String, Object> session) {
        Object statusValue = session.get("attempt_status");
        int attemptStatus = intValue(statusValue, -1);
        if ("WARN".equals(actionType)) {
            if (attemptStatus != 1) {
                throw new IllegalStateException("WARN action requires an in-progress attempt");
            }
            return;
        }
        if ("RULES_REMINDER".equals(actionType)) {
            if (attemptStatus > 1) {
                throw new IllegalStateException("RULES_REMINDER action requires an active attempt");
            }
            Object rulesConfirmedAt = session.get("rulesConfirmedAt");
            if (rulesConfirmedAt != null && !String.valueOf(rulesConfirmedAt).isBlank()) {
                throw new IllegalStateException("RULES_REMINDER action requires missing rules confirmation");
            }
            return;
        }
        if (!"FORCE_SUBMIT".equals(actionType)) {
            return;
        }
        String submitType = session.get("submit_type") == null ? "" : String.valueOf(session.get("submit_type"));
        if (attemptStatus < 2 || !"FORCED".equalsIgnoreCase(submitType)) {
            throw new IllegalStateException("FORCE_SUBMIT action requires a completed forced submission");
        }
    }

    private Map<String, Object> requireAttemptAccess(JdbcTemplate jdbcTemplate, Long attemptId, AuthUser user) {
        attemptId = requireAttemptId(attemptId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.exam_id,
                       a.user_id,
                       e.created_by,
                       e.exam_name,
                       u.real_name,
                       sp.student_no,
                       COALESCE(ecs.class_name, c.class_name) AS class_name
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN exam_candidate_snapshot ecs ON ecs.exam_id = a.exam_id AND ecs.user_id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN edu_class c ON c.id = sp.primary_class_id AND c.deleted = 0
                WHERE a.id = ?
                """, attemptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Attempt not found");
        }
        Map<String, Object> attempt = rows.get(0);
        if (user != null && user.hasRole("ADMIN")) {
            return attempt;
        }
        Object createdBy = attempt.get("created_by");
        if (createdBy != null && String.valueOf(createdBy).equals(String.valueOf(user == null ? null : user.getId()))) {
            return attempt;
        }
        Long studentUserId = ((Number) attempt.get("user_id")).longValue();
        if (teachingScopeService.canAccessStudent(user, studentUserId)) {
            return attempt;
        }
        throw new IllegalArgumentException("No permission to monitor this attempt");
    }

    private void requireExamMonitorAccess(JdbcTemplate jdbcTemplate, Long examId, AuthUser user) {
        examId = requireExamId(examId);
        if (user != null && user.hasRole("ADMIN")) {
            return;
        }
        List<Map<String, Object>> exams = jdbcTemplate.queryForList("""
                SELECT created_by
                FROM exam
                WHERE id = ? AND deleted = 0
                """, examId);
        if (exams.isEmpty()) {
            throw new IllegalArgumentException("Exam not found");
        }
        Object createdBy = exams.get(0).get("created_by");
        if (createdBy != null && String.valueOf(createdBy).equals(String.valueOf(user == null ? null : user.getId()))) {
            return;
        }
        if (canAccessExamByTeachingScope(jdbcTemplate, examId, user)) {
            return;
        }
        throw new IllegalArgumentException("No permission to monitor this exam");
    }

    private boolean canAccessExamByTeachingScope(JdbcTemplate jdbcTemplate, Long examId, AuthUser user) {
        if (user == null || !user.hasRole("TEACHER")) {
            return false;
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM exam e
                WHERE e.id = ?
                  AND e.deleted = 0
                  AND (
                """);
        params.add(examId);
        boolean hasScope = false;
        hasScope = appendTargetScope(sql, params, hasScope, "CLASS", teachingScopeService.visibleClassIds(user));
        hasScope = appendTargetScope(sql, params, hasScope, "CLASS_COURSE", teachingScopeService.visibleClassCourseIds(user));
        hasScope = appendTargetScope(sql, params, hasScope, "USER", teachingScopeService.visibleStudentUserIds(user));
        if (!hasScope) {
            return false;
        }
        sql.append(")");
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null && count > 0;
    }

    private String appendSessionStudentScope(JdbcTemplate jdbcTemplate, Long examId, AuthUser user, List<Object> params) {
        if (user == null || user.hasRole("ADMIN") || isExamCreator(jdbcTemplate, examId, user)) {
            return "";
        }
        List<Long> studentIds = teachingScopeService.visibleStudentUserIds(user);
        if (studentIds == null || studentIds.isEmpty()) {
            return " AND 1 = 0";
        }
        StringBuilder sql = new StringBuilder(" AND a.user_id IN (");
        for (int i = 0; i < studentIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(studentIds.get(i));
        }
        sql.append(")");
        return sql.toString();
    }

    private boolean isExamCreator(JdbcTemplate jdbcTemplate, Long examId, AuthUser user) {
        if (user == null) {
            return false;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT created_by
                FROM exam
                WHERE id = ? AND deleted = 0
                """, examId);
        if (rows.isEmpty()) {
            return false;
        }
        Object createdBy = rows.get(0).get("created_by");
        return createdBy != null && String.valueOf(createdBy).equals(String.valueOf(user.getId()));
    }

    private boolean appendTargetScope(StringBuilder sql, List<Object> params, boolean hasScope,
                                      String targetType, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return hasScope;
        }
        if (hasScope) {
            sql.append(" OR ");
        }
        sql.append("EXISTS (SELECT 1 FROM exam_target et WHERE et.exam_id = e.id AND et.target_type = ? AND et.target_id IN (");
        params.add(targetType);
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(ids.get(i));
        }
        sql.append("))");
        return true;
    }

    private String normalizeActionType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "WARN", "RULES_REMINDER", "ACKNOWLEDGE", "FORCE_SUBMIT", "NOTE" -> type;
            default -> throw new IllegalArgumentException("Unsupported monitor action type: " + value);
        };
    }

    private MonitorActionRequest requireMonitorActionRequest(MonitorActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Monitor action request is required");
        }
        return request;
    }

    private Long requireAttemptId(Long attemptId) {
        if (attemptId == null || attemptId <= 0) {
            throw new IllegalArgumentException("AttemptId is required for monitor event reporting");
        }
        return attemptId;
    }

    private Long requireExamId(Long examId) {
        if (examId == null || examId <= 0) {
            throw new IllegalArgumentException("examId must be positive");
        }
        return examId;
    }

    private Long requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }
        return sessionId;
    }

    private String normalizeEventType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (type.isBlank()) {
            throw new IllegalArgumentException("Monitor event type is required");
        }
        if (!ALLOWED_MONITOR_EVENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported monitor event type: " + value);
        }
        return type;
    }

    private String normalizeClientEventId(String value) {
        String clientEventId = value == null ? "" : value.trim();
        if (clientEventId.isBlank()) {
            throw new IllegalArgumentException("clientEventId is required for idempotent monitor reporting");
        }
        if (clientEventId.length() > 80) {
            throw new IllegalArgumentException("clientEventId must be at most 80 characters");
        }
        return clientEventId;
    }

    private String normalizeMonitorText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > MAX_MONITOR_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be at most 1000 characters");
        }
        return trimmed;
    }

    private int configInt(JdbcTemplate jdbcTemplate, String key, int defaultValue) {
        try {
            String value = jdbcTemplate.query("""
                    SELECT config_value
                    FROM system_config
                    WHERE config_key = ?
                    """, rs -> rs.next() ? rs.getString("config_value") : null, key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int normalizeMinRiskScore(Integer minRiskScore, String fieldName) {
        if (minRiskScore == null) {
            return -1;
        }
        if (minRiskScore < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return minRiskScore;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean sameId(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private Long nullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void upsertMonitorSession(JdbcTemplate jdbcTemplate, Long attemptId, Long examId, Long userId,
                                      String status, String eventType, int riskDelta, boolean eventReported) {
        jdbcTemplate.update("""
                INSERT INTO exam_monitor_session (
                    attempt_id, exam_id, user_id, status, last_heartbeat_at,
                    last_event_at, event_count, risk_score, last_event_type
                )
                VALUES (?, ?, ?, ?, NOW(), IF(? = 1, NOW(), NULL), IF(? = 1, 1, 0), ?, ?)
                ON DUPLICATE KEY UPDATE
                    exam_id = VALUES(exam_id),
                    user_id = VALUES(user_id),
                    status = CASE WHEN status = 'SUBMITTED' THEN status ELSE VALUES(status) END,
                    last_heartbeat_at = NOW(),
                    last_event_at = IF(? = 1, NOW(), last_event_at),
                    event_count = event_count + IF(? = 1, 1, 0),
                    risk_score = risk_score + ?,
                    last_event_type = IF(? = 1, ?, last_event_type),
                    updated_at = CURRENT_TIMESTAMP
                """,
                attemptId, examId, userId, status, eventReported ? 1 : 0, eventReported ? 1 : 0,
                riskDelta, eventType,
                eventReported ? 1 : 0, eventReported ? 1 : 0, riskDelta, eventReported ? 1 : 0, eventType);
    }

    private int riskWeight(String eventType) {
        String type = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "PASTE" -> 8;
            case "COPY" -> 6;
            case "FULLSCREEN_EXIT", "PAGE_UNLOAD_ATTEMPT" -> 5;
            case "NETWORK_OFFLINE", "HISTORY_BACK_ATTEMPT" -> 4;
            case "VISIBILITY_HIDDEN", "WINDOW_BLUR", "CONTEXT_MENU" -> 3;
            case "HEARTBEAT_FAILED" -> 2;
            case "NETWORK_ONLINE" -> 1;
            default -> throw new IllegalArgumentException("Unsupported monitor event type: " + eventType);
        };
    }

    private String monitorStatusForEvent(String eventType) {
        String type = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        if ("NETWORK_OFFLINE".equals(type)) {
            return "OFFLINE";
        }
        return "ONLINE";
    }

    private String monitorStatusForAttemptEvent(Map<String, Object> attempt, String eventType) {
        int status = ((Number) attempt.get("status")).intValue();
        if (status >= 2) {
            return "SUBMITTED";
        }
        return monitorStatusForEvent(eventType);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
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

    private Timestamp parseClientEventTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Instant instant = OffsetDateTime.parse(value.trim()).toInstant();
            return Timestamp.from(instant);
        } catch (Exception ignored) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(value.trim()));
            } catch (Exception ignoredAgain) {
                try {
                    return Timestamp.from(Instant.parse(value.trim()));
                } catch (Exception ignoredFinally) {
                    throw new IllegalArgumentException("clientEventTime must be ISO-8601 or local date-time");
                }
            }
        }
    }

    private Timestamp requireClientEventTime(String value) {
        Timestamp clientEventTime = parseClientEventTime(value);
        if (clientEventTime == null) {
            throw new IllegalArgumentException("clientEventTime is required for monitor event reporting");
        }
        return clientEventTime;
    }

    private Timestamp parseMonitorFilterTime(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required when filtering monitor events by time");
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(trimmed).toInstant());
        } catch (Exception ignored) {
            try {
                return Timestamp.from(Instant.parse(trimmed));
            } catch (Exception ignoredAgain) {
                try {
                    return Timestamp.valueOf(LocalDateTime.parse(trimmed.replace(' ', 'T')));
                } catch (Exception ignoredFinally) {
                    try {
                        return Timestamp.valueOf(LocalDate.parse(trimmed).atStartOfDay());
                    } catch (Exception ignoredDate) {
                        throw new IllegalArgumentException(fieldName + " must be ISO-8601 or local date-time");
                    }
                }
            }
        }
    }

    private void ensureAiUsageLogTable(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_usage_log (
                      id            BIGINT       NOT NULL AUTO_INCREMENT,
                      user_id       BIGINT       DEFAULT NULL,
                      scene         VARCHAR(64)  DEFAULT NULL,
                      prompt        TEXT         DEFAULT NULL,
                      `response`    TEXT         DEFAULT NULL,
                      `success`     TINYINT      NOT NULL DEFAULT 1,
                      error_message VARCHAR(500) DEFAULT NULL,
                      created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      KEY idx_ai_log_user (user_id),
                      KEY idx_ai_log_scene_success_time (scene, `success`, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (Exception ignored) {
            // Query-time compatibility for older databases; the following query will expose the real error.
        }
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Data source is not available");
        }
        return jdbcTemplate;
    }
}
