package com.smartexam.service;

import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AnalysisService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public AnalysisService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public Map<String, Object> overview() {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", count(jt, "SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        data.put("questionCount", count(jt, "SELECT COUNT(*) FROM question WHERE deleted = 0"));
        data.put("paperCount", count(jt, "SELECT COUNT(*) FROM paper WHERE deleted = 0"));
        data.put("examCount", count(jt, "SELECT COUNT(*) FROM exam WHERE deleted = 0"));
        data.put("attemptCount", count(jt, "SELECT COUNT(*) FROM exam_attempt"));
        data.put("completedCount", count(jt, "SELECT COUNT(*) FROM exam_attempt WHERE status = 5"));

        Double avg = jt.queryForObject(
                "SELECT COALESCE(AVG(score), 0) FROM exam_attempt WHERE status = 5 AND score IS NOT NULL", Double.class);
        data.put("averageScore", avg == null ? 0d : Math.round(avg * 100.0) / 100.0);

        data.put("roleDistribution", jt.queryForList("""
                SELECT r.role_code AS roleCode, r.role_name AS roleName, COUNT(u.id) AS userCount
                FROM sys_role r
                LEFT JOIN sys_user_role ur ON ur.role_id = r.id
                LEFT JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0
                WHERE r.deleted = 0
                GROUP BY r.id, r.role_code, r.role_name
                ORDER BY r.id
                """));

        data.put("subjectStats", jt.queryForList("""
                SELECT s.subject_name AS subjectName,
                       COUNT(DISTINCT e.id) AS examCount,
                       COUNT(ea.id) AS attemptCount,
                       COALESCE(ROUND(AVG(ea.score), 2), 0) AS avgScore
                FROM edu_subject s
                LEFT JOIN paper p ON p.subject_id = s.id AND p.deleted = 0
                LEFT JOIN exam e ON e.paper_id = p.id AND e.deleted = 0
                LEFT JOIN exam_attempt ea ON ea.exam_id = e.id AND ea.status = 5
                WHERE s.deleted = 0
                GROUP BY s.id, s.subject_name
                ORDER BY s.id
                """));

        data.put("scoreDistribution", jt.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN score < 60 THEN 1 ELSE 0 END), 0) AS belowSixty,
                       COALESCE(SUM(CASE WHEN score >= 60 AND score < 70 THEN 1 ELSE 0 END), 0) AS sixtyToSeventy,
                       COALESCE(SUM(CASE WHEN score >= 70 AND score < 80 THEN 1 ELSE 0 END), 0) AS seventyToEighty,
                       COALESCE(SUM(CASE WHEN score >= 80 AND score < 90 THEN 1 ELSE 0 END), 0) AS eightyToNinety,
                       COALESCE(SUM(CASE WHEN score >= 90 THEN 1 ELSE 0 END), 0) AS ninetyToHundred
                FROM exam_attempt WHERE status = 5 AND score IS NOT NULL
                """));

        return data;
    }

    private long count(JdbcTemplate jt, String sql) {
        Long value = jt.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("数据库连接不可用，请检查本地或云端数据源配置");
        }
        return jdbcTemplate;
    }
}
