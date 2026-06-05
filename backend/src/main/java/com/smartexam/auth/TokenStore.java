package com.smartexam.auth;

import com.smartexam.dto.auth.AuthUser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TokenStore {

    private static final int TOKEN_TTL_HOURS = 8;

    private final ConcurrentMap<String, TokenSession> sessions = new ConcurrentHashMap<>();

    public TokenSession create(AuthUser user) {
        clearExpiredTokens();
        String token = UUID.randomUUID().toString().replace("-", "");
        TokenSession session = new TokenSession(token, user, LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        sessions.put(token, session);
        return session;
    }

    public Optional<TokenSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        TokenSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private void clearExpiredTokens() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
