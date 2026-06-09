package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.monitor.CheatEventRequest;
import com.smartexam.service.MonitorService;
import com.smartexam.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;
    private final RoleAccessService roleAccessService;

    public MonitorController(MonitorService monitorService, RoleAccessService roleAccessService) {
        this.monitorService = monitorService;
        this.roleAccessService = roleAccessService;
    }

    @PostMapping("/cheat-event")
    public ApiResponse<?> recordCheatEvent(@Valid @RequestBody CheatEventRequest request) {
        monitorService.recordCheatEvent(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/cheat-events/{attemptId}")
    public ApiResponse<?> getCheatEvents(@PathVariable Long attemptId) {
        roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(monitorService.getCheatEvents(attemptId));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<Map<String, Object>>> getOperationLogs(@RequestParam(defaultValue = "1") int page,
                                                                          @RequestParam(defaultValue = "10") int size) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok(monitorService.getOperationLogs(page, size));
    }

    @GetMapping("/ai-logs")
    public ApiResponse<PageResult<Map<String, Object>>> getAiUsageLogs(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "10") int size,
                                                                        @RequestParam(required = false) String scene,
                                                                        @RequestParam(required = false) Boolean success) {
        AuthUser user = roleAccessService.requireAnyRole("ADMIN", "TEACHER");
        return ApiResponse.ok(monitorService.getAiUsageLogs(page, size, scene, success, user));
    }
}
