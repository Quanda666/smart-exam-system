package com.smartexam.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志记录服务。
 * 注意：record 方法内部吞掉所有异常，且【不要】加 @Transactional——
 * 日志写入失败不应影响主业务流程，也不应随主流程回滚。
 * IP 自动从当前请求上下文获取（兼容反向代理的 X-Forwarded-For）。
 */
@Service
public class OperationLogService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public OperationLogService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public void record(Long operatorId, String operatorName, String action, String target, String detail) {
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
            if (jdbcTemplate == null) {
                return;
            }
            jdbcTemplate.update(
                    "INSERT INTO operation_log (operator_id, operator_name, action, target, detail, ip) VALUES (?, ?, ?, ?, ?, ?)",
                    operatorId, operatorName, action, target, detail, currentIp());
        } catch (Exception ignored) {
            // 操作日志属于辅助能力，记录失败时静默忽略，不影响主流程。
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception ex) {
            return null;
        }
    }
}
