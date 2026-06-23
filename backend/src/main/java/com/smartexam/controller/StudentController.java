package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.student.ScoreAppealRequest;
import com.smartexam.service.ScoreAppealService;
import com.smartexam.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
@RequireRoles("STUDENT")
public class StudentController {

    private final StudentService studentService;
    private final ScoreAppealService scoreAppealService;

    public StudentController(StudentService studentService, ScoreAppealService scoreAppealService) {
        this.studentService = studentService;
        this.scoreAppealService = scoreAppealService;
    }

    @GetMapping("/grades")
    public ApiResponse<?> getGrades() {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentService.getGrades(user));
    }

    @GetMapping("/exam-result/{attemptId}")
    public ApiResponse<?> getExamResult(@PathVariable Long attemptId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentService.getExamResult(attemptId, user));
    }

    @GetMapping("/wrong-questions")
    public ApiResponse<?> getWrongQuestions() {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentService.getWrongQuestions(user));
    }

    @GetMapping("/mastery")
    public ApiResponse<?> getKnowledgePointMastery() {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentService.getKnowledgePointMastery(user));
    }

    @GetMapping("/appeals")
    public ApiResponse<?> myAppeals() {
        AuthUser user = currentUser();
        return ApiResponse.ok(scoreAppealService.listMyAppeals(user));
    }

    @GetMapping("/appeals/{id}/logs")
    public ApiResponse<?> myAppealLogs(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(scoreAppealService.listMyAppealLogs(id, user));
    }

    @GetMapping("/appeals/{id}/logs/export")
    public ResponseEntity<byte[]> exportMyAppealLogs(@PathVariable Long id) {
        AuthUser user = currentUser();
        ExportFile file = scoreAppealService.exportMyAppealLogs(id, user);
        return file.toDownload();
    }

    @GetMapping("/appeals/{id}/evidence")
    public ApiResponse<?> myAppealEvidence(@PathVariable Long id) {
        AuthUser user = currentUser();
        return ApiResponse.ok(scoreAppealService.studentAppealEvidence(id, user));
    }

    @PostMapping("/appeals")
    public ApiResponse<?> submitAppeal(@Valid @RequestBody ScoreAppealRequest request) {
        AuthUser user = currentUser();
        return ApiResponse.ok("申诉已提交", scoreAppealService.submitAppeal(request, user));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }
}
