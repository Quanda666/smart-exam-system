package com.smartexam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/health",
            "/api/ai/status",
            "/api/auth/login",
            "/api/auth/login-by-code",
            "/api/auth/send-login-code",
            "/api/auth/register",
            "/api/auth/register-options"
    );

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthFilter(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (isPublicRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = resolveToken(request);
            TokenSession session = tokenStore.findValid(token).orElse(null);
            if (session == null) {
                writeUnauthorized(response);
                return;
            }

            AuthContext.setSession(session);
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return true;
        }
        return PUBLIC_PATHS.stream().anyMatch(uri::equals);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return request.getHeader("X-Auth-Token");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("UNAUTHORIZED", "请先登录后再访问该接口")));
    }
}
