package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@RequireRoles({"ADMIN", "TEACHER"})
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(analysisService.overview());
    }

    @GetMapping("/teacher")
    public ApiResponse<Map<String, Object>> teacherOverview() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(analysisService.teacherOverview(user.getId()));
    }
}
