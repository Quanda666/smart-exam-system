package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        send(userId, title, content, type, link, null, null);
    }

    public void send(Long userId, String title, String content, String type, String link,
                     String relatedType, Long relatedId) {
        sendAndReturnId(userId, title, content, type, link, relatedType, relatedId);
    }

    public Long sendAndReturnId(Long userId, String title, String content, String type, String link,
                                String relatedType, Long relatedId) {
        JdbcTemplate jt = requireJdbcTemplate();
        return insertNotification(jt, userId, title, content, type, link, relatedType, relatedId, null);
    }

    public Long sendOnceAndReturnId(Long userId, String title, String content, String type, String link,
                                    String relatedType, Long relatedId) {
        JdbcTemplate jt = requireJdbcTemplate();
        if (relatedType == null || relatedId == null) {
            return insertNotification(jt, userId, title, content, type, link, relatedType, relatedId, null);
        }
        return insertNotification(jt, userId, title, content, type, link, relatedType, relatedId,
                notificationDedupKey(userId, type, relatedType, relatedId));
    }

    private Long insertNotification(JdbcTemplate jt, Long userId, String title, String content, String type,
                                    String link, String relatedType, Long relatedId, String dedupKey) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO notification (user_id, title, content, type, link, related_type, related_id, dedup_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, userId);
            ps.setString(2, title);
            ps.setString(3, content);
            ps.setString(4, type);
            ps.setString(5, link);
            ps.setString(6, relatedType);
            ps.setObject(7, relatedId);
            ps.setString(8, dedupKey);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String notificationDedupKey(Long userId, String type, String relatedType, Long relatedId) {
        return userId + ":" + type + ":" + relatedType + ":" + relatedId;
    }

    /** 批量发送相同通知给多个用户（考试发布给一个班的全体学生）。 */
    public void sendBatch(List<Long> userIds, String title, String content, String type, String link) {
        sendBatch(userIds, title, content, type, link, null, null);
    }

    public void sendBatch(List<Long> userIds, String title, String content, String type, String link,
                          String relatedType, Long relatedId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        JdbcTemplate jt = requireJdbcTemplate();
        for (Long userId : userIds) {
            insertNotification(jt, userId, title, content, type, link, relatedType, relatedId, null);
        }
    }

    /** 我的通知列表（分页，最新在前）。 */
    public PageResult<Map<String, Object>> myNotifications(Long userId, int page, int size) {
        return myNotifications(userId, page, size, null, null, null);
    }

    public PageResult<Map<String, Object>> auditNotifications(int page, int size, String keyword,
                                                              String type, String relatedType, Long relatedId,
                                                              Long notificationId, Boolean read, Long userId,
                                                              String startFrom, String startTo) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendNotificationAuditFilters(where, params, keyword, type, relatedType, relatedId, notificationId, read, userId,
                startFrom, startTo);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM notification n
                LEFT JOIN sys_user u ON u.id = n.user_id 
                """
                + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT n.id,
                       n.user_id AS userId,
                       u.username AS username,
                       u.real_name AS realName,
                       n.title,
                       n.content,
                       n.type,
                       n.link,
                       n.related_type AS relatedType,
                       n.related_id AS relatedId,
                       n.is_read AS isRead,
                       n.created_at AS createdAt
                FROM notification n
                LEFT JOIN sys_user u ON u.id = n.user_id
                """
                + where + " " +
                """
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportNotificationAudit(String keyword, String type, String relatedType, Long relatedId,
                                              Long notificationId, Boolean read, Long userId,
                                              String startFrom, String startTo) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendNotificationAuditFilters(where, params, keyword, type, relatedType, relatedId, notificationId, read, userId,
                startFrom, startTo);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT n.id,
                       n.user_id AS userId,
                       u.username AS username,
                       u.real_name AS realName,
                       n.title,
                       n.content,
                       n.type,
                       n.link,
                       n.related_type AS relatedType,
                       n.related_id AS relatedId,
                       n.is_read AS isRead,
                       n.created_at AS createdAt
                FROM notification n
                LEFT JOIN sys_user u ON u.id = n.user_id
                """
                + where + " " +
                """
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT 5000
                """, params.toArray());
        List<String> headers = List.of("通知ID", "接收人", "用户名", "用户ID", "类型", "标题", "内容", "是否已读",
                "关联类型", "关联ID", "跳转链接", "创建时间");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : list) {
            rows.add(List.of(
                    emptyIfNull(item.get("id")),
                    emptyIfNull(item.get("realName")),
                    emptyIfNull(item.get("username")),
                    emptyIfNull(item.get("userId")),
                    emptyIfNull(item.get("type")),
                    emptyIfNull(item.get("title")),
                    emptyIfNull(item.get("content")),
                    notificationReadText(item.get("isRead")),
                    emptyIfNull(item.get("relatedType")),
                    emptyIfNull(item.get("relatedId")),
                    emptyIfNull(item.get("link")),
                    emptyIfNull(item.get("createdAt"))
            ));
        }
        return new ExportFile("notification-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    public PageResult<Map<String, Object>> myNotifications(Long userId, int page, int size,
                                                           String type, String relatedType, Long relatedId) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        String where = buildMyNotificationWhere(userId, type, relatedType, relatedId, params);
        Long total = jt.queryForObject("SELECT COUNT(*) FROM notification " + where,
                Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT id,
                       title,
                       content,
                       type,
                       link,
                       related_type AS relatedType,
                       related_id AS relatedId,
                       is_read AS isRead,
                       created_at AS createdAt
                FROM notification
                """
                + where + " " +
                """
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    private void appendNotificationAuditFilters(StringBuilder where, List<Object> params, String keyword,
                                                String type, String relatedType, Long relatedId, Long notificationId,
                                                Boolean read,
                                                Long userId, String startFrom, String startTo) {
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (n.title LIKE CONCAT('%', ?, '%')
                      OR n.content LIKE CONCAT('%', ?, '%')
                      OR n.type LIKE CONCAT('%', ?, '%')
                      OR n.related_type LIKE CONCAT('%', ?, '%')
                      OR u.real_name LIKE CONCAT('%', ?, '%')
                      OR u.username LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String safeType = normalize(type);
        if (safeType != null) {
            where.append(" AND n.type = ?");
            params.add(safeType);
        }
        String safeRelatedType = normalize(relatedType);
        if (safeRelatedType != null) {
            where.append(" AND n.related_type = ?");
            params.add(safeRelatedType);
        }
        if (relatedId != null) {
            where.append(" AND n.related_id = ?");
            params.add(relatedId);
        }
        if (notificationId != null) {
            where.append(" AND n.id = ?");
            params.add(notificationId);
        }
        if (read != null) {
            where.append(" AND n.is_read = ?");
            params.add(read ? 1 : 0);
        }
        if (userId != null) {
            where.append(" AND n.user_id = ?");
            params.add(userId);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND n.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND n.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private String buildMyNotificationWhere(Long userId, String type, String relatedType, Long relatedId,
                                            List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE user_id = ?");
        params.add(userId);
        String safeType = normalize(type);
        if (safeType != null) {
            where.append(" AND type = ?");
            params.add(safeType);
        }
        String safeRelatedType = normalize(relatedType);
        if (safeRelatedType != null) {
            where.append(" AND related_type = ?");
            params.add(safeRelatedType);
        }
        if (relatedId != null) {
            where.append(" AND related_id = ?");
            params.add(relatedId);
        }
        return where.toString() + " ";
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /** 未读通知数（供顶栏红点显示）。 */
    private Object emptyIfNull(Object value) {
        return value == null ? "" : value;
    }

    private String notificationReadText(Object value) {
        if (value == null) {
            return "";
        }
        int read = Integer.parseInt(String.valueOf(value));
        return read == 1 ? "已读" : "未读";
    }

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
