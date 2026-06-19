package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OverviewService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final TeachingScopeService teachingScopeService;
    private final ExamDraftCacheService examDraftCacheService;
    private final ExamService examService;

    public OverviewService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                           TeachingScopeService teachingScopeService,
                           ExamDraftCacheService examDraftCacheService,
                           ExamService examService) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.teachingScopeService = teachingScopeService;
        this.examDraftCacheService = examDraftCacheService;
        this.examService = examService;
    }

    public Map<String, Object> adminOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 用户信息
        data.put("role", user.getPrimaryRole());

        // 统计卡片
        data.put("totalStudents", queryInt(jt, "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'STUDENT' AND u.deleted = 0"));
        data.put("totalTeachers", queryInt(jt, "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'TEACHER' AND u.deleted = 0"));
        data.put("todayExams", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND DATE(start_time) = CURDATE()"));
        data.put("runningExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0
                  AND start_time <= NOW()
                  AND (end_time IS NULL OR end_time >= NOW())
                """));
        data.put("pendingReviews", queryInt(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 4"));
        data.put("pendingApprovals", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND status = 0"));
        data.put("pendingTeacherReviews", queryInt(jt, """
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                WHERE u.deleted = 0
                  AND u.status = 0
                  AND r.role_code = 'TEACHER'
                  AND tp.status = 0
                """));
        data.put("rejectedTeacherReviews", queryInt(jt, """
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id
                JOIN sys_role r ON r.id = ur.role_id
                JOIN teacher_profile tp ON tp.user_id = u.id AND tp.deleted = 0
                WHERE u.deleted = 0
                  AND u.status = 0
                  AND r.role_code = 'TEACHER'
                  AND tp.status = 2
                """));
        data.put("totalPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0"));
        data.put("totalQuestions", queryInt(jt, "SELECT COUNT(*) FROM question WHERE deleted = 0"));
        int approvalOverdueHours = configNumber(jt, "approval.slaOverdueHours", 24);
        data.put("approvalSummary", adminApprovalSummary(jt, approvalOverdueHours));
        data.put("opsCapacity", adminOpsCapacity(jt));

        // 学科分布（teacher_profile 无学科关联字段，改以各科目下的题目数量反映学科分布）
        data.put("teacherSubjects", jt.queryForList("""
                SELECT s.subject_name AS name, COUNT(q.id) AS value
                FROM edu_subject s
                LEFT JOIN question q ON q.subject_id = s.id AND q.deleted = 0
                WHERE s.deleted = 0
                GROUP BY s.id, s.subject_name
                ORDER BY value DESC
                LIMIT 8
                """));

        // 学生年级分布
        data.put("studentGrades", jt.queryForList("""
                SELECT c.grade AS name, COUNT(sp.user_id) AS value
                FROM student_profile sp
                JOIN sys_user u ON u.id = sp.user_id AND u.deleted = 0
                LEFT JOIN edu_class c ON c.id = COALESCE(sp.primary_class_id, sp.class_id)
                GROUP BY c.grade
                ORDER BY value DESC
                """));

        // 考试通过率趋势（最近7天）
        data.put("examTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date,
                       COUNT(*) AS total,
                       SUM(CASE WHEN ea.score >= COALESCE(e.pass_score, 60) THEN 1 ELSE 0 END) AS passed
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.status = 5
                  AND ea.score IS NOT NULL
                  AND ea.submit_time IS NOT NULL
                  AND ea.submit_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                GROUP BY DATE(ea.submit_time)
                ORDER BY date
                """));

        data.put("recentExams", jt.queryForList("""
                SELECT e.id, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount
                FROM exam e
                WHERE e.deleted = 0
                ORDER BY e.start_time DESC
                LIMIT 6
                """));
        data.put("pendingApprovalExams", jt.queryForList("""
                SELECT e.id, e.exam_name AS name, e.start_time AS time, e.created_at AS createdAt,
                       COALESCE(u.real_name, u.username, CAST(e.created_by AS CHAR)) AS creatorName,
                       TIMESTAMPDIFF(HOUR, e.created_at, NOW()) AS pendingHours,
                       CONCAT_WS(',',
                         CASE WHEN e.start_time <= NOW() THEN 'PAST_START' END,
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
                LEFT JOIN sys_user u ON u.id = e.created_by
                WHERE e.deleted = 0 AND e.status = 0
                ORDER BY
                  CASE WHEN e.start_time <= NOW() THEN 0 ELSE 1 END,
                  e.created_at ASC
                LIMIT 5
                """));
        data.put("actionCenter", adminActionCenter(jt, data, user));

        return data;
    }

    private Map<String, Object> adminApprovalSummary(JdbcTemplate jt, int approvalOverdueHours) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int pending = queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND status = 0");
        int overdue = queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND status = 0
                  AND TIMESTAMPDIFF(HOUR, created_at, NOW()) >= ?
                """, approvalOverdueHours);
        int startPassed = queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND status = 0
                  AND start_time IS NOT NULL AND start_time <= NOW()
                """);
        double avgApprovalHours = queryDouble(jt, """
                SELECT COALESCE(ROUND(AVG(TIMESTAMPDIFF(MINUTE, e.created_at, l.created_at)) / 60, 1), 0)
                FROM exam_approval_log l
                JOIN exam e ON e.id = l.exam_id
                WHERE l.action IN ('APPROVE', 'REJECT')
                  AND l.created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """);
        int decisions = queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_approval_log
                WHERE action IN ('APPROVE', 'REJECT')
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """);
        int rejected = queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_approval_log
                WHERE action = 'REJECT'
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """);
        double rejectionRate = decisions == 0 ? 0 : Math.round(rejected * 1000.0 / decisions) / 10.0;
        summary.put("pending", pending);
        summary.put("overdue", overdue);
        summary.put("startPassed", startPassed);
        summary.put("overdueHours", approvalOverdueHours);
        summary.put("avgApprovalHours", avgApprovalHours);
        summary.put("rejectionRate", rejectionRate);
        summary.put("decisionCount30d", decisions);
        return summary;
    }

    private Map<String, Object> adminActionCenter(JdbcTemplate jt, Map<String, Object> overviewData, AuthUser user) {
        Map<String, Object> center = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> lifecycleHealth = examService.examLifecycleHealth(null, "ACTION_REQUIRED", user, 1, 5);
        Map<String, Object> lifecycleSummary = mapValue(lifecycleHealth.get("summary"));
        List<Map<String, Object>> lifecycleRows = pageRows(lifecycleHealth.get("page"));

        int pendingTeacherReviews = intValue(overviewData.get("pendingTeacherReviews"), 0);
        int rejectedTeacherReviews = intValue(overviewData.get("rejectedTeacherReviews"), 0);
        int pendingApprovals = intValue(overviewData.get("pendingApprovals"), 0);
        int pendingReviews = intValue(overviewData.get("pendingReviews"), 0);
        int runningExams = intValue(overviewData.get("runningExams"), 0);
        Map<String, Object> approvalSummary = mapValue(overviewData.get("approvalSummary"));
        Map<String, Object> opsCapacity = mapValue(overviewData.get("opsCapacity"));
        Map<String, Object> draftCache = mapValue(opsCapacity.get("draftCache"));
        Map<String, Object> examRuntime = mapValue(opsCapacity.get("examRuntime"));
        Map<String, Object> monitorRuntime = mapValue(opsCapacity.get("monitorRuntime"));
        Map<String, Object> submitRuntime = mapValue(opsCapacity.get("submitRuntime"));
        List<Map<String, Object>> opsAlerts = mapList(opsCapacity.get("alerts"));

        for (Map<String, Object> row : lifecycleRows) {
            Long examId = nullableLong(row.get("examId"));
            items.add(adminActionItem("LIFECYCLE_HEALTH", lifecycleTitle(row),
                    stringValue(row.get("examName")),
                    stringValue(row.get("lifecycleNextAction")),
                    1,
                    lifecycleSeverity(row),
                    lifecycleTarget(row),
                    examId,
                    null,
                    null));
        }

        if (pendingTeacherReviews > 0) {
            items.add(adminActionItem("TEACHER_REVIEW", "Teacher review pending",
                    "Teacher onboarding",
                    pendingTeacherReviews + " teacher profile(s) need administrator approval",
                    pendingTeacherReviews,
                    "HIGH",
                    "/system/users?role=TEACHER&status=0&teacherStatus=0",
                    null,
                    null,
                    null));
        }
        if (rejectedTeacherReviews > 0) {
            items.add(adminActionItem("TEACHER_REJECTED", "Rejected teacher follow-up",
                    "Teacher onboarding",
                    rejectedTeacherReviews + " rejected teacher profile(s) may need re-submission tracking",
                    rejectedTeacherReviews,
                    "WARN",
                    "/system/users?role=TEACHER&status=0&teacherStatus=2",
                    null,
                    null,
                    null));
        }

        long overdueHours = longValue(approvalSummary.get("overdueHours"), 24L);
        for (Map<String, Object> row : mapList(overviewData.get("pendingApprovalExams"))) {
            Long examId = nullableLong(row.get("id"));
            long pendingHours = longValue(row.get("pendingHours"), 0L);
            String riskFlags = stringValue(row.get("riskFlags"));
            boolean startPassed = riskFlags.contains("PAST_START");
            boolean overdue = pendingHours >= overdueHours;
            String type = startPassed ? "APPROVAL_START_PASSED" : overdue ? "APPROVAL_OVERDUE" : "EXAM_APPROVAL";
            String title = startPassed ? "Approval start time passed" : overdue ? "Approval overdue" : "Exam approval pending";
            String severity = startPassed || overdue ? "HIGH" : "WARN";
            String target = examId == null ? "/exam-approvals" : "/exam-approvals?examId=" + examId;
            items.add(adminActionItem(type, title,
                    stringValue(row.get("name")),
                    stringValue(row.get("creatorName")) + " / waiting " + pendingHours + "h / "
                            + adminApprovalRiskText(riskFlags),
                    1,
                    severity,
                    target,
                    examId,
                    null,
                    null));
        }
        if (pendingApprovals > 0 && mapList(overviewData.get("pendingApprovalExams")).isEmpty()) {
            items.add(adminActionItem("EXAM_APPROVAL", "Exam approval pending",
                    "Exam approval queue",
                    pendingApprovals + " exam(s) are waiting for approval",
                    pendingApprovals,
                    "WARN",
                    "/exam-approvals",
                    null,
                    null,
                    null));
        }

        List<Map<String, Object>> reviewRows = adminPendingReviewActions(jt);
        for (Map<String, Object> row : reviewRows) {
            Long examId = nullableLong(row.get("examId"));
            items.add(adminActionItem("PENDING_REVIEW", "Review backlog",
                    stringValue(row.get("examName")),
                    longValue(row.get("pendingAttemptCount"), 0L) + " attempt(s), "
                            + longValue(row.get("pendingAnswerCount"), 0L) + " answer(s) need review",
                    longValue(row.get("pendingAttemptCount"), 0L) + longValue(row.get("pendingAnswerCount"), 0L),
                    "HIGH",
                    examId == null ? "/reviews" : "/reviews?reviewExamId=" + examId,
                    examId,
                    null,
                    null));
        }
        if (pendingReviews > 0 && reviewRows.isEmpty()) {
            items.add(adminActionItem("PENDING_REVIEW", "Review backlog",
                    "Review queue",
                    pendingReviews + " submitted attempt(s) are waiting for manual review",
                    pendingReviews,
                    "HIGH",
                    "/reviews",
                    null,
                    null,
                    null));
        }

        if (runningExams > 0) {
            items.add(adminActionItem("RUNNING_EXAM", "Running exam monitor",
                    "Live examination",
                    runningExams + " exam(s) are in the active time window",
                    runningExams,
                    "INFO",
                    "/exam-monitor",
                    null,
                    null,
                    null));
        }

        long dirtyDrafts = longValue(draftCache.get("dirtyCount"), 0L);
        long staleDbDrafts = longValue(draftCache.get("staleDbDrafts"), 0L);
        long timeoutPressure = longValue(examRuntime.get("timeoutPressure"), 0L);
        long deadlinePassedActive = longValue(examRuntime.get("deadlinePassedActiveAttempts"), 0L);
        long offlineMonitor = longValue(monitorRuntime.get("offlineActiveSessions"), 0L);
        long highRiskMonitor = longValue(monitorRuntime.get("highRiskSessions"), 0L);
        long forcedSubmitsToday = longValue(submitRuntime.get("forceSubmittedToday"), 0L);

        addAdminOpsAction(items, "DIRTY_DRAFTS", "Dirty Redis drafts", "Draft cache",
                dirtyDrafts,
                "WARN",
                "/exam-monitor",
                "DIRTY_DRAFTS");
        addAdminOpsAction(items, "STALE_DB_DRAFTS", "Stale database drafts", "Draft cache",
                staleDbDrafts,
                "WARN",
                "/exam-monitor",
                "STALE_DB_DRAFTS");
        addAdminOpsAction(items, "TIMEOUT_PRESSURE", "Attempts near timeout", "Exam runtime",
                timeoutPressure,
                "WARN",
                "/exam-monitor",
                "TIMEOUT_PRESSURE");
        addAdminOpsAction(items, "DEADLINE_PASSED_ACTIVE", "Active attempts past deadline", "Exam runtime",
                deadlinePassedActive,
                "HIGH",
                "/exam-monitor",
                "DEADLINE_PASSED_ACTIVE");
        addAdminOpsAction(items, "OFFLINE_MONITOR", "Offline monitor sessions", "Exam monitoring",
                offlineMonitor,
                "WARN",
                "/exam-monitor",
                "OFFLINE_MONITOR");
        addAdminOpsAction(items, "HIGH_RISK_MONITOR", "High-risk monitor sessions", "Exam monitoring",
                highRiskMonitor,
                "HIGH",
                "/exam-monitor",
                "HIGH_RISK_MONITOR");
        addAdminOpsAction(items, "FORCED_SUBMITS", "Forced submissions today", "Submission runtime",
                forcedSubmitsToday,
                "INFO",
                "/exam-monitor",
                "FORCED_SUBMITS_TODAY");

        for (Map<String, Object> alert : opsAlerts) {
            String code = stringValue(alert.get("code"));
            String drilldownType = adminOpsDrilldownForAlert(code);
            if (drilldownType != null && adminOpsDrilldownHasCount(drilldownType, dirtyDrafts, staleDbDrafts,
                    timeoutPressure, deadlinePassedActive, offlineMonitor, highRiskMonitor, forcedSubmitsToday)) {
                continue;
            }
            items.add(adminActionItem("OPS_ALERT", "Ops alert: " + code,
                    "Operations capacity",
                    stringValue(alert.get("message")),
                    1,
                    adminSeverity(stringValue(alert.get("level"))),
                    "/system/config",
                    null,
                    null,
                    null));
        }

        center.put("generatedAt", LocalDateTime.now());
        center.put("lifecycleActionRequiredExams", longValue(lifecycleSummary.get("actionRequired"), 0L));
        center.put("lifecycleRiskExams", longValue(lifecycleSummary.get("risk"), 0L));
        center.put("pendingTeacherReviews", pendingTeacherReviews);
        center.put("rejectedTeacherReviews", rejectedTeacherReviews);
        center.put("pendingApprovals", pendingApprovals);
        center.put("approvalOverdue", intValue(approvalSummary.get("overdue"), 0));
        center.put("approvalStartPassed", intValue(approvalSummary.get("startPassed"), 0));
        center.put("pendingReviews", pendingReviews);
        center.put("runningExams", runningExams);
        center.put("opsAlerts", opsAlerts.size());
        center.put("dirtyDrafts", dirtyDrafts);
        center.put("staleDbDrafts", staleDbDrafts);
        center.put("timeoutPressure", timeoutPressure);
        center.put("deadlinePassedActive", deadlinePassedActive);
        center.put("offlineMonitor", offlineMonitor);
        center.put("highRiskMonitor", highRiskMonitor);
        center.put("forcedSubmitsToday", forcedSubmitsToday);
        center.put("total", pendingTeacherReviews
                + pendingApprovals
                + pendingReviews
                + opsAlerts.size()
                + dirtyDrafts
                + staleDbDrafts
                + timeoutPressure
                + deadlinePassedActive
                + offlineMonitor
                + highRiskMonitor
                + forcedSubmitsToday
                + longValue(lifecycleSummary.get("actionRequired"), 0L));
        center.put("items", items);
        return center;
    }

    private List<Map<String, Object>> adminPendingReviewActions(JdbcTemplate jt) {
        return jt.queryForList("""
                SELECT e.id AS examId, e.exam_name AS examName, p.paper_name AS paperName, e.end_time AS endTime,
                       COUNT(DISTINCT CASE WHEN a.status = 4 THEN a.id END) AS pendingAttemptCount,
                       COUNT(DISTINCT ar.id) AS pendingAnswerCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN exam_attempt a ON a.exam_id = e.id
                LEFT JOIN answer_record ar ON ar.attempt_id = a.id AND ar.review_status = 0
                WHERE e.deleted = 0
                  AND (a.status = 4 OR ar.id IS NOT NULL)
                GROUP BY e.id, e.exam_name, p.paper_name, e.end_time
                ORDER BY pendingAttemptCount DESC, pendingAnswerCount DESC, e.end_time ASC
                LIMIT 5
                """);
    }

    private void addAdminOpsAction(List<Map<String, Object>> items, String type, String title, String subject,
                                   long count, String severity, String target, String drilldownType) {
        if (count <= 0) {
            return;
        }
        items.add(adminActionItem(type, title, subject,
                count + " record(s) need operations review",
                count,
                severity,
                target,
                null,
                null,
                drilldownType));
    }

    private Map<String, Object> adminActionItem(String type, String title, String subject, String detail,
                                                long count, String severity, String target, Long examId,
                                                Long appealId, String drilldownType) {
        Map<String, Object> item = actionItem(type, title, subject, detail, count, severity, target, examId, appealId);
        item.put("drilldownType", drilldownType);
        return item;
    }

    private List<Map<String, Object>> pageRows(Object page) {
        if (!(page instanceof PageResult<?> pageResult)) {
            return List.of();
        }
        return mapList(pageResult.getList());
    }

    private String lifecycleTitle(Map<String, Object> row) {
        String state = stringValue(row.get("lifecycleState"));
        return switch (state) {
            case "APPROVAL_PENDING" -> "Lifecycle: approval pending";
            case "APPROVAL_START_PASSED" -> "Lifecycle: approval risk";
            case "REJECTED" -> "Lifecycle: rejected exam";
            case "SNAPSHOT_RISK" -> "Lifecycle: snapshot risk";
            case "TIMEOUT_PRESSURE" -> "Lifecycle: timeout pressure";
            case "MONITOR_RISK" -> "Lifecycle: monitor risk";
            case "FINALIZE_REQUIRED" -> "Lifecycle: finalize attempts";
            case "REVIEW_REQUIRED" -> "Lifecycle: review required";
            case "RECHECK_REQUIRED" -> "Lifecycle: recheck required";
            case "APPEAL_REQUIRED" -> "Lifecycle: appeal required";
            case "SCORE_MISSING" -> "Lifecycle: score missing";
            case "NO_COMPLETED_ATTEMPTS" -> "Lifecycle: no completed attempts";
            case "SCORE_READY" -> "Lifecycle: scores ready";
            case "SCORE_BLOCKED" -> "Lifecycle: score blocked";
            default -> "Lifecycle: action required";
        };
    }

    private String lifecycleSeverity(Map<String, Object> row) {
        String severity = stringValue(row.get("lifecycleSeverity"));
        if ("HIGH".equals(severity)) {
            return "HIGH";
        }
        if ("WARN".equals(severity)) {
            return "WARN";
        }
        return "INFO";
    }

    private String lifecycleTarget(Map<String, Object> row) {
        Long examId = nullableLong(row.get("examId"));
        String fallback = examId == null ? "/exam-tasks" : "/exam-tasks?examId=" + examId;
        String actionType = stringValue(row.get("lifecycleNextActionType"));
        if (examId == null) {
            return fallback;
        }
        return switch (actionType) {
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

    private String adminApprovalRiskText(String riskFlags) {
        if (riskFlags == null || riskFlags.isBlank()) {
            return "preflight ok";
        }
        List<String> risks = new ArrayList<>();
        for (String flag : riskFlags.split(",")) {
            String normalized = flag == null ? "" : flag.trim();
            if (normalized.isBlank()) {
                continue;
            }
            risks.add(switch (normalized) {
                case "PAST_START" -> "start time passed";
                case "NO_TARGET" -> "no target snapshot";
                case "NO_QUESTIONS" -> "no questions";
                case "PASS_SCORE_OVER_TOTAL" -> "pass score exceeds total";
                default -> normalized;
            });
        }
        return risks.isEmpty() ? "preflight ok" : String.join(", ", risks);
    }

    private String adminOpsDrilldownForAlert(String code) {
        return switch (code) {
            case "DRAFT_CACHE" -> "DIRTY_DRAFTS";
            case "ACTIVE_ATTEMPT_CAPACITY", "MONITOR_EVENT_STORM" -> "TIMEOUT_PRESSURE";
            case "DEADLINE_PASSED_ACTIVE" -> "DEADLINE_PASSED_ACTIVE";
            case "MONITOR_OFFLINE" -> "OFFLINE_MONITOR";
            case "MONITOR_HIGH_RISK" -> "HIGH_RISK_MONITOR";
            case "FORCE_SUBMIT_TODAY" -> "FORCED_SUBMITS_TODAY";
            default -> null;
        };
    }

    private boolean adminOpsDrilldownHasCount(String drilldownType, long dirtyDrafts, long staleDbDrafts,
                                              long timeoutPressure, long deadlinePassedActive,
                                              long offlineMonitor, long highRiskMonitor,
                                              long forcedSubmitsToday) {
        return switch (drilldownType) {
            case "DIRTY_DRAFTS" -> dirtyDrafts > 0 || staleDbDrafts > 0;
            case "STALE_DB_DRAFTS" -> staleDbDrafts > 0;
            case "TIMEOUT_PRESSURE" -> timeoutPressure > 0;
            case "DEADLINE_PASSED_ACTIVE" -> deadlinePassedActive > 0;
            case "OFFLINE_MONITOR" -> offlineMonitor > 0;
            case "HIGH_RISK_MONITOR" -> highRiskMonitor > 0;
            case "FORCED_SUBMITS_TODAY" -> forcedSubmitsToday > 0;
            default -> false;
        };
    }

    private String adminSeverity(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("HIGH".equals(normalized)) {
            return "HIGH";
        }
        if ("WARN".equals(normalized)) {
            return "WARN";
        }
        return "INFO";
    }

    private Map<String, Object> adminOpsCapacity(JdbcTemplate jt) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> database = databaseHealth(jt);
        Map<String, Object> draftCache = draftCacheCapacity(jt);
        Map<String, Object> examRuntime = examRuntimeCapacity(jt);
        Map<String, Object> monitorRuntime = monitorRuntimeCapacity(jt);
        Map<String, Object> submitRuntime = submitRuntimeCapacity(jt);
        List<Map<String, Object>> alerts = opsCapacityAlerts(database, draftCache, examRuntime, monitorRuntime, submitRuntime);
        result.put("generatedAt", LocalDateTime.now());
        result.put("alertLevel", opsAlertLevel(alerts));
        result.put("alertMessage", opsAlertMessage(alerts));
        result.put("database", database);
        result.put("draftCache", draftCache);
        result.put("examRuntime", examRuntime);
        result.put("monitorRuntime", monitorRuntime);
        result.put("submitRuntime", submitRuntime);
        result.put("alerts", alerts);
        return result;
    }

    public PageResult<Map<String, Object>> adminOpsDrilldown(String type, int page, int size) {
        String normalizedType = normalizeOpsDrilldownType(type);
        if ("DIRTY_DRAFTS".equals(normalizedType)) {
            return redisDirtyDraftDrilldown(page, size);
        }
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        OpsDrilldownQuery query = opsDrilldownQuery(jt, normalizedType);
        Long total = jt.queryForObject("SELECT COUNT(*) " + query.fromWhere(), Long.class, query.params());
        List<Object> listParams = new ArrayList<>();
        for (Object param : query.params()) {
            listParams.add(param);
        }
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList(query.select() + " " + query.fromWhere()
                + " " + query.orderBy() + " LIMIT ? OFFSET ?", listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportAdminOpsDrilldown(String type) {
        String normalizedType = normalizeOpsDrilldownType(type);
        List<Map<String, Object>> rows;
        if ("DIRTY_DRAFTS".equals(normalizedType)) {
            rows = redisDirtyDraftRows(5000);
        } else {
            JdbcTemplate jt = requireJdbcTemplate();
            OpsDrilldownQuery query = opsDrilldownQuery(jt, normalizedType);
            rows = jt.queryForList(query.select() + " " + query.fromWhere() + " " + query.orderBy()
                    + " LIMIT 5000", query.params());
        }
        List<String> headers = List.of(
                "Type", "Exam ID", "Exam", "Attempt ID", "Student ID", "Student", "Student No",
                "Status", "Submit Type", "Risk Score", "Event Count", "Last Event",
                "Last Heartbeat", "Deadline", "Seconds To Deadline", "Draft Revision",
                "Draft Saved Count", "Draft Updated At", "Note"
        );
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(List.of(
                    normalizedType,
                    nullable(row.get("examId")),
                    nullable(row.get("examName")),
                    nullable(row.get("attemptId")),
                    nullable(row.get("studentUserId")),
                    nullable(row.get("studentName")),
                    nullable(row.get("studentNo")),
                    nullable(row.get("attemptStatus")),
                    nullable(row.get("submitType")),
                    nullable(row.get("riskScore")),
                    nullable(row.get("eventCount")),
                    nullable(row.get("lastEventAt")),
                    nullable(row.get("lastHeartbeatAt")),
                    nullable(row.get("serverDeadline")),
                    nullable(row.get("secondsToDeadline")),
                    nullable(row.get("draftRevision")),
                    nullable(row.get("draftSavedCount")),
                    nullable(row.get("draftUpdatedAt")),
                    nullable(row.get("note"))
            ));
        }
        return new ExportFile("ops-drilldown-" + normalizedType.toLowerCase() + "-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, data));
    }

    private String normalizeOpsDrilldownType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase();
        return switch (value) {
            case "TIMEOUT_PRESSURE", "DEADLINE_PASSED_ACTIVE", "OFFLINE_MONITOR", "HIGH_RISK_MONITOR",
                    "STALE_DB_DRAFTS", "DIRTY_DRAFTS", "FORCED_SUBMITS_TODAY" -> value;
            default -> throw new IllegalArgumentException("Unsupported ops drilldown type");
        };
    }

    private OpsDrilldownQuery opsDrilldownQuery(JdbcTemplate jt, String type) {
        String deadline = """
                COALESCE(LEAST(e.end_time, DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)),
                         e.end_time,
                         DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE))
                """;
        String select = """
                SELECT e.id AS examId, e.exam_name AS examName, e.start_time AS examStartTime,
                       e.end_time AS examEndTime, a.id AS attemptId, a.user_id AS studentUserId,
                       COALESCE(u.real_name, u.username, CAST(a.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo, a.status AS attemptStatus,
                       a.start_time AS attemptStartTime, a.submit_time AS submitTime,
                       a.submit_type AS submitType, a.submit_reason AS note,
                       s.id AS sessionId, s.status AS monitorStatus, s.risk_score AS riskScore,
                       s.event_count AS eventCount, s.last_event_type AS lastEventType,
                       s.last_event_at AS lastEventAt, s.last_heartbeat_at AS lastHeartbeatAt,
                       d.revision AS draftRevision, d.saved_count AS draftSavedCount,
                       d.updated_at AS draftUpdatedAt,
                       %s AS serverDeadline,
                       TIMESTAMPDIFF(SECOND, NOW(), %s) AS secondsToDeadline
                """.formatted(deadline, deadline);
        String base = """
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                LEFT JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN exam_monitor_session s ON s.attempt_id = a.id
                LEFT JOIN exam_answer_draft d ON d.attempt_id = a.id
                """;
        int highRiskThreshold = configNumber(jt, "monitor.riskHighThreshold", 20);
        return switch (type) {
            case "TIMEOUT_PRESSURE" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.status = 1
                      AND a.start_time IS NOT NULL
                      AND e.duration_minutes IS NOT NULL
                      AND TIMESTAMPDIFF(SECOND, NOW(), %s) BETWEEN 0 AND 300
                    """.formatted(deadline), "ORDER BY secondsToDeadline ASC, a.id ASC", new Object[]{});
            case "DEADLINE_PASSED_ACTIVE" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.status = 1
                      AND (
                        (e.end_time IS NOT NULL AND e.end_time < NOW())
                        OR (a.start_time IS NOT NULL AND e.duration_minutes IS NOT NULL
                            AND DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < NOW())
                      )
                    """, "ORDER BY secondsToDeadline ASC, a.id ASC", new Object[]{});
            case "OFFLINE_MONITOR" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.status = 1
                      AND s.id IS NOT NULL
                      AND (s.last_heartbeat_at IS NULL OR s.last_heartbeat_at < DATE_SUB(NOW(), INTERVAL 90 SECOND))
                    """, "ORDER BY s.last_heartbeat_at ASC, a.id ASC", new Object[]{});
            case "HIGH_RISK_MONITOR" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.status = 1
                      AND s.id IS NOT NULL
                      AND s.risk_score >= ?
                    """, "ORDER BY s.risk_score DESC, s.last_event_at DESC, a.id ASC", new Object[]{highRiskThreshold});
            case "STALE_DB_DRAFTS" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.status = 1
                      AND d.id IS NOT NULL
                      AND d.updated_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
                    """, "ORDER BY d.updated_at ASC, a.id ASC", new Object[]{});
            case "FORCED_SUBMITS_TODAY" -> new OpsDrilldownQuery(select, base + """
                    WHERE a.submit_time >= CURDATE()
                      AND a.submit_type = 'FORCED'
                    """, "ORDER BY a.submit_time DESC, a.id DESC", new Object[]{});
            default -> throw new IllegalArgumentException("Unsupported SQL ops drilldown type");
        };
    }

    private PageResult<Map<String, Object>> redisDirtyDraftDrilldown(int page, int size) {
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        Map<String, Object> stats = examDraftCacheService.stats();
        int total = intValue(stats.get("dirtyCount"), 0);
        int requested = Math.max(safeSize, safePage * safeSize);
        List<Map<String, Object>> rows = redisDirtyDraftRows(requested);
        int offset = Math.min((safePage - 1) * safeSize, rows.size());
        int end = Math.min(offset + safeSize, rows.size());
        return PageResult.of(rows.subList(offset, end), total, safePage, safeSize);
    }

    private List<Map<String, Object>> redisDirtyDraftRows(int max) {
        List<Map<String, Object>> dirtyDrafts = examDraftCacheService.dirtyDrafts(Math.max(1, Math.min(max, 5000)));
        if (dirtyDrafts.isEmpty()) {
            return List.of();
        }
        JdbcTemplate jt = requireJdbcTemplate();
        Map<Long, Map<String, Object>> context = activeAttemptContext(jt, dirtyDrafts);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> draft : dirtyDrafts) {
            Long attemptId = nullableLong(draft.get("attemptId"));
            Map<String, Object> row = new LinkedHashMap<>();
            if (attemptId != null && context.containsKey(attemptId)) {
                row.putAll(context.get(attemptId));
            } else {
                row.put("attemptId", attemptId);
                row.put("note", "Dirty Redis draft without active attempt context");
            }
            row.put("draftRevision", draft.get("revision"));
            row.put("draftUpdatedAt", draft.get("savedAt"));
            row.put("cacheKey", draft.get("cacheKey"));
            row.put("clientDraftId", draft.get("clientDraftId"));
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, Map<String, Object>> activeAttemptContext(JdbcTemplate jt, List<Map<String, Object>> drafts) {
        List<Long> attemptIds = drafts.stream()
                .map(item -> nullableLong(item.get("attemptId")))
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (attemptIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", attemptIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT e.id AS examId, e.exam_name AS examName, a.id AS attemptId, a.user_id AS studentUserId,
                       COALESCE(u.real_name, u.username, CAST(a.user_id AS CHAR)) AS studentName,
                       sp.student_no AS studentNo, a.status AS attemptStatus,
                       a.start_time AS attemptStartTime, a.last_heartbeat_at AS lastHeartbeatAt,
                       s.id AS sessionId, s.status AS monitorStatus, s.risk_score AS riskScore,
                       s.event_count AS eventCount, s.last_event_at AS lastEventAt
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id AND e.deleted = 0
                LEFT JOIN sys_user u ON u.id = a.user_id
                LEFT JOIN student_profile sp ON sp.user_id = a.user_id AND sp.deleted = 0
                LEFT JOIN exam_monitor_session s ON s.attempt_id = a.id
                WHERE a.id IN (%s)
                """.formatted(placeholders), attemptIds.toArray());
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            if (attemptId != null) {
                result.put(attemptId, row);
            }
        }
        return result;
    }

    private Map<String, Object> databaseHealth(JdbcTemplate jt) {
        Map<String, Object> database = new LinkedHashMap<>();
        long started = System.nanoTime();
        try {
            Integer result = jt.queryForObject("SELECT 1", Integer.class);
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            database.put("connected", result != null && result == 1);
            database.put("latencyMs", latencyMs);
            database.put("slow", latencyMs >= configNumber(jt, "ops.databaseSlowWarningMs", 500));
            database.put("message", "Database connection is healthy");
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            database.put("connected", false);
            database.put("latencyMs", latencyMs);
            database.put("slow", true);
            database.put("message", "Database is unavailable: " + ex.getClass().getSimpleName());
        }
        return database;
    }

    private Map<String, Object> draftCacheCapacity(JdbcTemplate jt) {
        Map<String, Object> stats = new LinkedHashMap<>(examDraftCacheService.stats());
        int dirtyWarningThreshold = configNumber(jt, "exam.draftCacheDirtyWarningThreshold", 100);
        int dirtyHighThreshold = configNumber(jt, "exam.draftCacheDirtyHighThreshold", 500);
        int errorWarningThreshold = configNumber(jt, "exam.draftCacheErrorWarningThreshold", 5);
        int staleFlushWarningSeconds = configNumber(jt, "exam.draftCacheStaleFlushWarningSeconds", 300);
        stats.put("writeBackEnabled", configBoolean(jt, "exam.draftRedisWriteBackEnabled", false));
        stats.put("flushBatchSize", configNumber(jt, "exam.draftRedisFlushBatchSize", 200));
        stats.put("dirtyWarningThreshold", dirtyWarningThreshold);
        stats.put("dirtyHighThreshold", dirtyHighThreshold);
        stats.put("errorWarningThreshold", errorWarningThreshold);
        stats.put("staleFlushWarningSeconds", staleFlushWarningSeconds);
        stats.put("activeAttempts", queryInt(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 1"));
        stats.put("dbDrafts", queryInt(jt, "SELECT COUNT(*) FROM exam_answer_draft"));
        stats.put("staleDbDrafts", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_answer_draft d
                JOIN exam_attempt a ON a.id = d.attempt_id
                WHERE a.status = 1
                  AND d.updated_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
                """));
        stats.putAll(draftCacheAlert(stats, dirtyWarningThreshold, dirtyHighThreshold,
                errorWarningThreshold, staleFlushWarningSeconds));
        return stats;
    }

    private Map<String, Object> examRuntimeCapacity(JdbcTemplate jt) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("runningExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0
                  AND status = 1
                  AND start_time <= NOW()
                  AND (end_time IS NULL OR end_time >= NOW())
                """));
        runtime.put("activeAttempts", queryInt(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 1"));
        runtime.put("submittedLast10m", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
                """));
        runtime.put("timeoutPressure", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.status = 1
                  AND a.start_time IS NOT NULL
                  AND e.duration_minutes IS NOT NULL
                  AND TIMESTAMPDIFF(SECOND, NOW(), DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE)) BETWEEN 0 AND 300
                """));
        runtime.put("deadlinePassedActiveAttempts", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.status = 1
                  AND (
                    (e.end_time IS NOT NULL AND e.end_time < NOW())
                    OR (a.start_time IS NOT NULL AND e.duration_minutes IS NOT NULL
                        AND DATE_ADD(a.start_time, INTERVAL e.duration_minutes MINUTE) < NOW())
                  )
                """));
        runtime.put("activeAttemptWarningThreshold", configNumber(jt, "ops.activeAttemptWarningThreshold", 300));
        runtime.put("activeAttemptHighThreshold", configNumber(jt, "ops.activeAttemptHighThreshold", 600));
        return runtime;
    }

    private Map<String, Object> monitorRuntimeCapacity(JdbcTemplate jt) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        int highRiskThreshold = configNumber(jt, "monitor.riskHighThreshold", 20);
        runtime.put("sessions", queryInt(jt, "SELECT COUNT(*) FROM exam_monitor_session"));
        runtime.put("activeSessions", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                WHERE a.status = 1
                """));
        runtime.put("offlineActiveSessions", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                WHERE a.status = 1
                  AND (s.last_heartbeat_at IS NULL OR s.last_heartbeat_at < DATE_SUB(NOW(), INTERVAL 90 SECOND))
                """));
        runtime.put("highRiskSessions", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_monitor_session s
                JOIN exam_attempt a ON a.id = s.attempt_id
                WHERE a.status = 1 AND s.risk_score >= ?
                """, highRiskThreshold));
        runtime.put("eventsLast10m", queryInt(jt, """
                SELECT COUNT(*)
                FROM cheat_event
                WHERE event_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
                """));
        runtime.put("eventsLastHour", queryInt(jt, """
                SELECT COUNT(*)
                FROM cheat_event
                WHERE event_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
                """));
        runtime.put("actionsLastHour", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_monitor_action
                WHERE handled_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
                """));
        runtime.put("highRiskThreshold", highRiskThreshold);
        runtime.put("eventStormWarningThreshold", configNumber(jt, "ops.monitorEventStormWarningThreshold", 1000));
        return runtime;
    }

    private Map<String, Object> submitRuntimeCapacity(JdbcTemplate jt) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("submittedToday", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE submit_time >= CURDATE()
                """));
        runtime.put("manualSubmittedToday", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE submit_time >= CURDATE() AND submit_type = 'MANUAL'
                """));
        runtime.put("timeoutSubmittedToday", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE submit_time >= CURDATE() AND submit_type = 'TIMEOUT'
                """));
        runtime.put("forceSubmittedToday", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam_attempt
                WHERE submit_time >= CURDATE() AND submit_type = 'FORCED'
                """));
        runtime.put("replayedSubmitResponses", queryInt(jt, "SELECT COUNT(*) FROM exam_submit_response"));
        return runtime;
    }

    private List<Map<String, Object>> opsCapacityAlerts(Map<String, Object> database,
                                                        Map<String, Object> draftCache,
                                                        Map<String, Object> examRuntime,
                                                        Map<String, Object> monitorRuntime,
                                                        Map<String, Object> submitRuntime) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (!Boolean.TRUE.equals(database.get("connected"))) {
            addOpsAlert(alerts, "HIGH", "DATABASE_DOWN", stringValue(database.get("message")));
        } else if (Boolean.TRUE.equals(database.get("slow"))) {
            addOpsAlert(alerts, "WARN", "DATABASE_SLOW", "Database health check is slower than the warning threshold");
        }
        String draftLevel = stringValue(draftCache.get("alertLevel"));
        if ("HIGH".equals(draftLevel) || "WARN".equals(draftLevel)) {
            addOpsAlert(alerts, draftLevel, "DRAFT_CACHE", stringValue(draftCache.get("alertMessage")));
        }
        int activeAttempts = intValue(examRuntime.get("activeAttempts"), 0);
        int activeWarn = intValue(examRuntime.get("activeAttemptWarningThreshold"), 300);
        int activeHigh = intValue(examRuntime.get("activeAttemptHighThreshold"), 600);
        if (activeAttempts >= activeHigh) {
            addOpsAlert(alerts, "HIGH", "ACTIVE_ATTEMPT_CAPACITY", "Active attempts are above the high threshold");
        } else if (activeAttempts >= activeWarn) {
            addOpsAlert(alerts, "WARN", "ACTIVE_ATTEMPT_CAPACITY", "Active attempts are above the warning threshold");
        }
        if (intValue(examRuntime.get("deadlinePassedActiveAttempts"), 0) > 0) {
            addOpsAlert(alerts, "HIGH", "DEADLINE_PASSED_ACTIVE", "Some attempts are still active after their server deadline");
        }
        if (intValue(monitorRuntime.get("offlineActiveSessions"), 0) > 0) {
            addOpsAlert(alerts, "WARN", "MONITOR_OFFLINE", "Some active monitor sessions are offline");
        }
        if (intValue(monitorRuntime.get("highRiskSessions"), 0) > 0) {
            addOpsAlert(alerts, "WARN", "MONITOR_HIGH_RISK", "High-risk active monitor sessions need attention");
        }
        if (intValue(monitorRuntime.get("eventsLast10m"), 0)
                >= intValue(monitorRuntime.get("eventStormWarningThreshold"), 1000)) {
            addOpsAlert(alerts, "WARN", "MONITOR_EVENT_STORM", "Monitor events are above the 10-minute storm threshold");
        }
        if (intValue(submitRuntime.get("forceSubmittedToday"), 0) > 0) {
            addOpsAlert(alerts, "INFO", "FORCE_SUBMIT_TODAY", "Force submissions were recorded today");
        }
        return alerts;
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
        Map<String, Object> alert = new LinkedHashMap<>();
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

    private void addOpsAlert(List<Map<String, Object>> alerts, String level, String code, String message) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("level", level);
        alert.put("code", code);
        alert.put("message", message == null || message.isBlank() ? code : message);
        alerts.add(alert);
    }

    private String opsAlertLevel(List<Map<String, Object>> alerts) {
        if (alerts.stream().anyMatch(alert -> "HIGH".equals(alert.get("level")))) {
            return "HIGH";
        }
        if (alerts.stream().anyMatch(alert -> "WARN".equals(alert.get("level")))) {
            return "WARN";
        }
        if (alerts.stream().anyMatch(alert -> "INFO".equals(alert.get("level")))) {
            return "INFO";
        }
        return "OK";
    }

    private String opsAlertMessage(List<Map<String, Object>> alerts) {
        if (alerts.isEmpty()) {
            return "Operations capacity is healthy";
        }
        return alerts.size() + " operations alert(s) need review";
    }

    public Map<String, Object> teacherOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 我的考试任务数
        data.put("myExams", queryInt(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0 AND created_by = ?", user.getId()));
        data.put("runningExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND start_time <= NOW()
                  AND (end_time IS NULL OR end_time >= NOW())
                """, user.getId()));
        data.put("upcomingExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND (start_time IS NULL OR start_time > NOW())
                """, user.getId()));
        data.put("finishedExams", queryInt(jt, """
                SELECT COUNT(*)
                FROM exam
                WHERE deleted = 0 AND created_by = ?
                  AND end_time IS NOT NULL AND end_time < NOW()
                """, user.getId()));
        // 待批阅试卷（status=1 表示已提交未批阅）
        data.put("pendingReviews", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE status = 4 AND exam_id IN (SELECT id FROM exam WHERE created_by = ? AND deleted = 0)",
                user.getId()));
        data.put("pendingAppeals", scoreAppealCount(jt, user, 0, null));
        data.put("recheckAppeals", scoreAppealCount(jt, user, 1, "RECHECK_REQUIRED"));
        // 我创建的试卷数
        data.put("myPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0 AND created_by = ?", user.getId()));
        data.put("publishedPapers", queryInt(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0 AND created_by = ? AND status = 1", user.getId()));
        data.put("avgScore", queryInt(jt, """
                SELECT COALESCE(ROUND(AVG(ea.score), 0), 0)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE e.created_by = ? AND e.deleted = 0 AND ea.score IS NOT NULL AND ea.status = 5
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """, user.getId()));

        // 成绩分布
        data.put("scoreDistribution", jt.queryForList("""
                SELECT CASE
                    WHEN ea.score >= 90 THEN '90-100'
                    WHEN ea.score >= 80 THEN '80-89'
                    WHEN ea.score >= 70 THEN '70-79'
                    WHEN ea.score >= 60 THEN '60-69'
                    ELSE '60以下'
                END AS name, COUNT(*) AS value
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE e.created_by = ? AND e.deleted = 0 AND ea.status = 5 AND ea.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                GROUP BY name
                ORDER BY name
                """, user.getId()));

        // 近期考试
        data.put("recentExams", jt.queryForList("""
                SELECT e.id, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id) AS attemptCount,
                       (SELECT COUNT(*) FROM exam_attempt ea WHERE ea.exam_id = e.id AND ea.status IN (2,4,5)) AS submittedCount
                FROM exam e
                WHERE e.deleted = 0 AND e.created_by = ?
                ORDER BY e.start_time DESC LIMIT 6
                """, user.getId()));
        data.put("actionCenter", teacherActionCenter(jt, user));

        return data;
    }

    private Map<String, Object> teacherActionCenter(JdbcTemplate jt, AuthUser user) {
        Map<String, Object> center = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> lifecycleHealth = examService.examLifecycleHealth(null, "ACTION_REQUIRED", user, 1, 5);
        Map<String, Object> lifecycleSummary = mapValue(lifecycleHealth.get("summary"));
        List<Map<String, Object>> lifecycleRows = pageRows(lifecycleHealth.get("page"));

        for (Map<String, Object> row : lifecycleRows) {
            Long examId = nullableLong(row.get("examId"));
            items.add(actionItem("LIFECYCLE_HEALTH", lifecycleTitle(row),
                    stringValue(row.get("examName")),
                    stringValue(row.get("lifecycleNextAction")),
                    1,
                    lifecycleSeverity(row),
                    lifecycleTarget(row),
                    examId,
                    null));
        }

        List<Map<String, Object>> reviewRows = teacherPendingReviewActions(jt, user);
        for (Map<String, Object> row : reviewRows) {
            Long examId = nullableLong(row.get("examId"));
            long pendingAttempts = longValue(row.get("pendingAttemptCount"), 0L);
            long pendingAnswers = longValue(row.get("pendingAnswerCount"), 0L);
            items.add(actionItem("REVIEW", "Review pending",
                    stringValue(row.get("examName")),
                    pendingAttempts + " attempt(s), " + pendingAnswers + " answer(s) need review",
                    pendingAttempts + pendingAnswers,
                    "HIGH",
                    "/reviews?reviewExamId=" + examId,
                    examId,
                    null));
        }

        List<Map<String, Object>> appealRows = teacherAppealActions(jt, user, 0, null, 5);
        for (Map<String, Object> row : appealRows) {
            Long examId = nullableLong(row.get("examId"));
            Long appealId = nullableLong(row.get("appealId"));
            items.add(actionItem("APPEAL", "Reply to score appeal",
                    stringValue(row.get("examName")),
                    stringValue(row.get("studentName")) + " · waiting " + longValue(row.get("ageHours"), 0L) + "h",
                    1,
                    "HIGH",
                    "/reviews?appealExamId=" + examId + "&appealStatus=0&appealHandlingResult=ALL&appealId=" + appealId,
                    examId,
                    appealId));
        }

        List<Map<String, Object>> recheckRows = teacherAppealActions(jt, user, 1, "RECHECK_REQUIRED", 5);
        for (Map<String, Object> row : recheckRows) {
            Long examId = nullableLong(row.get("examId"));
            Long appealId = nullableLong(row.get("appealId"));
            items.add(actionItem("RECHECK", "Close score recheck",
                    stringValue(row.get("examName")),
                    stringValue(row.get("studentName")) + " · recheck still open",
                    1,
                    "HIGH",
                    "/reviews?reviewExamId=" + examId
                            + "&reviewTaskType=RECHECK&appealExamId=" + examId
                            + "&appealStatus=1&appealHandlingResult=RECHECK_REQUIRED&appealId=" + appealId,
                    examId,
                    appealId));
        }

        List<Map<String, Object>> blockedRows = teacherScoreReleaseBlockedActions(jt, user);
        for (Map<String, Object> row : blockedRows) {
            Long examId = nullableLong(row.get("examId"));
            items.add(actionItem("SCORE_BLOCKED", "Score release blocked",
                    stringValue(row.get("examName")),
                    stringValue(row.get("primaryBlocker")) + " · " + longValue(row.get("completedAttemptCount"), 0L)
                            + " completed attempt(s)",
                    scoreReleaseBlockerWeight(row),
                    "WARN",
                    "/exam-tasks?examId=" + examId,
                    examId,
                    null));
        }

        List<Map<String, Object>> readyRows = teacherScoreReleaseReadyActions(jt, user);
        for (Map<String, Object> row : readyRows) {
            Long examId = nullableLong(row.get("examId"));
            items.add(actionItem("SCORE_READY", "Publish scores",
                    stringValue(row.get("examName")),
                    longValue(row.get("scoredCompletedAttemptCount"), 0L) + " scored attempt(s) are ready",
                    longValue(row.get("scoredCompletedAttemptCount"), 0L),
                    "INFO",
                    "/exam-tasks?examId=" + examId,
                    examId,
                    null));
        }

        center.put("generatedAt", LocalDateTime.now());
        center.put("lifecycleActionRequiredExams", longValue(lifecycleSummary.get("actionRequired"), 0L));
        center.put("lifecycleRiskExams", longValue(lifecycleSummary.get("risk"), 0L));
        center.put("pendingReviewExams", reviewRows.size());
        center.put("pendingAppeals", scoreAppealCount(jt, user, 0, null));
        center.put("openRechecks", scoreAppealCount(jt, user, 1, "RECHECK_REQUIRED"));
        center.put("scoreBlockedExams", teacherScoreReleaseBlockedCount(jt, user));
        center.put("readyToPublishExams", teacherScoreReleaseReadyCount(jt, user));
        center.put("total", longValue(center.get("pendingReviewExams"), 0L)
                + longValue(center.get("pendingAppeals"), 0L)
                + longValue(center.get("openRechecks"), 0L)
                + longValue(center.get("scoreBlockedExams"), 0L)
                + longValue(center.get("readyToPublishExams"), 0L)
                + longValue(center.get("lifecycleActionRequiredExams"), 0L));
        center.put("items", items);
        return center;
    }

    private List<Map<String, Object>> teacherPendingReviewActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT e.id AS examId, e.exam_name AS examName, p.paper_name AS paperName, e.end_time AS endTime,
                       COUNT(DISTINCT CASE WHEN a.status = 4 THEN a.id END) AS pendingAttemptCount,
                       COUNT(DISTINCT ar.id) AS pendingAnswerCount
                FROM exam e
                JOIN paper p ON p.id = e.paper_id
                JOIN exam_attempt a ON a.exam_id = e.id
                LEFT JOIN answer_record ar ON ar.attempt_id = a.id AND ar.review_status = 0
                WHERE e.deleted = 0
                  AND e.created_by = ?
                  AND (a.status = 4 OR ar.id IS NOT NULL)
                GROUP BY e.id, e.exam_name, p.paper_name, e.end_time
                ORDER BY pendingAttemptCount DESC, pendingAnswerCount DESC, e.end_time ASC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> teacherAppealActions(JdbcTemplate jt, AuthUser user, int status,
                                                           String handlingResult, int limit) {
        List<Object> params = new ArrayList<>();
        params.add(status);
        StringBuilder sql = new StringBuilder("""
                SELECT sa.id AS appealId, e.id AS examId, e.exam_name AS examName,
                       COALESCE(u.real_name, u.username, CAST(sa.user_id AS CHAR)) AS studentName,
                       sa.created_at AS createdAt,
                       TIMESTAMPDIFF(HOUR, sa.created_at, NOW()) AS ageHours
                FROM score_appeal sa
                JOIN exam_attempt a ON a.id = sa.attempt_id
                JOIN exam e ON e.id = a.exam_id
                LEFT JOIN sys_user u ON u.id = sa.user_id
                WHERE e.deleted = 0
                  AND e.created_by = ?
                  AND sa.status = ?
                """);
        params.add(0, user.getId());
        if (handlingResult != null && !handlingResult.isBlank()) {
            sql.append(" AND sa.handling_result = ?");
            params.add(handlingResult);
        }
        sql.append(" ORDER BY sa.created_at ASC LIMIT ?");
        params.add(limit);
        return jt.queryForList(sql.toString(), params.toArray());
    }

    private List<Map<String, Object>> teacherScoreReleaseBlockedActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT x.*,
                       CASE
                         WHEN x.activeAttemptCount > 0 THEN 'ACTIVE_ATTEMPTS'
                         WHEN x.pendingReviewAttemptCount > 0 THEN 'PENDING_REVIEW'
                         WHEN x.pendingAnswerReviewCount > 0 THEN 'PENDING_REVIEW_ANSWERS'
                         WHEN x.nonFinalStartedAttemptCount > 0 THEN 'NON_FINAL_ATTEMPTS'
                         WHEN x.pendingScoreAppealCount > 0 THEN 'PENDING_APPEALS'
                         WHEN x.openRecheckAppealCount > 0 THEN 'OPEN_RECHECK'
                         WHEN x.unscoredCompletedAttemptCount > 0 THEN 'UNSCORED_COMPLETED'
                         WHEN x.completedAttemptCount = 0 THEN 'NO_COMPLETED_ATTEMPTS'
                         ELSE 'EXAM_NOT_READY'
                       END AS primaryBlocker
                FROM (
                  SELECT e.id AS examId, e.exam_name AS examName, e.end_time AS endTime,
                         COALESCE(sr.status, 0) AS scoreReleaseStatus,
                         CASE WHEN e.status = 2 OR (e.end_time IS NOT NULL AND e.end_time <= NOW()) THEN 1 ELSE 0 END AS ended,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                         (SELECT COUNT(*)
                          FROM answer_record ar
                          JOIN exam_attempt a ON a.id = ar.attempt_id
                          WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
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
                  WHERE e.deleted = 0 AND e.created_by = ?
                ) x
                WHERE x.scoreReleaseStatus <> 1
                  AND x.ended = 1
                  AND (x.completedAttemptCount = 0
                       OR x.unscoredCompletedAttemptCount > 0
                       OR x.activeAttemptCount > 0
                       OR x.pendingReviewAttemptCount > 0
                       OR x.pendingAnswerReviewCount > 0
                       OR x.nonFinalStartedAttemptCount > 0
                       OR x.pendingScoreAppealCount > 0
                       OR x.openRecheckAppealCount > 0)
                ORDER BY x.pendingReviewAttemptCount DESC, x.pendingAnswerReviewCount DESC, x.endTime DESC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> teacherScoreReleaseReadyActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT x.*
                FROM (
                  SELECT e.id AS examId, e.exam_name AS examName, e.end_time AS endTime,
                         COALESCE(sr.status, 0) AS scoreReleaseStatus,
                         CASE WHEN e.status = 2 OR (e.end_time IS NOT NULL AND e.end_time <= NOW()) THEN 1 ELSE 0 END AS ended,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                         (SELECT COUNT(*)
                          FROM answer_record ar
                          JOIN exam_attempt a ON a.id = ar.attempt_id
                          WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
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
                  WHERE e.deleted = 0 AND e.created_by = ?
                ) x
                WHERE x.scoreReleaseStatus <> 1
                  AND x.ended = 1
                  AND x.completedAttemptCount > 0
                  AND x.scoredCompletedAttemptCount > 0
                  AND x.unscoredCompletedAttemptCount = 0
                  AND x.activeAttemptCount = 0
                  AND x.pendingReviewAttemptCount = 0
                  AND x.pendingAnswerReviewCount = 0
                  AND x.nonFinalStartedAttemptCount = 0
                  AND x.pendingScoreAppealCount = 0
                  AND x.openRecheckAppealCount = 0
                ORDER BY x.endTime DESC
                LIMIT 5
                """, user.getId());
    }

    private int teacherScoreReleaseBlockedCount(JdbcTemplate jt, AuthUser user) {
        return queryInt(jt, """
                SELECT COUNT(*)
                FROM (
                  SELECT e.id AS examId,
                         COALESCE(sr.status, 0) AS scoreReleaseStatus,
                         CASE WHEN e.status = 2 OR (e.end_time IS NOT NULL AND e.end_time <= NOW()) THEN 1 ELSE 0 END AS ended,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                         (SELECT COUNT(*)
                          FROM answer_record ar
                          JOIN exam_attempt a ON a.id = ar.attempt_id
                          WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount,
                         (SELECT COUNT(*) FROM score_appeal sa LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                          WHERE ((a.id IS NOT NULL AND a.exam_id = e.id) OR (a.id IS NULL AND sa.exam_id = e.id))
                            AND sa.status = 0) AS pendingScoreAppealCount,
                         (SELECT COUNT(*) FROM score_appeal sa LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                          WHERE ((a.id IS NOT NULL AND a.exam_id = e.id) OR (a.id IS NULL AND sa.exam_id = e.id))
                            AND sa.status = 1 AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                  FROM exam e
                  LEFT JOIN score_release sr ON sr.exam_id = e.id
                  WHERE e.deleted = 0 AND e.created_by = ?
                ) x
                WHERE x.scoreReleaseStatus <> 1
                  AND x.ended = 1
                  AND (x.completedAttemptCount = 0
                       OR x.unscoredCompletedAttemptCount > 0
                       OR x.activeAttemptCount > 0
                       OR x.pendingReviewAttemptCount > 0
                       OR x.pendingAnswerReviewCount > 0
                       OR x.nonFinalStartedAttemptCount > 0
                       OR x.pendingScoreAppealCount > 0
                       OR x.openRecheckAppealCount > 0)
                """, user.getId());
    }

    private int teacherScoreReleaseReadyCount(JdbcTemplate jt, AuthUser user) {
        return queryInt(jt, """
                SELECT COUNT(*)
                FROM (
                  SELECT e.id AS examId,
                         COALESCE(sr.status, 0) AS scoreReleaseStatus,
                         CASE WHEN e.status = 2 OR (e.end_time IS NOT NULL AND e.end_time <= NOW()) THEN 1 ELSE 0 END AS ended,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5) AS completedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NOT NULL) AS scoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 5 AND a.score IS NULL) AS unscoredCompletedAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 1) AS activeAttemptCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status = 4) AS pendingReviewAttemptCount,
                         (SELECT COUNT(*)
                          FROM answer_record ar
                          JOIN exam_attempt a ON a.id = ar.attempt_id
                          WHERE a.exam_id = e.id AND ar.review_status = 0) AS pendingAnswerReviewCount,
                         (SELECT COUNT(*) FROM exam_attempt a WHERE a.exam_id = e.id AND a.status <> 0 AND a.status <> 5) AS nonFinalStartedAttemptCount,
                         (SELECT COUNT(*) FROM score_appeal sa LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                          WHERE ((a.id IS NOT NULL AND a.exam_id = e.id) OR (a.id IS NULL AND sa.exam_id = e.id))
                            AND sa.status = 0) AS pendingScoreAppealCount,
                         (SELECT COUNT(*) FROM score_appeal sa LEFT JOIN exam_attempt a ON a.id = sa.attempt_id
                          WHERE ((a.id IS NOT NULL AND a.exam_id = e.id) OR (a.id IS NULL AND sa.exam_id = e.id))
                            AND sa.status = 1 AND sa.handling_result = 'RECHECK_REQUIRED') AS openRecheckAppealCount
                  FROM exam e
                  LEFT JOIN score_release sr ON sr.exam_id = e.id
                  WHERE e.deleted = 0 AND e.created_by = ?
                ) x
                WHERE x.scoreReleaseStatus <> 1
                  AND x.ended = 1
                  AND x.completedAttemptCount > 0
                  AND x.scoredCompletedAttemptCount > 0
                  AND x.unscoredCompletedAttemptCount = 0
                  AND x.activeAttemptCount = 0
                  AND x.pendingReviewAttemptCount = 0
                  AND x.pendingAnswerReviewCount = 0
                  AND x.nonFinalStartedAttemptCount = 0
                  AND x.pendingScoreAppealCount = 0
                  AND x.openRecheckAppealCount = 0
                """, user.getId());
    }

    private Map<String, Object> actionItem(String type, String title, String subject, String detail,
                                           long count, String severity, String target, Long examId, Long appealId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("title", title);
        item.put("subject", subject == null ? "" : subject);
        item.put("detail", detail == null ? "" : detail);
        item.put("count", count);
        item.put("severity", severity);
        item.put("target", target);
        item.put("examId", examId);
        item.put("appealId", appealId);
        return item;
    }

    private long scoreReleaseBlockerWeight(Map<String, Object> row) {
        long sum = longValue(row.get("activeAttemptCount"), 0L)
                + longValue(row.get("pendingReviewAttemptCount"), 0L)
                + longValue(row.get("pendingAnswerReviewCount"), 0L)
                + longValue(row.get("nonFinalStartedAttemptCount"), 0L)
                + longValue(row.get("pendingScoreAppealCount"), 0L)
                + longValue(row.get("openRecheckAppealCount"), 0L)
                + longValue(row.get("unscoredCompletedAttemptCount"), 0L);
        return sum > 0 ? sum : 1L;
    }

    public Map<String, Object> studentOverview(AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();

        // 即将开始/进行中的考试
        data.put("upcomingExams", queryInt(jt,
                """
                SELECT COUNT(*)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND e.deleted = 0 AND ea.status = 0
                  AND (e.end_time IS NULL OR e.end_time >= NOW())
                """,
                user.getId()));
        data.put("activeExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE user_id = ? AND status = 1",
                user.getId()));
        // 已完成考试
        data.put("finishedExams", queryInt(jt,
                "SELECT COUNT(*) FROM exam_attempt WHERE user_id = ? AND status IN (2,4,5)", user.getId()));
        // 错题数
        data.put("wrongQuestions", queryInt(jt,
                """
                SELECT COUNT(DISTINCT CONCAT(e.id, ':', ar.question_id))
                FROM answer_record ar
                JOIN exam_attempt ea ON ea.id = ar.attempt_id
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL AND ar.review_status = 1 AND ar.is_correct = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """, user.getId()));
        data.put("avgScore", queryInt(jt,
                """
                SELECT COALESCE(ROUND(AVG(ea.score), 0), 0)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.user_id = ? AND ea.score IS NOT NULL AND ea.status = 5
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """,
                user.getId()));
        data.put("bestScore", queryInt(jt,
                """
                SELECT COALESCE(ROUND(MAX(ea.score), 0), 0)
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.user_id = ? AND ea.score IS NOT NULL AND ea.status = 5
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                """,
                user.getId()));

        // 成绩趋势
        data.put("scoreTrend", jt.queryForList("""
                SELECT DATE(ea.submit_time) AS date, ea.score AS score, e.exam_name AS examName
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL AND e.deleted = 0 AND ea.submit_time IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                ORDER BY ea.submit_time DESC LIMIT 10
                """, user.getId()));

        data.put("recentExams", jt.queryForList("""
                SELECT ea.id AS attemptId, e.exam_name AS name, e.start_time AS time, e.end_time AS endTime,
                       ea.attempt_no AS attemptNo, e.max_attempts AS maxAttempts, ea.status,
                       CASE
                         WHEN e.end_time IS NOT NULL AND e.end_time < NOW() THEN 2
                         WHEN e.start_time IS NOT NULL AND e.start_time <= NOW()
                              AND (e.end_time IS NULL OR e.end_time >= NOW()) THEN 1
                         ELSE 0
                       END AS phase
                FROM exam_attempt ea
                JOIN exam e ON e.id = ea.exam_id
                WHERE ea.user_id = ? AND e.deleted = 0 AND ea.status < 2
                ORDER BY e.start_time ASC, ea.attempt_no ASC
                LIMIT 5
                """, user.getId()));

        // 知识点掌握度
        // 知识点掌握度（基于答题记录，wrong_answer 表不存在，改用 answer_record 关联 exam_attempt 取 user）
        data.put("knowledgePoints", jt.queryForList("""
                SELECT kp.point_name AS name,
                       ROUND(AVG(CASE WHEN ar.is_correct = 1 THEN 100 ELSE 30 END), 0) AS mastery
                FROM answer_record ar
                JOIN exam_attempt ea ON ea.id = ar.attempt_id
                JOIN exam e ON e.id = ea.exam_id AND e.deleted = 0
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                JOIN question q ON q.id = ar.question_id
                LEFT JOIN exam_question_snapshot eqs ON eqs.exam_id = e.id AND eqs.question_id = ar.question_id
                JOIN edu_knowledge_point kp
                  ON kp.id = CASE WHEN eqs.id IS NOT NULL THEN eqs.knowledge_point_id ELSE q.knowledge_point_id END
                WHERE ea.user_id = ? AND ea.status = 5 AND ea.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = ea.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                GROUP BY kp.id, kp.point_name
                ORDER BY mastery ASC LIMIT 8
                """, user.getId()));
        data.put("actionCenter", studentActionCenter(jt, user, longValue(data.get("wrongQuestions"), 0L)));

        return data;
    }

    private Map<String, Object> studentActionCenter(JdbcTemplate jt, AuthUser user, long wrongQuestionCount) {
        Map<String, Object> center = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        List<Map<String, Object>> activeRows = studentActiveExamActions(jt, user);
        for (Map<String, Object> row : activeRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            items.add(studentActionItem("RESUME_EXAM", "Resume exam",
                    stringValue(row.get("examName")),
                    "Continue before " + stringValue(row.get("endTime")),
                    1,
                    "HIGH",
                    "/student/exams?attemptId=" + attemptId,
                    attemptId,
                    nullableLong(row.get("examId")),
                    null));
        }

        List<Map<String, Object>> readyRows = studentReadyExamActions(jt, user);
        for (Map<String, Object> row : readyRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            boolean rulesConfirmed = row.get("rulesConfirmedAt") != null;
            items.add(studentActionItem(rulesConfirmed ? "ENTER_EXAM" : "CONFIRM_RULES",
                    rulesConfirmed ? "Enter exam" : "Confirm rules",
                    stringValue(row.get("examName")),
                    longValue(row.get("secondsUntilEnd"), 0L) + " second(s) left in exam window",
                    1,
                    "HIGH",
                    "/student/exams?attemptId=" + attemptId + (rulesConfirmed ? "" : "&notice=rules"),
                    attemptId,
                    nullableLong(row.get("examId")),
                    null));
        }

        List<Map<String, Object>> waitingRows = studentWaitingSoonActions(jt, user);
        for (Map<String, Object> row : waitingRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            items.add(studentActionItem("UPCOMING_EXAM", "Upcoming exam",
                    stringValue(row.get("examName")),
                    "Starts in " + longValue(row.get("secondsUntilStart"), 0L) + " second(s)",
                    1,
                    "INFO",
                    "/student/exams?attemptId=" + attemptId,
                    attemptId,
                    nullableLong(row.get("examId")),
                    null));
        }

        List<Map<String, Object>> releasedRows = studentReleasedScoreActions(jt, user);
        for (Map<String, Object> row : releasedRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            items.add(studentActionItem("SCORE_RELEASED", "Score released",
                    stringValue(row.get("examName")),
                    "Published at " + stringValue(row.get("scorePublishedAt")),
                    1,
                    "INFO",
                    "/student/results?attemptId=" + attemptId,
                    attemptId,
                    nullableLong(row.get("examId")),
                    null));
        }

        List<Map<String, Object>> pendingScoreRows = studentPendingScoreActions(jt, user);
        for (Map<String, Object> row : pendingScoreRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            items.add(studentActionItem("SCORE_PENDING", "Score pending",
                    stringValue(row.get("examName")),
                    stringValue(row.get("scoreVisibility")),
                    1,
                    "WARN",
                    "/student/results?attemptId=" + attemptId,
                    attemptId,
                    nullableLong(row.get("examId")),
                    null));
        }

        List<Map<String, Object>> appealRows = studentAppealActions(jt, user);
        for (Map<String, Object> row : appealRows) {
            Long attemptId = nullableLong(row.get("attemptId"));
            Long appealId = nullableLong(row.get("appealId"));
            items.add(studentActionItem("APPEAL_STATUS", "Appeal status",
                    stringValue(row.get("examName")),
                    stringValue(row.get("handlingResult")) + " - " + stringValue(row.get("status")),
                    1,
                    "WARN",
                    "/student/results?attemptId=" + attemptId + "&appealId=" + appealId,
                    attemptId,
                    nullableLong(row.get("examId")),
                    appealId));
        }

        if (wrongQuestionCount > 0) {
            items.add(studentActionItem("WRONG_BOOK", "Review wrong questions",
                    "Wrong question book",
                    wrongQuestionCount + " wrong question(s) need practice",
                    wrongQuestionCount,
                    "INFO",
                    "/student/wrong-questions",
                    null,
                    null,
                    null));
        }

        center.put("generatedAt", LocalDateTime.now());
        center.put("activeExams", studentActionCount(jt, user, "ACTIVE"));
        center.put("readyExams", studentActionCount(jt, user, "READY"));
        center.put("waitingSoonExams", studentActionCount(jt, user, "WAITING_SOON"));
        center.put("releasedScores", studentActionCount(jt, user, "RELEASED_SCORE"));
        center.put("pendingScores", studentActionCount(jt, user, "PENDING_SCORE"));
        center.put("openAppeals", studentActionCount(jt, user, "OPEN_APPEAL"));
        center.put("wrongQuestions", wrongQuestionCount);
        center.put("total", items.size());
        center.put("items", items);
        return center;
    }

    private List<Map<String, Object>> studentActiveExamActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName, e.end_time AS endTime
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 1
                  AND e.status = 1
                  AND (e.end_time IS NULL OR e.end_time > NOW())
                ORDER BY e.end_time ASC, a.id ASC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> studentReadyExamActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName,
                       a.rules_confirmed_at AS rulesConfirmedAt,
                       CASE WHEN e.end_time IS NULL THEN NULL ELSE GREATEST(TIMESTAMPDIFF(SECOND, NOW(), e.end_time), 0) END AS secondsUntilEnd
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 0
                  AND e.status = 1
                  AND (e.start_time IS NULL OR e.start_time <= NOW())
                  AND (e.end_time IS NULL OR e.end_time > NOW())
                ORDER BY e.end_time ASC, e.start_time ASC, a.id ASC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> studentWaitingSoonActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName,
                       GREATEST(TIMESTAMPDIFF(SECOND, NOW(), e.start_time), 0) AS secondsUntilStart
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 0
                  AND e.status = 1
                  AND e.start_time IS NOT NULL
                  AND e.start_time > NOW()
                  AND e.start_time <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
                ORDER BY e.start_time ASC, a.id ASC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> studentReleasedScoreActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName, sr.published_at AS scorePublishedAt
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 5 AND a.score IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM score_appeal sa
                    WHERE sa.attempt_id = a.id
                      AND sa.status = 1
                      AND sa.handling_result = 'RECHECK_REQUIRED'
                  )
                ORDER BY sr.published_at DESC, a.submit_time DESC
                LIMIT 3
                """, user.getId());
    }

    private List<Map<String, Object>> studentPendingScoreActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT a.id AS attemptId, e.id AS examId, e.exam_name AS examName,
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
                       END AS scoreVisibility
                FROM exam_attempt a
                JOIN exam e ON e.id = a.exam_id
                LEFT JOIN score_release sr ON sr.exam_id = e.id
                WHERE a.user_id = ? AND e.deleted = 0 AND a.status IN (2, 4, 5)
                  AND NOT (COALESCE(sr.status, 0) = 1 AND a.status = 5 AND a.score IS NOT NULL
                    AND NOT EXISTS (
                      SELECT 1 FROM score_appeal sa
                      WHERE sa.attempt_id = a.id
                        AND sa.status = 1
                        AND sa.handling_result = 'RECHECK_REQUIRED'
                    ))
                ORDER BY COALESCE(a.submit_time, e.end_time) DESC, a.id DESC
                LIMIT 5
                """, user.getId());
    }

    private List<Map<String, Object>> studentAppealActions(JdbcTemplate jt, AuthUser user) {
        return jt.queryForList("""
                SELECT sa.id AS appealId, sa.attempt_id AS attemptId, e.id AS examId, e.exam_name AS examName,
                       sa.status, COALESCE(sa.handling_result, 'PENDING') AS handlingResult
                FROM score_appeal sa
                JOIN exam_attempt a ON a.id = sa.attempt_id
                JOIN exam e ON e.id = a.exam_id
                WHERE sa.user_id = ? AND e.deleted = 0 AND sa.status IN (0, 1)
                ORDER BY sa.updated_at DESC, sa.created_at DESC
                LIMIT 5
                """, user.getId());
    }

    private int studentActionCount(JdbcTemplate jt, AuthUser user, String type) {
        return switch (type) {
            case "ACTIVE" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM exam_attempt a
                    JOIN exam e ON e.id = a.exam_id
                    WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 1
                      AND e.status = 1
                      AND (e.end_time IS NULL OR e.end_time > NOW())
                    """, user.getId());
            case "READY" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM exam_attempt a
                    JOIN exam e ON e.id = a.exam_id
                    WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 0
                      AND e.status = 1
                      AND (e.start_time IS NULL OR e.start_time <= NOW())
                      AND (e.end_time IS NULL OR e.end_time > NOW())
                    """, user.getId());
            case "WAITING_SOON" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM exam_attempt a
                    JOIN exam e ON e.id = a.exam_id
                    WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 0
                      AND e.status = 1
                      AND e.start_time IS NOT NULL
                      AND e.start_time > NOW()
                      AND e.start_time <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
                    """, user.getId());
            case "RELEASED_SCORE" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM exam_attempt a
                    JOIN exam e ON e.id = a.exam_id
                    JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1
                    WHERE a.user_id = ? AND e.deleted = 0 AND a.status = 5 AND a.score IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM score_appeal sa
                        WHERE sa.attempt_id = a.id
                          AND sa.status = 1
                          AND sa.handling_result = 'RECHECK_REQUIRED'
                      )
                    """, user.getId());
            case "PENDING_SCORE" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM exam_attempt a
                    JOIN exam e ON e.id = a.exam_id
                    LEFT JOIN score_release sr ON sr.exam_id = e.id
                    WHERE a.user_id = ? AND e.deleted = 0 AND a.status IN (2, 4, 5)
                      AND NOT (COALESCE(sr.status, 0) = 1 AND a.status = 5 AND a.score IS NOT NULL
                        AND NOT EXISTS (
                          SELECT 1 FROM score_appeal sa
                          WHERE sa.attempt_id = a.id
                            AND sa.status = 1
                            AND sa.handling_result = 'RECHECK_REQUIRED'
                        ))
                    """, user.getId());
            case "OPEN_APPEAL" -> queryInt(jt, """
                    SELECT COUNT(*)
                    FROM score_appeal sa
                    JOIN exam_attempt a ON a.id = sa.attempt_id
                    JOIN exam e ON e.id = a.exam_id
                    WHERE sa.user_id = ? AND e.deleted = 0 AND sa.status IN (0, 1)
                    """, user.getId());
            default -> 0;
        };
    }

    private Map<String, Object> studentActionItem(String type, String title, String subject, String detail,
                                                  long count, String severity, String target, Long attemptId,
                                                  Long examId, Long appealId) {
        Map<String, Object> item = actionItem(type, title, subject, detail, count, severity, target, examId, appealId);
        item.put("attemptId", attemptId);
        return item;
    }

    private int queryInt(JdbcTemplate jt, String sql, Object... args) {
        Long val = jt.queryForObject(sql, (rs, rowNum) -> rs.getLong(1), args);
        return val == null ? 0 : val.intValue();
    }

    private int scoreAppealCount(JdbcTemplate jt, AuthUser user, int status, String handlingResult) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM score_appeal sa
                JOIN exam_attempt a ON a.id = sa.attempt_id
                JOIN exam e ON e.id = a.exam_id
                WHERE e.deleted = 0 AND sa.status = ?
                """);
        params.add(status);
        if (handlingResult != null && !handlingResult.isBlank()) {
            sql.append(" AND sa.handling_result = ?");
            params.add(handlingResult);
        }
        if (!teachingScopeService.hasGlobalScope(user)) {
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
        return queryInt(jt, sql.toString(), params.toArray());
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

    private double queryDouble(JdbcTemplate jt, String sql, Object... args) {
        Double val = jt.queryForObject(sql, (rs, rowNum) -> rs.getDouble(1), args);
        return val == null ? 0 : val;
    }

    private boolean configBoolean(JdbcTemplate jt, String key, boolean fallback) {
        try {
            String value = jt.query("""
                    SELECT config_value
                    FROM system_config
                    WHERE config_key = ?
                    """, rs -> rs.next() ? rs.getString("config_value") : null, key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int configNumber(JdbcTemplate jt, String key, int fallback) {
        try {
            String value = jt.query("""
                    SELECT config_value
                    FROM system_config
                    WHERE config_key = ?
                    """, rs -> rs.next() ? rs.getString("config_value") : null, key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add(mapValue(item));
            }
        }
        return result;
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jt = jdbcTemplateProvider.getIfAvailable();
        if (jt == null) {
            throw new DatabaseUnavailableException("数据库连接不可用");
        }
        return jt;
    }
    private record OpsDrilldownQuery(String select, String fromWhere, String orderBy, Object[] params) {
    }
}
