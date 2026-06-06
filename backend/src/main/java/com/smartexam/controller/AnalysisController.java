package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.AnalysisService;
import com.smartexam.service.RoleAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final RoleAccessService roleAccessService;

    public AnalysisController(AnalysisService analysisService, RoleAccessService roleAccessService) {
        this.analysisService = analysisService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(analysisService.overview());
    }

    @GetMapping("/teacher")
    public ApiResponse<Map<String, Object>> teacherOverview() {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(analysisService.teacherOverview(user.getId()));
    }
}
