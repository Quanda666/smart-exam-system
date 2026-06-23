package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.MenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuService {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public MenuService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public List<MenuItem> menusFor(AuthUser user) {
        Map<String, MenuItem> menuByPath = new LinkedHashMap<>();
        for (MenuItem menu : allMenus()) {
            menuByPath.put(menu.getPath(), menu);
        }
        List<MenuItem> flatMenus = new ArrayList<>();
        for (String role : List.of("ADMIN", "TEACHER", "STUDENT")) {
            if (user != null && user.hasRole(role)) {
                for (String path : rolePageMap().getOrDefault(role, List.of())) {
                    MenuItem menu = menuByPath.get(path);
                    if (menu != null && canAccess(user, menu) && flatMenus.stream().noneMatch(item -> item.getPath().equals(path))) {
                        flatMenus.add(menu);
                    }
                }
            }
        }
        return groupMenus(user, flatMenus);
    }

    public Map<String, List<String>> defaultRolePageMap() {
        Map<String, List<String>> data = new LinkedHashMap<>();
        // 管理员：概况 -> 核心业务 -> 基础数据 -> 系统管理
        data.put("ADMIN", List.of("/admin", "/exam-approvals", "/question-bank", "/materials", "/papers", "/exam/analysis", "/exam-monitor", "/basic/data", "/system/users", "/system/roles", "/system/config", "/monitor/logs"));
        // 教师：概况 -> 核心业务 -> 基础数据
        data.put("TEACHER", List.of("/teacher", "/exam-tasks", "/exam-monitor", "/reviews", "/teacher/analysis", "/teacher/students", "/question-bank", "/materials", "/papers", "/basic/data"));
        // 学生：概况 -> 核心功能 -> 基础数据
        data.put("STUDENT", List.of("/student", "/student/exams", "/student/results", "/student/wrong-questions", "/basic/data"));
        return data;
    }

    public Map<String, List<String>> rolePageMap() {
        Map<String, List<String>> defaults = defaultRolePageMap();
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null || !rolePermissionTableExists(jdbc)) {
            return defaults;
        }
        try {
            Map<String, List<String>> custom = new LinkedHashMap<>();
            jdbc.query("""
                    SELECT role_code, page_path
                    FROM role_page_permission
                    ORDER BY role_code, sort_order, id
                    """, rs -> {
                String role = rs.getString("role_code");
                String path = rs.getString("page_path");
                custom.computeIfAbsent(role, key -> new ArrayList<>()).add(path);
            });
            Map<String, List<String>> merged = new LinkedHashMap<>();
            defaults.forEach((role, paths) -> merged.put(role, custom.getOrDefault(role, paths)));
            return merged;
        } catch (Exception ex) {
            return defaults;
        }
    }

    public List<MenuItem> allMenuItems() {
        return allMenus();
    }

    @Transactional
    public List<String> updateRolePages(String roleCode, List<String> pages) {
        String role = roleCode == null ? "" : roleCode.trim().toUpperCase();
        Map<String, List<String>> defaults = defaultRolePageMap();
        if (!defaults.containsKey(role)) {
            throw new IllegalArgumentException("Unknown role: " + roleCode);
        }

        Set<String> allowed = new LinkedHashSet<>();
        for (MenuItem menu : allMenus()) {
            if (menu.getRoles() != null && menu.getRoles().contains(role)) {
                allowed.add(menu.getPath());
            }
        }

        Set<String> next = new LinkedHashSet<>();
        if (pages != null) {
            for (String page : pages) {
                if (page != null && allowed.contains(page)) {
                    next.add(page);
                }
            }
        }

        for (String required : requiredPages(role)) {
            if (!next.contains(required)) {
                throw new IllegalArgumentException("Required page missing: " + required);
            }
        }

        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("Data source is not available");
        }
        ensureRolePermissionTable(jdbc);
        jdbc.update("DELETE FROM role_page_permission WHERE role_code = ?", role);
        int sort = 0;
        for (String page : next) {
            jdbc.update("""
                    INSERT INTO role_page_permission (role_code, page_path, sort_order)
                    VALUES (?, ?, ?)
                    """, role, page, sort++);
        }
        return new ArrayList<>(next);
    }

    private List<MenuItem> allMenus() {
        return List.of(
                // 管理员菜单 - 按使用频率排序
                new MenuItem("概况", "/admin", "DataAnalysis", List.of("ADMIN")),
                new MenuItem("考试审批", "/exam-approvals", "Tickets", List.of("ADMIN")),
                new MenuItem("题库管理", "/question-bank", "Collection", List.of("ADMIN", "TEACHER")),
                new MenuItem("课程资料库", "/materials", "Notebook", List.of("ADMIN", "TEACHER")),
                new MenuItem("试卷管理", "/papers", "Files", List.of("ADMIN", "TEACHER")),
                new MenuItem("成绩分析", "/exam/analysis", "PieChart", List.of("ADMIN")),
                new MenuItem("实时监考", "/exam-monitor", "DataLine", List.of("ADMIN", "TEACHER")),
                new MenuItem("基础数据", "/basic/data", "Management", List.of("ADMIN", "TEACHER", "STUDENT")),
                new MenuItem("用户管理", "/system/users", "User", List.of("ADMIN")),
                new MenuItem("角色管理", "/system/roles", "Lock", List.of("ADMIN")),
                new MenuItem("系统配置", "/system/config", "Setting", List.of("ADMIN")),
                new MenuItem("系统日志", "/monitor/logs", "Document", List.of("ADMIN")),

                // 教师菜单 - 按使用频率排序
                new MenuItem("概况", "/teacher", "Notebook", List.of("TEACHER")),
                new MenuItem("考试任务", "/exam-tasks", "Calendar", List.of("TEACHER")),
                new MenuItem("阅卷管理", "/reviews", "EditPen", List.of("TEACHER")),
                new MenuItem("成绩分析", "/teacher/analysis", "TrendCharts", List.of("TEACHER")),
                new MenuItem("学情分析", "/teacher/students", "DataLine", List.of("TEACHER")),

                // 学生菜单 - 按使用频率排序
                new MenuItem("概况", "/student", "House", List.of("STUDENT")),
                new MenuItem("考试中心", "/student/exams", "Clock", List.of("STUDENT")),
                new MenuItem("成绩查询", "/student/results", "Tickets", List.of("STUDENT")),
                new MenuItem("错题本", "/student/wrong-questions", "Reading", List.of("STUDENT"))
        );
    }

    private boolean canAccess(AuthUser user, MenuItem menu) {
        if (user == null || menu.getRoles() == null || menu.getRoles().isEmpty()) {
            return false;
        }
        return menu.getRoles().stream().anyMatch(user::hasRole);
    }

    private List<MenuItem> groupMenus(AuthUser user, List<MenuItem> flatMenus) {
        if (user != null && user.hasRole("ADMIN")) {
            return groups(flatMenus,
                    group("工作台", "House", "/admin"),
                    group("考试与题库", "Collection", "/exam-approvals", "/question-bank", "/materials", "/papers", "/exam/analysis", "/exam-monitor"),
                    group("基础数据", "Management", "/basic/data"),
                    group("用户与权限", "User", "/system/users", "/system/roles", "/system/config"),
                    group("系统监控", "Document", "/monitor/logs"));
        }
        if (user != null && user.hasRole("TEACHER")) {
            return groups(flatMenus,
                    group("工作台", "House", "/teacher"),
                    group("考试管理", "Calendar", "/exam-tasks", "/reviews", "/teacher/analysis", "/exam-monitor"),
                    group("试卷题库", "Files", "/papers", "/question-bank", "/materials"),
                    group("教学数据", "DataLine", "/teacher/students", "/basic/data"));
        }
        if (user != null && user.hasRole("STUDENT")) {
            return groups(flatMenus,
                    group("学习首页", "House", "/student"),
                    group("我的考试", "Clock", "/student/exams", "/student/results", "/student/wrong-questions"),
                    group("基础数据", "Management", "/basic/data"));
        }
        return flatMenus;
    }

    private MenuGroup group(String title, String icon, String... paths) {
        return new MenuGroup(title, icon, List.of(paths));
    }

    private List<MenuItem> groups(List<MenuItem> flatMenus, MenuGroup... groups) {
        List<MenuItem> result = new ArrayList<>();
        for (MenuGroup group : groups) {
            MenuItem parent = new MenuItem(group.title, group.paths.get(0), group.icon, List.of("ADMIN", "TEACHER", "STUDENT"));
            List<MenuItem> children = new ArrayList<>();
            for (String path : group.paths) {
                flatMenus.stream()
                        .filter(item -> item.getPath().equals(path))
                        .findFirst()
                        .ifPresent(children::add);
            }
            if (children.isEmpty()) {
                continue;
            }
            if (children.size() == 1) {
                result.add(children.get(0));
                continue;
            }
            parent.setChildren(children);
            result.add(parent);
        }
        return result;
    }

    private record MenuGroup(String title, String icon, List<String> paths) {
    }

    private List<String> requiredPages(String role) {
        return switch (role) {
            case "ADMIN" -> List.of("/admin", "/system/users", "/system/roles");
            case "TEACHER" -> List.of("/teacher");
            case "STUDENT" -> List.of("/student");
            default -> List.of();
        };
    }

    private boolean rolePermissionTableExists(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'role_page_permission'
                    """, Integer.class);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureRolePermissionTable(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS role_page_permission (
                  id         BIGINT      NOT NULL AUTO_INCREMENT,
                  role_code  VARCHAR(32) NOT NULL,
                  page_path  VARCHAR(128) NOT NULL,
                  sort_order INT         NOT NULL DEFAULT 0,
                  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_role_page_permission (role_code, page_path),
                  KEY idx_role_page_permission_role (role_code, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role page permissions'
                """);
    }
}
