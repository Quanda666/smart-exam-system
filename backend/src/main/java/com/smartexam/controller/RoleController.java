package com.smartexam.controller;

import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.service.MenuService;
import com.smartexam.service.RoleAccessService;
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
        roleAccessService.requireRole("ADMIN");
        List<String> pages = menuService.updateRolePages(roleCode, body == null ? List.of() : body.getOrDefault("pages", List.of()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleCode", roleCode == null ? "" : roleCode.toUpperCase());
        result.put("pages", pages);
        return ApiResponse.ok("授权已保存", result);
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
