package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.system.ResetPasswordRequest;
import com.smartexam.service.OperationLogService;
import com.smartexam.service.RoleAccessService;
import com.smartexam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;
    private final RoleAccessService roleAccessService;
    private final OperationLogService operationLogService;

    public UserController(UserService userService, RoleAccessService roleAccessService, OperationLogService operationLogService) {
        this.userService = userService;
        this.roleAccessService = roleAccessService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String role,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok(userService.listUsers(keyword, role, status, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok(userService.summary());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        AuthUser admin = roleAccessService.requireRole("ADMIN");
        userService.updateStatus(id, status, admin.getId());
        operationLogService.record(admin.getId(), admin.getRealName(),
                status == 1 ? "启用用户" : "禁用用户", "用户#" + id, null);
        return ApiResponse.ok("操作成功", Map.of("id", id, "status", status));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        AuthUser admin = roleAccessService.requireRole("ADMIN");
        userService.resetPassword(id, request.getNewPassword());
        operationLogService.record(admin.getId(), admin.getRealName(), "重置密码", "用户#" + id, null);
        return ApiResponse.ok("密码已重置", Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        AuthUser admin = roleAccessService.requireRole("ADMIN");
        userService.deleteUser(id, admin.getId());
        operationLogService.record(admin.getId(), admin.getRealName(), "删除用户", "用户#" + id, null);
        return ApiResponse.ok("用户已删除", Map.of("id", id, "deleted", true));
    }
}
