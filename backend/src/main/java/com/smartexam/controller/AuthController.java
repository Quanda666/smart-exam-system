package com.smartexam.controller;

import com.smartexam.auth.AuthContext;
import com.smartexam.common.ApiResponse;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.dto.auth.BindEmailRequest;
import com.smartexam.dto.auth.ChangePasswordRequest;
import com.smartexam.dto.auth.LoginByCodeRequest;
import com.smartexam.dto.auth.LoginRequest;
import com.smartexam.dto.auth.LoginResponse;
import com.smartexam.dto.auth.MenuItem;
import com.smartexam.dto.auth.RegisterRequest;
import com.smartexam.dto.auth.SendBindCodeRequest;
import com.smartexam.dto.auth.SendLoginCodeRequest;
import com.smartexam.dto.auth.UpdateProfileRequest;
import com.smartexam.service.AuthService;
import com.smartexam.service.MenuService;
import com.smartexam.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String AUTH_TARGET = "认证";

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
        try {
            LoginResponse response = authService.login(request);
            operationLogService.record(response.getUser().getId(), response.getUser().getRealName(),
                    "登录系统", AUTH_TARGET, "角色: " + response.getUser().getRoleLabel());
            return ApiResponse.ok("登录成功", response);
        } catch (RuntimeException ex) {
            recordLoginFailure(request.getUsername(), "LOGIN_FAILED", ex);
            throw ex;
        }
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        recordAuthSecurityEvent(null, request.getUsername(), "REGISTER_REQUEST",
                "role: " + request.getRoleType() + "; realName: " + request.getRealName());
        return ApiResponse.ok("注册成功", response);
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
        AuthUser user = AuthContext.getSession() == null ? null : AuthContext.getSession().getUser();
        authService.logout(resolveToken(request));
        if (user != null) {
            recordAuthSecurityEvent(user, user.getRealName(), "LOGOUT", "userId: " + user.getId());
        }
        return ApiResponse.ok("退出成功", Map.of("loggedOut", true));
    }

    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        authService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        recordAuthSecurityEvent(user, user.getRealName(), "PASSWORD_CHANGED", "userId: " + user.getId());
        return ApiResponse.ok("密码修改成功", Map.of("changed", true));
    }

    @PostMapping("/send-login-code")
    public ApiResponse<Map<String, Object>> sendLoginCode(@Valid @RequestBody SendLoginCodeRequest request) {
        authService.sendLoginCode(request.getEmail());
        recordAuthSecurityEvent(null, request.getEmail(), "LOGIN_CODE_SENT", "email: " + request.getEmail());
        return ApiResponse.ok("验证码已发送", Map.of("sent", true));
    }

    @PostMapping("/login-by-code")
    public ApiResponse<LoginResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest request) {
        try {
            LoginResponse response = authService.loginByCode(request.getEmail(), request.getCode());
            operationLogService.record(response.getUser().getId(), response.getUser().getRealName(),
                    "验证码登录", AUTH_TARGET, "邮箱: " + request.getEmail());
            return ApiResponse.ok("登录成功", response);
        } catch (RuntimeException ex) {
            recordLoginFailure(request.getEmail(), "CODE_LOGIN_FAILED", ex);
            throw ex;
        }
    }

    @PostMapping("/send-bind-code")
    public ApiResponse<Map<String, Object>> sendBindCode(@Valid @RequestBody SendBindCodeRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        authService.sendBindCode(request.getEmail(), user.getId());
        recordAuthSecurityEvent(user, user.getRealName(), "BIND_CODE_SENT", "email: " + request.getEmail());
        return ApiResponse.ok("验证码已发送", Map.of("sent", true));
    }

    @PostMapping("/bind-email")
    public ApiResponse<Map<String, Object>> bindEmail(@Valid @RequestBody BindEmailRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        authService.bindEmail(user.getId(), request.getEmail(), request.getCode());
        recordAuthSecurityEvent(user, user.getRealName(), "EMAIL_BOUND", "email: " + request.getEmail());
        return ApiResponse.ok("邮箱绑定成功", Map.of("bound", true, "email", request.getEmail()));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        AuthUser user = AuthContext.requireSession().getUser();
        authService.updateProfile(user.getId(), request.getRealName(), request.getPhone());
        recordAuthSecurityEvent(user, user.getRealName(), "PROFILE_UPDATED", "userId: " + user.getId());
        return ApiResponse.ok("个人资料已更新", Map.of("updated", true));
    }

    @GetMapping("/login-logs")
    public ApiResponse<List<Map<String, Object>>> loginLogs() {
        AuthUser user = AuthContext.requireSession().getUser();
        return ApiResponse.ok(authService.listLoginLogs(user.getId(), 10));
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

    private void recordLoginFailure(String account, String action, RuntimeException ex) {
        String safeAccount = account == null || account.isBlank() ? "unknown" : account.trim();
        String reason = ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
        operationLogService.record(null, safeAccount, action, AUTH_TARGET,
                "account: " + safeAccount + "; reason: " + reason);
    }

    private void recordAuthSecurityEvent(AuthUser user, String operatorName, String action, String detail) {
        operationLogService.record(user == null ? null : user.getId(), operatorName, action, AUTH_TARGET, detail);
    }
}
