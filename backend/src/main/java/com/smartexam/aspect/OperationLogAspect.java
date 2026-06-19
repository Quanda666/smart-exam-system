package com.smartexam.aspect;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.TokenSession;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 自动记录控制器写操作到操作日志。
 * 认证和用户管理接口已有更精确的审计记录，这里跳过以避免重复写入。
 */
@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Pointcut("within(com.smartexam.controller..*) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void writeOperation() {
    }

    @AfterReturning("writeOperation()")
    public void logWrite() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/auth") || uri.startsWith("/api/system/users")) {
                return;
            }
            TokenSession session = AuthContext.getSession();
            if (session == null || session.getUser() == null) {
                return;
            }
            AuthUser user = session.getUser();
            operationLogService.record(user.getId(), user.getRealName(), actionOf(request.getMethod(), uri), uri, null);
        } catch (Exception ignored) {
            // 审计日志失败不能影响主业务流程。
        }
    }

    private String actionOf(String method, String uri) {
        String verb = switch (method) {
            case "POST" -> "新增";
            case "PUT" -> "更新";
            case "DELETE" -> "删除";
            default -> "操作";
        };
        return verb + moduleOf(uri);
    }

    private String moduleOf(String uri) {
        if (uri.contains("/basic/classes")) return "班级";
        if (uri.contains("/basic/subjects")) return "科目";
        if (uri.contains("/basic/knowledge-points")) return "知识点";
        if (uri.contains("/basic/notices")) return "公告";
        if (uri.contains("/questions")) return "题目";
        if (uri.contains("/papers")) return "试卷";
        if (uri.contains("/exams")) return "考试";
        if (uri.contains("/reviews")) return "阅卷";
        if (uri.contains("/monitor")) return "监控";
        return "数据";
    }
}
