package com.bluewhale.sts.auth.context;

import com.bluewhale.sts.auth.common.AuthResultCode;

/**
 * 认证上下文的线程级入口 —— 业务代码拿"当前用户"的唯一入口。
 *
 * <p><b>为什么用 ThreadLocal 而不是把上下文当参数层层传递：</b>
 * 上下文是典型的**横切关注点**，从 Filter 到 Service 中间隔着 Controller、
 * Service、Mapper 若干层，逐层加参数会让每个方法签名都背上一个与自身职责无关的对象，
 * 且任何一层忘了传就断链。ThreadLocal 把"沿调用链携带"这件事交给运行时。
 *
 * <p><b>必须由 Filter 在 finally 里清理</b>（见 {@code AuthTokenFilter}）：
 * Tomcat 的线程是复用的，不清理会把上一个请求的用户身份泄露给下一个请求 ——
 * 这是 ThreadLocal 最危险的失效方式，且症状是"偶发地串号"，极难排查。
 */
public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    /** 写入上下文；传 null 等价于清理 */
    public static void set(AuthContext context) {
        if (context == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(context);
        }
    }

    /** 取上下文，未认证时返回 null */
    public static AuthContext get() {
        return HOLDER.get();
    }

    /**
     * 取上下文，未认证时抛 401。
     *
     * <p>供"一定在认证之后执行"的代码调用（如按当前用户过滤数据），
     * 这样就不必在每个调用点写 {@code if (ctx == null)}。
     */
    public static AuthContext require() {
        AuthContext context = HOLDER.get();
        if (context == null) {
            throw AuthResultCode.TOKEN_INVALID.businessException("当前请求未认证");
        }
        return context;
    }

    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }

    /** 当前登录用户主键，未认证返回 null */
    public static Long currentUserId() {
        AuthContext context = HOLDER.get();
        return context == null ? null : context.getUserId();
    }

    /** 当前租户主键，未认证返回 null */
    public static Long currentTenantId() {
        AuthContext context = HOLDER.get();
        return context == null ? null : context.getTenantId();
    }

    public static void clear() {
        HOLDER.remove();
    }

}
