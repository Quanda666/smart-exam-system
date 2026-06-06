package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.dto.auth.RegisterRequest;
import com.smartexam.service.AuthService;
import com.smartexam.service.MenuService;
import com.smartexam.service.OperationLogService;
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
    private final OperationLogService operationLogService;

    public AuthController(AuthService authService, MenuService menuService, OperationLogService operationLogService) {
        this.authService = authService;
        this.menuService = menuService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        operationLogService.record(response.getUser().getId(), response.getUser().getRealName(),
                "登录系统", "认证", "角色：" + response.getUser().getRoleLabel());
        return ApiResponse.ok("登录成功", response);
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @GetMapping("/register-options")
    public ApiResponse<Map<String, Object>> registerOptions() {
        return ApiResponse.ok(authService.registerOptions());
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
