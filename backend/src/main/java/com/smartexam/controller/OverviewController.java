package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.OverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/admin")
    public ApiResponse<Map<String, Object>> admin() {
        AuthContext.requireSession();
        return ApiResponse.ok(overviewService.adminOverview());
    }

    @GetMapping("/teacher")
    public ApiResponse<Map<String, Object>> teacher() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(overviewService.teacherOverview(user));
    }

    @GetMapping("/student")
    public ApiResponse<Map<String, Object>> student() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(overviewService.studentOverview(user));
    }
}
