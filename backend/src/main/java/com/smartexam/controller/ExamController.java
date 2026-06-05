package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.exam.AnswerRequest;
import com.smartexam.dto.exam.ExamRequest;
import com.smartexam.service.ExamService;
import com.smartexam.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;
    private final RoleAccessService roleAccessService;

    public ExamController(ExamService examService, RoleAccessService roleAccessService) {
        this.examService = examService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/teacher")
    public ApiResponse<List<Map<String, Object>>> listTeacherExams(@RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) Integer status) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(examService.listTeacherExams(keyword, status, user));
    }

    @GetMapping("/student")
    public ApiResponse<List<Map<String, Object>>> listStudentExams() {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(examService.listStudentExams(user));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createExam(@Valid @RequestBody ExamRequest request) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok("考试任务创建成功", examService.createExam(request, user));
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
}
