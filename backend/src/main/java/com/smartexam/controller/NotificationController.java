package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.common.ApiResponse;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequireRoles
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my")
    public ApiResponse<PageResult<Map<String, Object>>> myNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) Long relatedId) {
        AuthUser user = currentUser();
        return ApiResponse.ok(notificationService.myNotifications(user.getId(), page, size, type, relatedType, relatedId));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        AuthUser user = currentUser();
        long count = notificationService.unreadCount(user.getId());
        return ApiResponse.ok(Map.of("count", count));
    }

    @GetMapping("/audit")
    @RequireRoles("ADMIN")
    public ApiResponse<PageResult<Map<String, Object>>> auditNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) Long relatedId,
            @RequestParam(required = false) Long notificationId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(notificationService.auditNotifications(
                page, size, keyword, type, relatedType, relatedId, notificationId, read, userId, startFrom, startTo));
    }

    @GetMapping("/audit/export")
    @RequireRoles("ADMIN")
    public ResponseEntity<byte[]> exportNotificationAudit(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) Long relatedId,
            @RequestParam(required = false) Long notificationId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        ExportFile file = notificationService.exportNotificationAudit(
                keyword, type, relatedType, relatedId, notificationId, read, userId, startFrom, startTo);
        return file.toDownload();
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable Long id) {
        AuthUser user = currentUser();
        notificationService.markRead(id, user.getId());
        return ApiResponse.ok(Map.of("id", id, "read", true));
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllRead() {
        AuthUser user = currentUser();
        notificationService.markAllRead(user.getId());
        return ApiResponse.ok(Map.of("success", true));
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }
}
