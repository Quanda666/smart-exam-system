package com.smartexam.controller;

import com.smartexam.auth.RequireRoles;
import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.service.MenuService;
import com.smartexam.service.OperationLogService;
import com.smartexam.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/roles")
@RequireRoles("ADMIN")
public class RoleController {

    private final UserService userService;
    private final MenuService menuService;
    private final OperationLogService operationLogService;

    public RoleController(UserService userService, MenuService menuService, OperationLogService operationLogService) {
        this.userService = userService;
        this.menuService = menuService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        Map<String, List<String>> pageMap = menuService.rolePageMap();
        List<Map<String, Object>> availablePages = menuService.allMenuItems().stream()
                .map(this::toPageOption)
                .toList();
        List<Map<String, Object>> roles = userService.listRoles();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            Map<String, Object> item = new LinkedHashMap<>(role);
            String roleCode = String.valueOf(role.get("roleCode"));
            item.put("pages", pageMap.getOrDefault(roleCode, List.of()));
            item.put("availablePages", availablePages.stream()
                    .filter(page -> ((List<?>) page.getOrDefault("roles", List.of())).contains(roleCode))
                    .toList());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @PutMapping("/{roleCode}/pages")
    public ApiResponse<Map<String, Object>> updatePages(@PathVariable String roleCode,
                                                        @RequestBody Map<String, List<String>> body) {
        AuthUser admin = currentUser();
        String normalizedRoleCode = roleCode == null ? "" : roleCode.toUpperCase();
        List<String> beforePages = menuService.rolePageMap().getOrDefault(normalizedRoleCode, List.of());
        List<String> pages = menuService.updateRolePages(roleCode, body == null ? List.of() : body.getOrDefault("pages", List.of()));
        Long operationLogId = operationLogService.record(admin.getId(), admin.getRealName(),
                "UPDATE_ROLE_PAGES", "ROLE#" + normalizedRoleCode,
                "before=" + String.join(",", beforePages) + "; after=" + String.join(",", pages));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", normalizedRoleCode);
        result.put("pages", pages);
        result.put("operationLogId", operationLogId);
        return ApiResponse.ok("授权已保存", result);
    }

    private AuthUser currentUser() {
        return AuthContext.requireSession().getUser();
    }

    private Map<String, Object> toPageOption(MenuItem menu) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", menu.getTitle());
        item.put("path", menu.getPath());
        item.put("icon", menu.getIcon());
        item.put("roles", menu.getRoles());
        return item;
    }
}
