package com.smartexam.service;

import com.smartexam.dto.monitor.CheatEventRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
    
    public List<Map<String, Object>> getOperationLogs() {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        return jdbcTemplate.queryForList("SELECT * FROM operation_log ORDER BY created_at DESC");
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
