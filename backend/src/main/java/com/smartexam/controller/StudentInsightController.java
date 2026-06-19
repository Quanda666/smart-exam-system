package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.ExportService;
import com.smartexam.service.StudentInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/insight")
@RequireRoles({"ADMIN", "TEACHER"})
public class StudentInsightController {

    private final StudentInsightService studentInsightService;
    private final ExportService exportService;

    public StudentInsightController(StudentInsightService studentInsightService, ExportService exportService) {
        this.studentInsightService = studentInsightService;
        this.exportService = exportService;
    }

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> classStudents(@PathVariable Long classId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentInsightService.listClassStudents(classId, user));
    }

    @GetMapping("/students/{userId}")
    public ApiResponse<Map<String, Object>> studentInsight(@PathVariable Long userId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(studentInsightService.studentInsight(userId, user));
    }

    @GetMapping("/classes/{classId}/students/export")
    public ResponseEntity<byte[]> exportClassStudents(@PathVariable Long classId) {
        AuthUser user = currentUser();
        return exportService.classRoster(classId, user).toDownload();
    }

    @GetMapping("/students/{userId}/export")
    public ResponseEntity<byte[]> exportStudentScores(@PathVariable Long userId) {
        AuthUser user = currentUser();
        return exportService.studentScores(userId, user).toDownload();
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }
}
