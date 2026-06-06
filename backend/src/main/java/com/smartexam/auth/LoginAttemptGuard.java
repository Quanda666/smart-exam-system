package com.smartexam.auth;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败防护：同一账号连续失败达上限后临时锁定，缓解暴力破解。
 * 基于内存计数，单实例有效；多实例部署时应替换为 Redis 等共享存储。
 */
@Component
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MINUTES = 15;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void assertNotLocked(String username) {
        if (username == null) {
            return;
        }
        Attempt attempt = attempts.get(username.toLowerCase());
        if (attempt != null && attempt.lockedUntil != null && attempt.lockedUntil.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("登录失败次数过多，账号已临时锁定，请 " + LOCK_MINUTES + " 分钟后再试");
        }
    }

    public void recordFailure(String username) {
        if (username == null) {
            return;
        }
        Attempt attempt = attempts.computeIfAbsent(username.toLowerCase(), k -> new Attempt());
        if (attempt.lockedUntil != null && attempt.lockedUntil.isBefore(LocalDateTime.now())) {
            attempt.count = 0;
            attempt.lockedUntil = null;
        }
        attempt.count++;
        if (attempt.count >= MAX_FAILURES) {
            attempt.lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
        }
    }

    public void recordSuccess(String username) {
        if (username != null) {
            attempts.remove(username.toLowerCase());
        }
    }

    private static final class Attempt {
        private int count;
        private LocalDateTime lockedUntil;
    }
}
