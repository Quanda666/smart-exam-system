package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.NotificationService;
import com.smartexam.service.RoleAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final RoleAccessService roleAccessService;

    public NotificationController(NotificationService notificationService, RoleAccessService roleAccessService) {
        this.notificationService = notificationService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/my")
    public ApiResponse<PageResult<Map<String, Object>>> myNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        AuthUser user = roleAccessService.requireLogin();
        return ApiResponse.ok(notificationService.myNotifications(user.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        AuthUser user = roleAccessService.requireLogin();
        long count = notificationService.unreadCount(user.getId());
        return ApiResponse.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable Long id) {
        AuthUser user = roleAccessService.requireLogin();
        notificationService.markRead(id, user.getId());
        return ApiResponse.ok(Map.of("id", id, "read", true));
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllRead() {
        AuthUser user = roleAccessService.requireLogin();
        notificationService.markAllRead(user.getId());
        return ApiResponse.ok(Map.of("success", true));
    }
}
