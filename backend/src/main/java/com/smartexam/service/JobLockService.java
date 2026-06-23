package com.smartexam.service;

import com.smartexam.exception.DatabaseUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobLockService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final String ownerId;

    public JobLockService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.ownerId = resolveOwnerId();
    }

    public String ownerId() {
        return ownerId;
    }

    public boolean tryAcquire(String lockKey, int leaseSeconds) {
        JdbcTemplate jt = requireJdbcTemplate();
        int safeLeaseSeconds = Math.max(30, leaseSeconds);
        jt.update("""
                INSERT IGNORE INTO system_job_lock (lock_key, owner_id, locked_until, acquired_at)
                VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND), NOW())
                """, lockKey, ownerId, safeLeaseSeconds);
        jt.update("""
                UPDATE system_job_lock
                SET owner_id = ?,
                    locked_until = DATE_ADD(NOW(), INTERVAL ? SECOND),
                    acquired_at = NOW(),
                    updated_at = CURRENT_TIMESTAMP
                WHERE lock_key = ?
                  AND (locked_until <= NOW() OR owner_id = ?)
                """, ownerId, safeLeaseSeconds, lockKey, ownerId);
        List<Map<String, Object>> rows = jt.queryForList("""
                SELECT owner_id
                FROM system_job_lock
                WHERE lock_key = ?
                  AND owner_id = ?
                  AND locked_until > NOW()
                """, lockKey, ownerId);
        return !rows.isEmpty();
    }

    public void release(String lockKey) {
        JdbcTemplate jt = requireJdbcTemplate();
        jt.update("""
                UPDATE system_job_lock
                SET locked_until = NOW(), updated_at = CURRENT_TIMESTAMP
                WHERE lock_key = ? AND owner_id = ?
                """, lockKey, ownerId);
    }

    private String resolveOwnerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        String runtime = ManagementFactory.getRuntimeMXBean().getName();
        String value = (host + ":" + runtime + ":" + UUID.randomUUID()).replaceAll("\\s+", "-");
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new DatabaseUnavailableException("Database connection is unavailable");
        }
        return jdbcTemplate;
    }
}
