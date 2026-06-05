package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.service.AuthService;
import com.smartexam.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;

    public AuthController(AuthService authService, MenuService menuService) {
        this.authService = authService;
        this.menuService = menuService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        return ApiResponse.ok(authService.buildCurrentResponse(AuthContext.requireSession().getUser()));
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuItem>> menus() {
        return ApiResponse.ok(menuService.menusFor(AuthContext.requireSession().getUser()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return ApiResponse.ok("退出成功", Map.of("loggedOut", true));
    }

    @GetMapping("/demo-users")
    public ApiResponse<List<Map<String, Object>>> demoUsers() {
        return ApiResponse.ok(authService.demoUsers());
    }

    @GetMapping("/access-matrix")
    public ApiResponse<Map<String, List<String>>> accessMatrix() {
        return ApiResponse.ok(menuService.rolePageMap());
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return request.getHeader("X-Auth-Token");
    }
}
