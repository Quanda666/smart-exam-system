package com.smartexam.aspect;

import com.smartexam.auth.AuthContext;
import com.smartexam.auth.RequireRoles;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.ForbiddenException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RequireRolesAspect {

    @Around("@within(com.smartexam.auth.RequireRoles) || @annotation(com.smartexam.auth.RequireRoles)")
    public Object enforceRoles(ProceedingJoinPoint joinPoint) throws Throwable {
        RequireRoles requireRoles = resolveAnnotation(joinPoint);
        if (requireRoles == null) {
            return joinPoint.proceed();
        }

        AuthUser user = AuthContext.requireSession().getUser();
        String[] roles = requireRoles.value();
        if (roles.length == 0 || Arrays.stream(roles).anyMatch(user::hasRole)) {
            return joinPoint.proceed();
        }
        throw new ForbiddenException("当前账号角色为 " + user.getRoleLabel() + "，不能访问该接口");
    }

    private RequireRoles resolveAnnotation(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Method method = signature.getMethod();
        RequireRoles methodAnnotation = method.getAnnotation(RequireRoles.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        Object target = joinPoint.getTarget();
        return target == null ? null : target.getClass().getAnnotation(RequireRoles.class);
    }
}
