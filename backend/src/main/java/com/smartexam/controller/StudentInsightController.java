package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.service.ExportService;
import com.smartexam.service.RoleAccessService;
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
public class StudentInsightController {

    private final StudentInsightService studentInsightService;
    private final ExportService exportService;
    private final RoleAccessService roleAccessService;

    public StudentInsightController(StudentInsightService studentInsightService, ExportService exportService,
                                    RoleAccessService roleAccessService) {
        this.studentInsightService = studentInsightService;
        this.exportService = exportService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> classStudents(@PathVariable Long classId) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(studentInsightService.listClassStudents(classId));
    }

    @GetMapping("/students/{userId}")
    public ApiResponse<Map<String, Object>> studentInsight(@PathVariable Long userId) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(studentInsightService.studentInsight(userId));
    }

    @GetMapping("/classes/{classId}/students/export")
    public ResponseEntity<byte[]> exportClassStudents(@PathVariable Long classId) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return exportService.classRoster(classId).toDownload();
    }

    @GetMapping("/students/{userId}/export")
    public ResponseEntity<byte[]> exportStudentScores(@PathVariable Long userId) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return exportService.studentScores(userId).toDownload();
    }
}
