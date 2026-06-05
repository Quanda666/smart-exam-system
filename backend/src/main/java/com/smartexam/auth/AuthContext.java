package com.smartexam.auth;

public final class AuthContext {

    private static final ThreadLocal<TokenSession> CURRENT_SESSION = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setSession(TokenSession session) {
        CURRENT_SESSION.set(session);
    }

    public static TokenSession getSession() {
        return CURRENT_SESSION.get();
    }

    public static TokenSession requireSession() {
        TokenSession session = CURRENT_SESSION.get();
        if (session == null) {
            throw new IllegalStateException("当前请求未携带有效登录态");
        }
        return session;
    }

    public static void clear() {
        CURRENT_SESSION.remove();
    }
}
