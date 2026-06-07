package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.AnswerRequest;
import com.smartexam.dto.exam.DraftRequest;
import com.smartexam.dto.exam.ExamRequest;
import com.smartexam.dto.exam.ExamUpdateRequest;
import com.smartexam.service.ExamService;
import com.smartexam.service.ExportService;
import com.smartexam.service.RoleAccessService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;
    private final ExportService exportService;
    private final RoleAccessService roleAccessService;

    public ExamController(ExamService examService, ExportService exportService, RoleAccessService roleAccessService) {
        this.examService = examService;
        this.exportService = exportService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/teacher")
    public ApiResponse<PageResult<Map<String, Object>>> listTeacherExams(@RequestParam(required = false) String keyword,
                                                                          @RequestParam(required = false) Integer status,
                                                                          @RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(examService.listTeacherExams(keyword, status, user, page, size));
    }

    @GetMapping("/student")
    public ApiResponse<PageResult<Map<String, Object>>> listStudentExams(@RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(examService.listStudentExams(user, page, size));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createExam(@Valid @RequestBody ExamRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("考试任务创建成功", examService.createExam(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateExam(@PathVariable Long id, @Valid @RequestBody ExamUpdateRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("考试更新成功", examService.updateExam(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteExam(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        examService.deleteExam(id, user);
        return ApiResponse.ok("考试已删除", Map.of("id", id, "deleted", true));
    }

    @PutMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> closeExam(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        examService.closeExam(id, user);
        return ApiResponse.ok("考试已结束", Map.of("id", id));
    }

    @GetMapping("/{id}/scores/export")
    public ResponseEntity<byte[]> exportScores(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return exportService.examScoreSheet(id, user).toDownload();
    }

    @PostMapping("/attempt/{attemptId}/start")
    public ApiResponse<Map<String, Object>> startExam(@PathVariable Long attemptId) {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(examService.startExam(attemptId, user));
    }

    @PostMapping("/attempt/{attemptId}/submit")
    public ApiResponse<Map<String, Object>> submitExam(@PathVariable Long attemptId, @Valid @RequestBody AnswerRequest request) {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(examService.submitExam(attemptId, request.getAnswers(), user));
    }

    @PostMapping("/attempt/{attemptId}/save")
    public ApiResponse<Map<String, Object>> saveDraft(@PathVariable Long attemptId, @RequestBody DraftRequest request) {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        examService.saveDraft(attemptId, request.getAnswers(), user);
        return ApiResponse.ok(Map.of("saved", true));
    }
}
