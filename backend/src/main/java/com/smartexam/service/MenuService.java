package com.smartexam.service;

import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.MenuItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuService {

    public List<MenuItem> menusFor(AuthUser user) {
        Map<String, MenuItem> menuByPath = new LinkedHashMap<>();
        for (MenuItem menu : allMenus()) {
            menuByPath.put(menu.getPath(), menu);
        }
        List<MenuItem> menus = new ArrayList<>();
        for (String role : List.of("ADMIN", "TEACHER", "STUDENT")) {
            if (user != null && user.hasRole(role)) {
                for (String path : rolePageMap().getOrDefault(role, List.of())) {
                    MenuItem menu = menuByPath.get(path);
                    if (menu != null && canAccess(user, menu) && menus.stream().noneMatch(item -> item.getPath().equals(path))) {
                        menus.add(menu);
                    }
                }
            }
        }
        return menus;
    }

    public Map<String, List<String>> rolePageMap() {
        Map<String, List<String>> data = new LinkedHashMap<>();
        // 管理员：概况 -> 核心业务 -> 基础数据 -> 系统管理
        data.put("ADMIN", List.of("/admin", "/question-bank", "/papers", "/exam/analysis", "/basic/data", "/system/users", "/system/roles", "/monitor/logs"));
        // 教师：概况 -> 核心业务 -> 基础数据
        data.put("TEACHER", List.of("/teacher", "/exam-tasks", "/reviews", "/teacher/analysis", "/teacher/students", "/question-bank", "/papers", "/basic/data"));
        // 学生：概况 -> 核心功能 -> 基础数据
        data.put("STUDENT", List.of("/student", "/student/exams", "/student/results", "/student/wrong-questions", "/basic/data"));
        return data;
    }

    private List<MenuItem> allMenus() {
        return List.of(
                // 管理员菜单 - 按使用频率排序
                new MenuItem("概况", "/admin", "DataAnalysis", List.of("ADMIN")),
                new MenuItem("题库管理", "/question-bank", "Collection", List.of("ADMIN", "TEACHER")),
                new MenuItem("试卷管理", "/papers", "Files", List.of("ADMIN", "TEACHER")),
                new MenuItem("成绩分析", "/exam/analysis", "PieChart", List.of("ADMIN")),
                new MenuItem("基础数据", "/basic/data", "Management", List.of("ADMIN", "TEACHER", "STUDENT")),
                new MenuItem("用户管理", "/system/users", "User", List.of("ADMIN")),
                new MenuItem("角色管理", "/system/roles", "Lock", List.of("ADMIN")),
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
}
