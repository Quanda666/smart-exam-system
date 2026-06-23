package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.system.CreateUserRequest;
import com.smartexam.dto.system.RejectTeacherReviewRequest;
import com.smartexam.dto.system.ResetPasswordRequest;
import com.smartexam.dto.system.UpdateUserRequest;
import com.smartexam.service.OperationLogService;
import com.smartexam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system/users")
@RequireRoles("ADMIN")
public class UserController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    public UserController(UserService userService, OperationLogService operationLogService) {
        this.userService = userService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String role,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) Integer teacherStatus,
                                                             @RequestParam(required = false) Long userId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(userService.listUsers(keyword, role, status, teacherStatus, userId, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(userService.summary());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest request) {
        AuthUser admin = currentUser();
        Map<String, Object> user = userService.createUser(request);
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(),
                "新建用户", "用户#" + user.get("id"), "用户名: " + request.getUsername());
        user.put("operationLogId", operationLogId);
        return ApiResponse.ok("用户创建成功", user);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateUserRequest request) {
        AuthUser admin = currentUser();
        Map<String, Object> user = userService.updateUser(id, request);
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(),
                "编辑用户", "用户#" + id, "姓名: " + request.getRealName());
        user.put("operationLogId", operationLogId);
        return ApiResponse.ok("用户信息已更新", user);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        AuthUser admin = currentUser();
        UserService.UserStatusUpdateResult result = userService.updateStatus(id, status, admin.getId());
        String action = result.teacherReviewApproved()
                ? "审核通过教师"
                : (status == 1 ? "启用用户" : "禁用用户");
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(),
                action, "用户#" + id, null);
        return ApiResponse.ok("操作成功", responseWithOperationLogId(
                Map.of("id", id, "status", result.status(),
                        "teacherReviewApproved", result.teacherReviewApproved()), operationLogId));
    }

    @PutMapping("/{id}/teacher-review/reject")
    public ApiResponse<Map<String, Object>> rejectTeacherReview(@PathVariable Long id,
                                                                 @Valid @RequestBody RejectTeacherReviewRequest request) {
        AuthUser admin = currentUser();
        userService.rejectTeacherReview(id, request.getReason());
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(),
                "驳回教师注册", "用户#" + id, request.getReason());
        return ApiResponse.ok("教师注册已驳回", responseWithOperationLogId(
                Map.of("id", id, "teacherStatus", 2), operationLogId));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        AuthUser admin = currentUser();
        userService.resetPassword(id, request.getNewPassword());
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(), "重置密码", "用户#" + id, null);
        return ApiResponse.ok("密码已重置", responseWithOperationLogId(Map.of("id", id), operationLogId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        AuthUser admin = currentUser();
        userService.deleteUser(id, admin.getId());
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(), "删除用户", "用户#" + id, null);
        return ApiResponse.ok("用户已删除", responseWithOperationLogId(
                Map.of("id", id, "deleted", true), operationLogId));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }

    private Map<String, Object> responseWithOperationLogId(Map<String, Object> values, Long operationLogId) {
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("operationLogId", operationLogId);
        return result;
    }
}
