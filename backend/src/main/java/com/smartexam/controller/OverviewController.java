package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.OverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/admin")
    @RequireRoles("ADMIN")
    public ApiResponse<Map<String, Object>> admin() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(overviewService.adminOverview(user));
    }

    @GetMapping("/admin/ops-drilldown")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> adminOpsDrilldown(@RequestParam String type,
                                                                           @RequestParam(defaultValue = "1") int page,
                                                                           @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireSession();
        return ApiResponse.ok(overviewService.adminOpsDrilldown(type, page, size));
    }

    @GetMapping("/admin/ops-drilldown/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportAdminOpsDrilldown(@RequestParam String type) {
        AuthContext.requireSession();
        ExportFile file = overviewService.exportAdminOpsDrilldown(type);
        return file.toDownload();
    }

    @GetMapping("/teacher")
    @RequireRoles({"ADMIN", "TEACHER"})
    public ApiResponse<Map<String, Object>> teacher() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(overviewService.teacherOverview(user));
    }

    @GetMapping("/student")
    @RequireRoles("STUDENT")
    public ApiResponse<Map<String, Object>> student() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(overviewService.studentOverview(user));
    }
}
