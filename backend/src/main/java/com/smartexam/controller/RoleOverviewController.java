package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.RoleOverviewResponse;
import com.smartexam.service.RoleAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RoleOverviewController {

    private final RoleAccessService roleAccessService;

    public RoleOverviewController(RoleAccessService roleAccessService) {
        this.roleAccessService = roleAccessService;
    }

    @GetMapping("/admin/overview")
    public ApiResponse<RoleOverviewResponse> adminOverview() {
        AuthUser user = roleAccessService.requireRole("ADMIN");
        return ApiResponse.ok(new RoleOverviewResponse(
                "ADMIN",
                "管理员工作台",
                user.getRealName() + "可管理用户、角色、系统日志和全局考试分析。",
                List.of(
                        RoleOverviewResponse.card("用户总览", 3, "阶段 2 演示账号覆盖三类角色"),
                        RoleOverviewResponse.card("基础资料", "已接入", "班级、科目、知识点、公告"),
                        RoleOverviewResponse.card("权限边界", "已启用", "后端接口按角色校验")
                ),
                List.of("维护班级与科目", "发布系统公告", "进入题库管理阶段")
        ));
    }

    @GetMapping("/teacher/overview")
    public ApiResponse<RoleOverviewResponse> teacherOverview() {
        AuthUser user = roleAccessService.requireRole("TEACHER");
        return ApiResponse.ok(new RoleOverviewResponse(
                "TEACHER",
                "教师工作台",
                user.getRealName() + "可维护题库、创建试卷、发布考试并完成阅卷。",
                List.of(
                        RoleOverviewResponse.card("科目知识点", "可维护", "阶段 3 已接入基础资料接口"),
                        RoleOverviewResponse.card("试卷管理", "阶段 5", "支持手动组卷与规则组卷"),
                        RoleOverviewResponse.card("阅卷入口", "预留", "阶段 7 接入主观题批阅")
                ),
                List.of("维护科目知识点", "创建题库题目", "发布考试任务")
        ));
    }

    @GetMapping("/student/overview")
    public ApiResponse<RoleOverviewResponse> studentOverview() {
        AuthUser user = roleAccessService.requireRole("STUDENT");
        return ApiResponse.ok(new RoleOverviewResponse(
                "STUDENT",
                "学生首页",
                user.getRealName() + "可查看待考任务、进入在线答题、查询成绩与错题。",
                List.of(
                        RoleOverviewResponse.card("系统公告", "可查看", "阶段 3 已接入公告列表"),
                        RoleOverviewResponse.card("成绩查询", "预留", "阶段 8 展示成绩与排名"),
                        RoleOverviewResponse.card("错题反馈", "预留", "阶段 8 接入学习反馈")
                ),
                List.of("查看系统公告", "参加限时考试", "查看错题本与学习建议")
        ));
    }
}
