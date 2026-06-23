package com.smartexam.auth;

import com.smartexam.dto.auth.AuthUser;

import java.time.LocalDateTime;

public class TokenSession {

    private final String token;
    private final AuthUser user;
    private final LocalDateTime expiresAt;

    public TokenSession(String token, AuthUser user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public String getToken() {
        return token;
    }

    public AuthUser getUser() {
        return user;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
