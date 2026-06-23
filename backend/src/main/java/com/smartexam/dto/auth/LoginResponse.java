package com.smartexam.dto.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LoginResponse {

    private String token;
    private LocalDateTime expiresAt;
    private AuthUser user;
    private List<MenuItem> menus = new ArrayList<>();
    private String defaultPath;

    public LoginResponse() {
    }

    public LoginResponse(String token, LocalDateTime expiresAt, AuthUser user, List<MenuItem> menus, String defaultPath) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.user = user;
        this.menus = menus == null ? new ArrayList<>() : new ArrayList<>(menus);
        this.defaultPath = defaultPath;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public AuthUser getUser() {
        return user;
    }

    public void setUser(AuthUser user) {
        this.user = user;
    }

    public List<MenuItem> getMenus() {
        return menus;
    }

    public void setMenus(List<MenuItem> menus) {
        this.menus = menus == null ? new ArrayList<>() : new ArrayList<>(menus);
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }
}
