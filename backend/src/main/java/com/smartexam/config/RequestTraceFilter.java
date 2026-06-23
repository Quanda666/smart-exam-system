package com.smartexam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String RESPONSE_TIME_HEADER = "X-Response-Time-Ms";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    private final long slowWarningMs;

    public RequestTraceFilter(@Value("${smart-exam.request-tracing.slow-warning-ms:1000}") long slowWarningMs) {
        this.slowWarningMs = Math.max(1L, slowWarningMs);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = resolveRequestId(request);
        MDC.put(TRACE_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            response.setHeader(RESPONSE_TIME_HEADER, String.valueOf(elapsedMs));
            if (elapsedMs >= slowWarningMs) {
                log.warn("slow request method={} uri={} status={} durationMs={} thresholdMs={} remote={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMs,
                        slowWarningMs,
                        clientAddress(request));
            } else {
                log.info("request method={} uri={} status={} durationMs={} remote={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMs,
                        clientAddress(request));
            }
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String value = request.getHeader(REQUEST_ID_HEADER);
        if (value == null || value.isBlank()) {
            value = request.getHeader("X-Correlation-Id");
        }
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String normalized = value.trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma < 0 ? forwardedFor.trim() : forwardedFor.substring(0, comma).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
