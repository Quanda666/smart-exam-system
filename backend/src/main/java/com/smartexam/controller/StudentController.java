package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.RoleAccessService;
import com.smartexam.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final RoleAccessService roleAccessService;

    public StudentController(StudentService studentService, RoleAccessService roleAccessService) {
        this.studentService = studentService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/grades")
    public ApiResponse<?> getGrades() {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(studentService.getGrades(user));
    }

    @GetMapping("/exam-result/{attemptId}")
    public ApiResponse<?> getExamResult(@PathVariable Long attemptId) {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(studentService.getExamResult(attemptId, user));
    }

    @GetMapping("/wrong-questions")
    public ApiResponse<?> getWrongQuestions() {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(studentService.getWrongQuestions(user));
    }

    @GetMapping("/mastery")
    public ApiResponse<?> getKnowledgePointMastery() {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(studentService.getKnowledgePointMastery(user));
    }
}
