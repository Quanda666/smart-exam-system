package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.AnswerRequest;
import com.smartexam.dto.exam.DraftRequest;
import com.smartexam.dto.exam.ExamApprovalDecisionRequest;
import com.smartexam.dto.exam.ExamRequest;
import com.smartexam.dto.exam.ExamUpdateRequest;
import com.smartexam.dto.exam.ScoreRevokeRequest;
import com.smartexam.dto.exam.StartExamRequest;
import com.smartexam.service.ExamService;
import com.smartexam.service.ExportService;
import com.smartexam.service.OperationLogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;
    private final ExportService exportService;
    private final OperationLogService operationLogService;

    public ExamController(ExamService examService,
                          ExportService exportService,
                          OperationLogService operationLogService) {
        this.examService = examService;
        this.exportService = exportService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/teacher")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<PageResult<Map<String, Object>>> listTeacherExams(@RequestParam(required = false) String keyword,
                                                                          @RequestParam(required = false) Integer status,
                                                                          @RequestParam(required = false) Long examId,
                                                                          @RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.listTeacherExams(keyword, status, examId, currentUser(), page, size));
    }

    @GetMapping("/approvals")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> listApprovalQueue(@RequestParam(required = false) String keyword,
                                                                           @RequestParam(required = false) String creatorKeyword,
                                                                           @RequestParam(required = false) Integer status,
                                                                           @RequestParam(required = false) String statusGroup,
                                                                           @RequestParam(required = false) String startFrom,
                                                                           @RequestParam(required = false) String startTo,
                                                                           @RequestParam(required = false) String risk,
                                                                           @RequestParam(required = false) Long examId,
                                                                           @RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.listApprovalQueue(keyword, creatorKeyword, status, statusGroup, startFrom, startTo, risk,
                examId, page, size, currentUser()));
    }

    @PostMapping("/approvals/reminders")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> sendApprovalOverdueReminders() {
        return ApiResponse.ok("Approval overdue reminders processed",
                examService.sendApprovalOverdueReminders(currentUser()));
    }

    @GetMapping("/approvals/reminders")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> listApprovalReminderLogs(@RequestParam(defaultValue = "1") int page,
                                                                                  @RequestParam(defaultValue = "10") int size,
                                                                                  @RequestParam(required = false) Long logId) {
        return ApiResponse.ok(examService.listApprovalReminderLogs(page, size, logId, currentUser()));
    }

    @GetMapping("/approvals/reminders/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportApprovalReminderLogs() {
        ExportFile file = examService.exportApprovalReminderLogs(currentUser());
        return file.toDownload();
    }

    @GetMapping("/student")
    @RequireRoles("STUDENT")
    public ApiResponse<PageResult<Map<String, Object>>> listStudentExams(@RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.listStudentExams(currentUser(), page, size));
    }

    @GetMapping("/targets/students")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<Map<String, Object>>> listTargetStudents() {
        return ApiResponse.ok(examService.listTargetStudents(currentUser()));
    }

    @PostMapping("/preflight")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> preflightExam(@Valid @RequestBody ExamRequest request) {
        return ApiResponse.ok(examService.preflightExam(request, currentUser()));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> createExam(@Valid @RequestBody ExamRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = examService.createExam(request, user);
        return ApiResponse.ok("Exam created", withOperationLogId(user, result,
                "CREATE_EXAM", "EXAM#" + result.get("id"), request.getExamName()));
    }

    @GetMapping("/{id}/snapshot")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> getExamSnapshot(@PathVariable Long id) {
        return ApiResponse.ok(examService.getExamSnapshot(id, currentUser()));
    }

    @PostMapping("/{id}/snapshot/repair")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> repairExamSnapshot(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = examService.repairExamSnapshot(id, user);
        return ApiResponse.ok("Exam snapshot repaired", withOperationLogId(user, result,
                "REPAIR_EXAM_SNAPSHOT", "EXAM#" + id,
                "Repair exam candidate and question snapshots from current targets and paper."));
    }

    @GetMapping("/{id}/snapshot/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportExamSnapshot(@PathVariable Long id) {
        AuthUser user = currentUser();
        ExportFile file = examService.exportExamSnapshot(id, user);
        operationLogService.record(user.getId(), user.getRealName(),
                "EXPORT_EXAM_SNAPSHOT", "EXAM#" + id,
                "Snapshot evidence export; excludes student answers, correct answers, analysis, stems, option content, and unreleased scores.");
        return file.toDownload();
    }

    @GetMapping("/{id}/approval-logs")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<Map<String, Object>>> listApprovalLogs(@PathVariable Long id) {
        return ApiResponse.ok(examService.listApprovalLogs(id, currentUser()));
    }

    @GetMapping("/{id}/approval-logs/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportApprovalLogs(@PathVariable Long id) {
        ExportFile file = examService.exportApprovalLogs(id, currentUser());
        return file.toDownload();
    }

    @GetMapping("/{id}/score-release-logs")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<List<Map<String, Object>>> listScoreReleaseLogs(@PathVariable Long id) {
        return ApiResponse.ok(examService.listScoreReleaseLogs(id, currentUser()));
    }

    @GetMapping("/{id}/score-release-logs/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportScoreReleaseLogs(@PathVariable Long id) {
        ExportFile file = examService.exportScoreReleaseLogs(id, currentUser());
        return file.toDownload();
    }

    @GetMapping("/scores/safety")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> scoreReleaseSafety(@RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "ALL") String state,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.scoreReleaseSafety(keyword, state, currentUser(), page, size));
    }

    @GetMapping("/scores/safety/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportScoreReleaseSafety(@RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "ALL") String state) {
        ExportFile file = examService.exportScoreReleaseSafety(keyword, state, currentUser());
        return file.toDownload();
    }

    @GetMapping("/lifecycle/health")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> examLifecycleHealth(@RequestParam(required = false) String keyword,
                                                                @RequestParam(defaultValue = "ALL") String state,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.examLifecycleHealth(keyword, state, currentUser(), page, size));
    }

    @GetMapping("/lifecycle/health/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportExamLifecycleHealth(@RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "ALL") String state) {
        ExportFile file = examService.exportExamLifecycleHealth(keyword, state, currentUser());
        return file.toDownload();
    }

    @GetMapping("/lifecycle/health/handoff")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> examLifecycleHealthHandoff(@RequestParam(required = false) String keyword,
                                                                       @RequestParam(defaultValue = "ALL") String state) {
        return ApiResponse.ok(examService.examLifecycleHealthHandoff(keyword, state, currentUser()));
    }

    @PostMapping("/lifecycle/health/handoff/notify")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> notifyExamLifecycleHealthHandoff(@RequestParam(required = false) String keyword,
                                                                             @RequestParam(defaultValue = "ALL") String state,
                                                                             @RequestParam(defaultValue = "SELF") String audience) {
        return ApiResponse.ok(examService.notifyExamLifecycleHealthHandoff(keyword, state, audience, currentUser()));
    }

    @GetMapping("/{id}/lifecycle")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> getExamLifecycle(@PathVariable Long id) {
        return ApiResponse.ok(examService.getExamLifecycle(id, currentUser()));
    }

    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> updateExam(@PathVariable Long id,
                                                       @Valid @RequestBody ExamUpdateRequest request) {
        AuthUser user = currentUser();
        Map<String, Object> result = examService.updateExam(id, request, user);
        return ApiResponse.ok("Exam updated", withOperationLogId(user, result,
                "UPDATE_EXAM", "EXAM#" + id, request.getExamName()));
    }

    @PostMapping("/{id}/approve")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> approveExam(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) ExamApprovalDecisionRequest request) {
        return ApiResponse.ok("Exam approved", examService.approveExam(id, request, currentUser()));
    }

    @PostMapping("/{id}/reject")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> rejectExam(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) ExamApprovalDecisionRequest request) {
        return ApiResponse.ok("Exam rejected", examService.rejectExam(id, request, currentUser()));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> deleteExam(@PathVariable Long id) {
        AuthUser user = currentUser();
        examService.deleteExam(id, user);
        return ApiResponse.ok("Exam deleted", withOperationLogId(user, Map.of("id", id, "deleted", true),
                "DELETE_EXAM", "EXAM#" + id, null));
    }

    @PutMapping("/{id}/close")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> closeExam(@PathVariable Long id) {
        AuthUser user = currentUser();
        examService.closeExam(id, user);
        return ApiResponse.ok("Exam closed", withOperationLogId(user, Map.of("id", id),
                "CLOSE_EXAM", "EXAM#" + id, null));
    }

    @PostMapping("/{id}/scores/publish")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> publishScores(@PathVariable Long id) {
        return ApiResponse.ok("Scores published", examService.publishScores(id, currentUser()));
    }

    @PostMapping("/{id}/scores/recalculate-missing")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> recalculateMissingScores(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = examService.recalculateMissingScores(id, user);
        return ApiResponse.ok("Missing scores recalculated", withOperationLogId(user, result,
                "RECALCULATE_MISSING_SCORES", "EXAM#" + id,
                "Recalculate scores only for completed attempts with missing scores; does not publish or overwrite released scores."));
    }

    @PostMapping("/{id}/attempts/finalize-active")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> finalizeActiveAttempts(@PathVariable Long id) {
        AuthUser user = currentUser();
        Map<String, Object> result = examService.finalizeActiveAttempts(id, user);
        return ApiResponse.ok("Active attempts finalized", withOperationLogId(user, result,
                "FINALIZE_ACTIVE_ATTEMPTS", "EXAM#" + id,
                "Force-submit active attempts from saved drafts before score release; pending review remains teacher-controlled."));
    }

    @GetMapping("/{id}/scores/readiness")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> scoreReleaseReadiness(@PathVariable Long id) {
        return ApiResponse.ok(examService.scoreReleaseReadiness(id, currentUser()));
    }

    @PostMapping("/{id}/scores/revoke")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> revokeScores(@PathVariable Long id,
                                                         @Valid @RequestBody(required = false) ScoreRevokeRequest request) {
        return ApiResponse.ok("Scores revoked", examService.revokeScores(id, request, currentUser()));
    }

    @GetMapping("/{id}/scores/export")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ResponseEntity<byte[]> exportScores(@PathVariable Long id) {
        return exportService.examScoreSheet(id, currentUser()).toDownload();
    }

    @PostMapping("/attempt/{attemptId}/start")
    @RequireRoles("STUDENT")
    public ApiResponse<Map<String, Object>> startExam(@PathVariable Long attemptId,
                                                       @RequestBody(required = false) StartExamRequest request) {
        return ApiResponse.ok(examService.startExam(attemptId, request, currentUser()));
    }

    @PostMapping("/attempt/{attemptId}/submit")
    @RequireRoles("STUDENT")
    public ApiResponse<Map<String, Object>> submitExam(@PathVariable Long attemptId,
                                                        @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.ok(examService.submitExam(attemptId, request.getAnswers(),
                request.getSubmitToken(), currentUser()));
    }

    @PostMapping("/attempt/{attemptId}/heartbeat")
    @RequireRoles("STUDENT")
    public ApiResponse<Map<String, Object>> attemptHeartbeat(@PathVariable Long attemptId) {
        return ApiResponse.ok(examService.attemptHeartbeat(attemptId, currentUser()));
    }

    @PostMapping("/attempt/{attemptId}/force-submit")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> forceSubmitAttempt(@PathVariable Long attemptId) {
        return ApiResponse.ok("Attempt force-submitted",
                examService.forceSubmitAttempt(attemptId, currentUser()));
    }

    @GetMapping("/draft-cache/status")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> draftCacheStatus() {
        return ApiResponse.ok(examService.draftCacheStatus(currentUser()));
    }

    @GetMapping("/attempt-resilience/candidates")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> listAttemptResilienceCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(examService.listAttemptResilienceCandidates(keyword, openOnly, page, size, currentUser()));
    }

    @PostMapping("/attempt-resilience/fixture")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> prepareAttemptResilienceFixture(
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.ok("Attempt resilience fixture prepared",
                examService.prepareAttemptResilienceFixture(request == null ? Map.of() : request, currentUser()));
    }

    @DeleteMapping("/attempt-resilience/fixtures")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> cleanupAttemptResilienceFixtures(
            @RequestParam(defaultValue = "24") int olderThanHours,
            @RequestParam(defaultValue = "verify_student") String studentPrefix,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ApiResponse.ok(dryRun ? "Attempt resilience fixture cleanup preview" : "Attempt resilience fixtures cleaned",
                examService.cleanupAttemptResilienceFixtures(olderThanHours, studentPrefix, dryRun, currentUser()));
    }

    @PostMapping("/attempt/{attemptId}/save")
    @RequireRoles("STUDENT")
    public ApiResponse<Map<String, Object>> saveDraft(@PathVariable Long attemptId,
                                                       @Valid @RequestBody DraftRequest request) {
        return ApiResponse.ok(examService.saveDraft(attemptId, request.getAnswers(),
                request.getClientDraftId(), request.getRevision(), currentUser()));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
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
