package com.bluewhale.auth.sdk;

/**
 * 当前登录用户上下文占位
 *
 * @author BlueWhale
 */
public final class UserContext {

    private static final ThreadLocal<Object> USER_HOLDER = new ThreadLocal<>();

    /**
     * 私有构造器，禁止实例化
     */
    private UserContext() {
    }

    /**
     * 设置当前线程登录用户
     *
     * @param user 登录用户对象
     */
    public static void setUser(Object user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前线程登录用户
     *
     * @return 登录用户对象，未设置时返回 null
     */
    public static Object getUser() {
        return USER_HOLDER.get();
    }

    /**
     * 清理当前线程登录用户
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
}
