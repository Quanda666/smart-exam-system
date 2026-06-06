package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.service.MenuService;
import com.smartexam.service.RoleAccessService;
import com.smartexam.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/roles")
public class RoleController {

    private final UserService userService;
    private final MenuService menuService;
    private final RoleAccessService roleAccessService;

    public RoleController(UserService userService, MenuService menuService, RoleAccessService roleAccessService) {
        this.userService = userService;
        this.menuService = menuService;
        this.roleAccessService = roleAccessService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        roleAccessService.requireRole("ADMIN");
        Map<String, List<String>> pageMap = menuService.rolePageMap();
        List<Map<String, Object>> roles = userService.listRoles();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            Map<String, Object> item = new LinkedHashMap<>(role);
            String roleCode = String.valueOf(role.get("roleCode"));
            item.put("pages", pageMap.getOrDefault(roleCode, List.of()));
            result.add(item);
        }
        return ApiResponse.ok(result);
    }
}
