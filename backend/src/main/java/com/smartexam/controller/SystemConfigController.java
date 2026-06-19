package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.system.SystemConfigUpdateRequest;
import com.smartexam.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/configs")
@RequireRoles("ADMIN")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(systemConfigService.listConfigs(category));
    }

    @GetMapping("/audit")
    public ApiResponse<PageResult<Map<String, Object>>> listAuditLogs(@RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "10") int size,
                                                                       @RequestParam(required = false) Long logId,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) String category,
                                                                       @RequestParam(required = false) String configKey,
                                                                       @RequestParam(required = false) Long actorId,
                                                                       @RequestParam(required = false) String startFrom,
                                                                       @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(systemConfigService.listConfigAuditLogs(
                page, size, logId, keyword, category, configKey, actorId, startFrom, startTo));
    }

    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAuditLogs(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long logId,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String configKey,
                                                  @RequestParam(required = false) Long actorId,
                                                  @RequestParam(required = false) String startFrom,
                                                  @RequestParam(required = false) String startTo) {
        ExportFile file = systemConfigService.exportConfigAuditLogs(logId, keyword, category, configKey, actorId, startFrom, startTo);
        return file.toDownload();
    }

    @PutMapping("/{key}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String key,
                                                   @Valid @RequestBody SystemConfigUpdateRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok("配置已更新", systemConfigService.updateConfig(key, request, user));
    }
}
