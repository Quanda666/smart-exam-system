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
        List<MenuItem> menus = new ArrayList<>();
        for (MenuItem menu : allMenus()) {
            if (canAccess(user, menu)) {
                menus.add(menu);
            }
        }
        return menus;
    }

    public Map<String, List<String>> rolePageMap() {
        Map<String, List<String>> data = new LinkedHashMap<>();
        data.put("ADMIN", List.of("/admin", "/basic/classes", "/basic/subjects", "/basic/knowledge-points", "/basic/notices", "/question-bank", "/papers", "/system/users", "/system/roles", "/monitor/logs", "/exam/analysis"));
        data.put("TEACHER", List.of("/teacher", "/basic/subjects", "/basic/knowledge-points", "/basic/notices", "/question-bank", "/papers", "/exam-tasks", "/reviews", "/teacher/analysis"));
        data.put("STUDENT", List.of("/student", "/basic/notices", "/student/exams", "/student/results", "/student/wrong-questions"));
        return data;
    }

    private List<MenuItem> allMenus() {
        return List.of(
                new MenuItem("管理员首页", "/admin", "DataAnalysis", List.of("ADMIN")),
                new MenuItem("班级管理", "/basic/classes", "OfficeBuilding", List.of("ADMIN")),
                new MenuItem("科目管理", "/basic/subjects", "Management", List.of("ADMIN", "TEACHER")),
                new MenuItem("知识点管理", "/basic/knowledge-points", "Connection", List.of("ADMIN", "TEACHER")),
                new MenuItem("公告管理", "/basic/notices", "Bell", List.of("ADMIN", "TEACHER", "STUDENT")),
                new MenuItem("题库管理", "/question-bank", "Collection", List.of("ADMIN", "TEACHER")),
                new MenuItem("用户管理", "/system/users", "User", List.of("ADMIN")),
                new MenuItem("角色管理", "/system/roles", "Lock", List.of("ADMIN")),
                new MenuItem("系统日志", "/monitor/logs", "Document", List.of("ADMIN")),
                new MenuItem("全局成绩分析", "/exam/analysis", "PieChart", List.of("ADMIN")),
                new MenuItem("教师首页", "/teacher", "Notebook", List.of("TEACHER")),
                new MenuItem("试卷管理", "/papers", "Files", List.of("ADMIN", "TEACHER")),
                new MenuItem("考试任务", "/exam-tasks", "Calendar", List.of("TEACHER")),
                new MenuItem("阅卷管理", "/reviews", "EditPen", List.of("TEACHER")),
                new MenuItem("教师成绩分析", "/teacher/analysis", "TrendCharts", List.of("TEACHER")),
                new MenuItem("学生首页", "/student", "House", List.of("STUDENT")),
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
