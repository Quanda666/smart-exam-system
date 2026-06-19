package com.smartexam.service;

import com.smartexam.common.CsvExport;
import com.smartexam.common.ExportFile;
import com.smartexam.common.PageResult;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.system.SystemConfigUpdateRequest;
import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemConfigService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public SystemConfigService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<Map<String, Object>> listConfigs(String category) {
        JdbcTemplate jt = requireJdbcTemplate();
        String normalizedCategory = blankToNull(category);
        return jt.queryForList("""
                SELECT id, config_key AS configKey, config_value AS configValue,
                       value_type AS valueType, category, description, editable,
                       updated_by AS updatedBy, created_at AS createdAt, updated_at AS updatedAt
                FROM system_config
                WHERE (? IS NULL OR category = ?)
                ORDER BY category, config_key
                """, normalizedCategory, normalizedCategory);
    }

    @Transactional
    public Map<String, Object> updateConfig(String key, SystemConfigUpdateRequest request, AuthUser user) {
        JdbcTemplate jt = requireJdbcTemplate();
        Map<String, Object> config = findConfig(jt, key);
        if (((Number) config.get("editable")).intValue() != 1) {
            throw new IllegalStateException("Config is not editable");
        }
        String valueType = String.valueOf(config.get("valueType"));
        String normalizedValue = validateValue(valueType, request.getConfigValue());
        String oldValue = String.valueOf(config.get("configValue"));
        jt.update("""
                UPDATE system_config
                SET config_value = ?, updated_by = ?
                WHERE config_key = ?
                """, normalizedValue, user.getId(), key);
        if (!oldValue.equals(normalizedValue)) {
            recordConfigAudit(jt, config, oldValue, normalizedValue, user.getId());
        }
        return findConfig(jt, key);
    }

    public PageResult<Map<String, Object>> listConfigAuditLogs(int page, int size, String keyword,
                                                               String category, String configKey, Long actorId,
                                                               String startFrom, String startTo) {
        return listConfigAuditLogs(page, size, null, keyword, category, configKey, actorId, startFrom, startTo);
    }

    public PageResult<Map<String, Object>> listConfigAuditLogs(int page, int size, Long logId, String keyword,
                                                               String category, String configKey, Long actorId,
                                                               String startFrom, String startTo) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendConfigAuditFilters(where, params, logId, keyword, category, configKey, actorId, startFrom, startTo);
        Long total = jt.queryForObject("""
                SELECT COUNT(*)
                FROM system_config_log l
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                """ + where, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add(offset);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT l.id,
                       l.config_key AS configKey,
                       l.old_value AS oldValue,
                       l.new_value AS newValue,
                       l.value_type AS valueType,
                       l.category,
                       l.actor_id AS actorId,
                       actor.real_name AS actorName,
                       actor.username AS actorUsername,
                       l.created_at AS createdAt
                FROM system_config_log l
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, listParams.toArray());
        return PageResult.of(list, total == null ? 0 : total, safePage, safeSize);
    }

    public ExportFile exportConfigAuditLogs(String keyword, String category, String configKey, Long actorId,
                                            String startFrom, String startTo) {
        return exportConfigAuditLogs(null, keyword, category, configKey, actorId, startFrom, startTo);
    }

    public ExportFile exportConfigAuditLogs(Long logId, String keyword, String category, String configKey, Long actorId,
                                            String startFrom, String startTo) {
        JdbcTemplate jt = requireJdbcTemplate();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        appendConfigAuditFilters(where, params, logId, keyword, category, configKey, actorId, startFrom, startTo);
        List<Map<String, Object>> list = jt.queryForList("""
                SELECT l.id,
                       l.config_key AS configKey,
                       l.old_value AS oldValue,
                       l.new_value AS newValue,
                       l.value_type AS valueType,
                       l.category,
                       l.actor_id AS actorId,
                       actor.real_name AS actorName,
                       actor.username AS actorUsername,
                       l.created_at AS createdAt
                FROM system_config_log l
                LEFT JOIN sys_user actor ON actor.id = l.actor_id
                """ + where + """
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT 5000
                """, params.toArray());
        List<String> headers = List.of("日志ID", "配置项", "分类", "类型", "旧值", "新值", "操作人", "用户名", "操作人ID", "时间");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : list) {
            rows.add(List.of(
                    emptyIfNull(item.get("id")),
                    emptyIfNull(item.get("configKey")),
                    emptyIfNull(item.get("category")),
                    emptyIfNull(item.get("valueType")),
                    emptyIfNull(item.get("oldValue")),
                    emptyIfNull(item.get("newValue")),
                    emptyIfNull(item.get("actorName")),
                    emptyIfNull(item.get("actorUsername")),
                    emptyIfNull(item.get("actorId")),
                    emptyIfNull(item.get("createdAt"))
            ));
        }
        return new ExportFile("system-config-audit-" + LocalDate.now() + ".csv",
                CsvExport.build(headers, rows));
    }

    private void recordConfigAudit(JdbcTemplate jt, Map<String, Object> config, String oldValue,
                                   String newValue, Long actorId) {
        jt.update("""
                INSERT INTO system_config_log (
                    config_key, old_value, new_value, value_type, category, actor_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                config.get("configKey"),
                oldValue,
                newValue,
                config.get("valueType"),
                config.get("category"),
                actorId);
    }

    private void appendConfigAuditFilters(StringBuilder where, List<Object> params, Long logId, String keyword,
                                          String category, String configKey, Long actorId,
                                          String startFrom, String startTo) {
        if (logId != null) {
            where.append(" AND l.id = ?");
            params.add(logId);
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isBlank()) {
            where.append("""
                    AND (l.config_key LIKE CONCAT('%', ?, '%')
                      OR l.category LIKE CONCAT('%', ?, '%')
                      OR l.old_value LIKE CONCAT('%', ?, '%')
                      OR l.new_value LIKE CONCAT('%', ?, '%')
                      OR actor.real_name LIKE CONCAT('%', ?, '%')
                      OR actor.username LIKE CONCAT('%', ?, '%'))
                    """);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        String safeCategory = blankToNull(category);
        if (safeCategory != null) {
            where.append(" AND l.category = ?");
            params.add(safeCategory);
        }
        String safeConfigKey = configKey == null ? null : configKey.trim();
        if (safeConfigKey != null && !safeConfigKey.isBlank()) {
            where.append(" AND l.config_key = ?");
            params.add(safeConfigKey);
        }
        if (actorId != null) {
            where.append(" AND l.actor_id = ?");
            params.add(actorId);
        }
        if (startFrom != null && !startFrom.isBlank()) {
            where.append(" AND l.created_at >= ?");
            params.add(startFrom.trim());
        }
        if (startTo != null && !startTo.isBlank()) {
            where.append(" AND l.created_at <= ?");
            params.add(startTo.trim());
        }
    }

    private Map<String, Object> findConfig(JdbcTemplate jt, String key) {
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT id, config_key AS configKey, config_value AS configValue,
                       value_type AS valueType, category, description, editable,
                       updated_by AS updatedBy, created_at AS createdAt, updated_at AS updatedAt
                FROM system_config
                WHERE config_key = ?
                """, key);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Config not found: " + key);
        }
        return rows.get(0);
    }

    private String validateValue(String valueType, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Config value cannot be empty");
        }
        String type = valueType == null ? "STRING" : valueType.toUpperCase(Locale.ROOT);
        if ("BOOLEAN".equals(type)) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!"true".equals(lower) && !"false".equals(lower)) {
                throw new IllegalArgumentException("Boolean config value must be true or false");
            }
            return lower;
        }
        if ("NUMBER".equals(type)) {
            try {
                new BigDecimal(value);
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Number config value is invalid");
            }
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Object emptyIfNull(Object value) {
        return value == null ? "" : value;
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }
}
