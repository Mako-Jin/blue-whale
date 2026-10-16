package com.bluewhale.auth.sdk;

/**
 * 当前登录用户上下文占位
 *
 * @author BlueWhale
 */
public final class UserContext {

    private static final ThreadLocal<Object> USER_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUser(Object user) {
        USER_HOLDER.set(user);
    }

    public static Object getUser() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
