package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.monitor.CheatEventBatchRequest;
import com.smartexam.dto.monitor.CheatEventRequest;
import com.smartexam.dto.monitor.MonitorActionRequest;
import com.smartexam.dto.monitor.MonitorForceSubmitRequest;
import com.smartexam.service.MonitorService;
import com.smartexam.service.OperationLogService;
import com.smartexam.service.QuestionBankService;
import com.smartexam.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;
    private final OperationLogService operationLogService;
    private final QuestionBankService questionBankService;
    private final SystemConfigService systemConfigService;

    public MonitorController(MonitorService monitorService,
                             OperationLogService operationLogService,
                             QuestionBankService questionBankService,
                             SystemConfigService systemConfigService) {
        this.monitorService = monitorService;
        this.operationLogService = operationLogService;
        this.questionBankService = questionBankService;
        this.systemConfigService = systemConfigService;
    }

    @PostMapping("/cheat-event")
    @RequireRoles("STUDENT")
    public ApiResponse<?> recordCheatEvent(@Valid @RequestBody CheatEventRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        monitorService.recordCheatEvent(request, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/cheat-events/batch")
    @RequireRoles("STUDENT")
    public ApiResponse<?> recordCheatEvents(@Valid @RequestBody CheatEventBatchRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.recordCheatEvents(request.getEvents(), user));
    }

    @GetMapping("/cheat-events/{attemptId}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> getCheatEvents(@PathVariable Long attemptId,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) String startFrom,
                                         @RequestParam(required = false) String startTo,
                                         @RequestParam(required = false) Integer minRiskScore) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.getCheatEvents(attemptId, eventType, startFrom, startTo, minRiskScore, user));
    }

    @GetMapping("/cheat-events/{attemptId}/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportCheatEvents(@PathVariable Long attemptId,
                                                    @RequestParam(required = false) String eventType,
                                                    @RequestParam(required = false) String startFrom,
                                                    @RequestParam(required = false) String startTo,
                                                    @RequestParam(required = false) Integer minRiskScore) {
        AuthUser user = AuthContext.requireSession().getUser();
        ExportFile file = monitorService.exportCheatEvents(attemptId, eventType, startFrom, startTo, minRiskScore, user);
        return file.toDownload();
    }

    @GetMapping("/exams/{examId}/sessions")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> listExamMonitorSessions(@PathVariable Long examId) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.listExamMonitorSessions(examId, user));
    }

    @GetMapping("/exams/{examId}/sessions/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportExamMonitorSessions(@PathVariable Long examId,
                                                            @RequestParam(required = false) String sessionStatus,
                                                            @RequestParam(required = false) Integer minRiskScore,
                                                            @RequestParam(required = false) String latestNotificationStatus,
                                                            @RequestParam(required = false) String rulesConfirmationStatus,
                                                            @RequestParam(required = false) String latestActionType) {
        AuthUser user = AuthContext.requireSession().getUser();
        ExportFile file = monitorService.exportExamMonitorSessions(
                examId, sessionStatus, minRiskScore, latestNotificationStatus, rulesConfirmationStatus,
                latestActionType, user);
        return file.toDownload();
    }

    @PostMapping("/sessions/{sessionId}/actions")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> createMonitorAction(@PathVariable Long sessionId,
                                              @Valid @RequestBody MonitorActionRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        Map<String, Object> result = monitorService.createMonitorAction(sessionId, request, user);
        return ApiResponse.ok(withOperationLogId(user, result,
                "CREATE_MONITOR_ACTION", "MONITOR_SESSION#" + sessionId,
                "actionType=" + request.getActionType()));
    }

    @PostMapping("/sessions/{sessionId}/force-submit")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> forceSubmitMonitorSession(@PathVariable Long sessionId,
                                                    @Valid @RequestBody(required = false) MonitorForceSubmitRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        String note = request == null ? null : request.getNote();
        Map<String, Object> result = monitorService.forceSubmitMonitorSession(sessionId, note, user);
        Object attemptId = result.get("attemptId");
        return ApiResponse.ok(withOperationLogId(user, result,
                "FORCE_SUBMIT_MONITOR_SESSION", "MONITOR_SESSION#" + sessionId,
                "attemptId=" + attemptId));
    }

    @GetMapping("/sessions/{sessionId}/actions")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> listMonitorActions(@PathVariable Long sessionId) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.listMonitorActions(sessionId, user));
    }

    @GetMapping("/sessions/{sessionId}/incident")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<?> getMonitorAttemptIncident(@PathVariable Long sessionId) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.getMonitorAttemptIncident(sessionId, user));
    }

    @GetMapping("/sessions/{sessionId}/actions/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportMonitorActions(@PathVariable Long sessionId) {
        AuthUser user = AuthContext.requireSession().getUser();
        ExportFile file = monitorService.exportMonitorActions(sessionId, user);
        return file.toDownload();
    }

    @GetMapping("/logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getOperationLogs(@RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size,
                                                                          @RequestParam(required = false) Long logId,
                                                                          @RequestParam(required = false) String keyword,
                                                                          @RequestParam(required = false) String action,
                                                                          @RequestParam(required = false) String target,
                                                                          @RequestParam(required = false) String startFrom,
                                                                          @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getOperationLogs(page, size, logId, keyword, action, target, startFrom, startTo));
    }

    @GetMapping("/logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportOperationLogs(@RequestParam(required = false) Long logId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String action,
                                                      @RequestParam(required = false) String target,
                                                      @RequestParam(required = false) String startFrom,
                                                      @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportOperationLogs(logId, keyword, action, target, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/login-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getLoginAuditLogs(@RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "10") int size,
                                                                           @RequestParam(required = false) Long logId,
                                                                           @RequestParam(required = false) String keyword,
                                                                           @RequestParam(required = false) String action,
                                                                           @RequestParam(required = false) Long operatorId,
                                                                           @RequestParam(required = false) Boolean success,
                                                                           @RequestParam(required = false) String startFrom,
                                                                           @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getLoginAuditLogs(
                page, size, logId, keyword, action, operatorId, success, startFrom, startTo));
    }

    @GetMapping("/login-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportLoginAuditLogs(@RequestParam(required = false) Long logId,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String action,
                                                       @RequestParam(required = false) Long operatorId,
                                                       @RequestParam(required = false) Boolean success,
                                                       @RequestParam(required = false) String startFrom,
                                                       @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportLoginAuditLogs(
                logId, keyword, action, operatorId, success, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/ai-logs")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<PageResult<Map<String, Object>>> getAiUsageLogs(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "10") int size,
                                                                        @RequestParam(required = false) String scene,
                                                                        @RequestParam(required = false) Boolean success,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) String startFrom,
                                                                        @RequestParam(required = false) String startTo) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(monitorService.getAiUsageLogs(page, size, scene, success, keyword, startFrom, startTo, user));
    }

    @GetMapping("/ai-logs/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportAiUsageLogs(@RequestParam(required = false) String scene,
                                                    @RequestParam(required = false) Boolean success,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String startFrom,
                                                    @RequestParam(required = false) String startTo) {
        AuthUser user = AuthContext.requireSession().getUser();
        ExportFile file = monitorService.exportAiUsageLogs(scene, success, keyword, startFrom, startTo, user);
        return file.toDownload();
    }

    @GetMapping("/score-release-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getScoreReleaseAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getScoreReleaseAuditLogs(page, size, logId, keyword, action, startFrom, startTo));
    }

    @GetMapping("/score-release-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportScoreReleaseAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportScoreReleaseAuditLogs(logId, keyword, action, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/exam-approval-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getExamApprovalAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getExamApprovalAuditLogs(page, size, logId, keyword, action, startFrom, startTo));
    }

    @GetMapping("/exam-approval-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportExamApprovalAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportExamApprovalAuditLogs(logId, keyword, action, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/approval-reminder-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getApprovalReminderAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerSource,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getApprovalReminderAuditLogs(
                page, size, logId, keyword, status, triggerSource, startFrom, startTo));
    }

    @GetMapping("/approval-reminder-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportApprovalReminderAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerSource,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportApprovalReminderAuditLogs(
                logId, keyword, status, triggerSource, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/system-config-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getSystemConfigAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String configKey,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(systemConfigService.listConfigAuditLogs(
                page, size, logId, keyword, category, configKey, actorId, startFrom, startTo));
    }

    @GetMapping("/system-config-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportSystemConfigAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String configKey,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = systemConfigService.exportConfigAuditLogs(
                logId, keyword, category, configKey, actorId, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/question-review-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getQuestionReviewAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(questionBankService.listReviewAuditLogs(
                page, size, logId, questionId, keyword, actionType, reviewStatus,
                subjectId, operatorId, startFrom, startTo));
    }

    @GetMapping("/question-review-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportQuestionReviewAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = questionBankService.exportReviewAuditLogs(
                logId, questionId, keyword, actionType, reviewStatus, subjectId, operatorId, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/score-appeal-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getScoreAppealAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String handlingResult,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getScoreAppealAuditLogs(
                page, size, logId, keyword, action, handlingResult, startFrom, startTo));
    }

    @GetMapping("/score-appeal-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportScoreAppealAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String handlingResult,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportScoreAppealAuditLogs(
                logId, keyword, action, handlingResult, startFrom, startTo);
        return file.toDownload();
    }

    @GetMapping("/review-score-logs")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> getReviewScoreAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long reviewerId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(monitorService.getReviewScoreAuditLogs(
                page, size, logId, keyword, examId, reviewerId, startFrom, startTo));
    }

    @GetMapping("/review-score-logs/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportReviewScoreAuditLogs(
            @RequestParam(required = false) Long logId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long reviewerId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = monitorService.exportReviewScoreAuditLogs(logId, keyword, examId, reviewerId, startFrom, startTo);
        return file.toDownload();
    }

    private Map<String, Object> withOperationLogId(AuthUser user,
                                                   Map<String, Object> values,
                                                   String action,
                                                   String target,
                                                   String detail) {
        Long operationLogId = operationLogService.record(user.getId(), user.getRealName(), action, target, detail);
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("operationLogId", operationLogId);
        return result;
    }
}
