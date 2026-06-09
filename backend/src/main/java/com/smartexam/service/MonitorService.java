package com.smartexam.service;

import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.monitor.CheatEventRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MonitorService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public MonitorService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public void recordCheatEvent(CheatEventRequest request) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        jdbcTemplate.update("INSERT INTO cheat_event (attempt_id, event_type, extra_info) VALUES (?, ?, ?)",
                request.getAttemptId(), request.getEventType(), request.getExtraInfo());
    }

    public List<Map<String, Object>> getCheatEvents(Long attemptId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("SELECT * FROM cheat_event WHERE attempt_id = ? ORDER BY event_time DESC", attemptId);
    }
    
    public PageResult<Map<String, Object>> getOperationLogs(int page, int size) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_log", Long.class);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT * FROM operation_log ORDER BY created_at DESC LIMIT ? OFFSET ?",
                safeSize, offset);
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> getAiUsageLogs(int page, int size, String scene, Boolean success, AuthUser user) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (user == null || !user.hasRole("ADMIN")) {
            where.append(" AND l.user_id = ?");
            params.add(user == null ? null : user.getId());
        }
        if (scene != null && !scene.isBlank()) {
            where.append(" AND l.scene = ?");
            params.add(scene.trim());
        }
        if (success != null) {
            where.append(" AND l.success = ?");
            params.add(Boolean.TRUE.equals(success) ? 1 : 0);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_usage_log l" + where,
                Long.class,
                params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT l.id,
                       l.user_id AS userId,
                       u.real_name AS userName,
                       l.scene,
                       l.prompt,
                       l.response,
                       l.success,
                       l.error_message AS errorMessage,
                       l.created_at AS createdAt
                FROM ai_usage_log l
                LEFT JOIN sys_user u ON u.id = l.user_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
