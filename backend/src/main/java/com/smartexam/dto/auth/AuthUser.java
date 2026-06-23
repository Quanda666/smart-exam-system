package com.smartexam.dto.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthUser {

    private Long id;
    private String username;
    private String realName;
    private List<String> roles = new ArrayList<>();
    private String primaryRole;
    private String roleLabel;
    private String defaultPath;
    private Map<String, Object> profile = new LinkedHashMap<>();

    public AuthUser() {
    }

    public AuthUser(Long id, String username, String realName, List<String> roles, Map<String, Object> profile) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        this.profile = profile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profile);
        this.primaryRole = resolvePrimaryRole(this.roles);
        this.roleLabel = resolveRoleLabel(this.primaryRole);
        this.defaultPath = resolveDefaultPath(this.primaryRole);
    }

    public boolean hasRole(String role) {
        if (role == null || roles == null) {
            return false;
        }
        return roles.stream().anyMatch(item -> role.equalsIgnoreCase(item));
    }

    private String resolvePrimaryRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "GUEST";
        }
        if (roles.stream().anyMatch("ADMIN"::equalsIgnoreCase)) {
            return "ADMIN";
        }
        if (roles.stream().anyMatch("TEACHER"::equalsIgnoreCase)) {
            return "TEACHER";
        }
        if (roles.stream().anyMatch("STUDENT"::equalsIgnoreCase)) {
            return "STUDENT";
        }
        return roles.get(0);
    }

    private String resolveRoleLabel(String primaryRole) {
        return switch (primaryRole) {
            case "ADMIN" -> "管理员";
            case "TEACHER" -> "教师";
            case "STUDENT" -> "学生";
            default -> "访客";
        };
    }

    private String resolveDefaultPath(String primaryRole) {
        return switch (primaryRole) {
            case "ADMIN" -> "/admin";
            case "TEACHER" -> "/teacher";
            case "STUDENT" -> "/student";
            default -> "/login";
        };
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        this.primaryRole = resolvePrimaryRole(this.roles);
        this.roleLabel = resolveRoleLabel(this.primaryRole);
        this.defaultPath = resolveDefaultPath(this.primaryRole);
    }

    public String getPrimaryRole() {
        return primaryRole;
    }

    public void setPrimaryRole(String primaryRole) {
        this.primaryRole = primaryRole;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.roleLabel = roleLabel;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    public Map<String, Object> getProfile() {
        return profile;
    }

    public void setProfile(Map<String, Object> profile) {
        this.profile = profile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profile);
    }
}
