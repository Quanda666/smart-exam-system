package com.smartexam.service;

import com.smartexam.auth.AuthContext;
import com.smartexam.dto.auth.AuthUser;
import com.smartexam.exception.ForbiddenException;
import org.springframework.stereotype.Service;

@Service
public class RoleAccessService {

    /** 要求当前请求已登录（任意角色），返回当前用户。通知等全角色共用接口用此校验。 */
    public AuthUser requireLogin() {
        return AuthContext.requireSession().getUser();
    }

    public AuthUser requireRole(String role) {
        AuthUser user = AuthContext.requireSession().getUser();
        if (!user.hasRole(role)) {
            throw new ForbiddenException("当前账号角色为" + user.getRoleLabel() + "，不能访问" + role + "专属接口");
        }
        return user;
    }

    public AuthUser requireAnyRole(String... roles) {
        AuthUser user = AuthContext.requireSession().getUser();
        for (String role : roles) {
            if (user.hasRole(role)) {
                return user;
            }
        }
        throw new ForbiddenException("当前账号角色为" + user.getRoleLabel() + "，不能访问该基础资料接口");
    }
}
