package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public HealthController(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "smart-exam-backend");
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        data.put("database", checkDatabase());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> database = new LinkedHashMap<>();
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            database.put("connected", false);
            database.put("message", "JdbcTemplate is not initialized");
            return database;
        }

        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            database.put("connected", result != null && result == 1);
            database.put("message", "Database connection is healthy");
        } catch (Exception ex) {
            database.put("connected", false);
            database.put("message", "Database is unavailable: " + ex.getClass().getSimpleName());
        }
        return database;
    }
}
