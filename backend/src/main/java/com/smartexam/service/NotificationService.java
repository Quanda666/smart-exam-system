package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 站内通知服务：针对个人的消息推送，支持已读/未读状态。
 * 不同于全局公告(notice)，通知是一对一的、带状态的。
 */
@Service
public class NotificationService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public NotificationService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 给单个用户发通知（考试发布、审核通过等场景）。 */
    public void send(Long userId, String title, String content, String type, String link) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("INSERT INTO notification (user_id, title, content, type, link) VALUES (?, ?, ?, ?, ?)",
                userId, title, content, type, link);
    }

    /** 批量发送相同通知给多个用户（考试发布给一个班的全体学生）。 */
    public void sendBatch(List<Long> userIds, String title, String content, String type, String link) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        JdbcTemplate jt = requireJdbcTemplate();
        for (Long userId : userIds) {
            jt.update("INSERT INTO notification (user_id, title, content, type, link) VALUES (?, ?, ?, ?, ?)",
                    userId, title, content, type, link);
        }
    }

    /** 我的通知列表（分页，最新在前）。 */
    public PageResult<Map<String, Object>> myNotifications(Long userId, int page, int size) {
        JdbcTemplate jt = requireJdbcTemplate();
        Long total = jt.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Long.class, userId);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT id, title, content, type, link, is_read AS isRead, created_at AS createdAt
                FROM notification
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, userId, safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    /** 未读通知数（供顶栏红点显示）。 */
    public long unreadCount(Long userId) {
        JdbcTemplate jt = requireJdbcTemplate();
        Long count = jt.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND is_read = 0",
                Long.class, userId);
        return count == null ? 0L : count;
    }

    /** 标记某条通知已读。 */
    public void markRead(Long notificationId, Long userId) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("UPDATE notification SET is_read = 1 WHERE id = ? AND user_id = ?", notificationId, userId);
    }

    /** 一键全部已读（用户点击「全部已读」按钮）。 */
    public void markAllRead(Long userId) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("UPDATE notification SET is_read = 1 WHERE user_id = ? AND is_read = 0", userId);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
