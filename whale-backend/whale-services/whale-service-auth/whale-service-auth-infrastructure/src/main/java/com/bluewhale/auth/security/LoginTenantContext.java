package com.bluewhale.auth.security;

/**
 * 登录/认证链路的租户上下文（Wave 5.54 Phase 0）。
 *
 * <p>{@link org.springframework.security.core.userdetails.UserDetailsService} 只接收用户名，
 * 无法携带租户；因此由登录、刷新、JWT 认证三处在调用前绑定租户，调用后立即清理，
 * 避免租户串线程。</p>
 *
 * @author BlueWhale
 */
public final class LoginTenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    /**
     * 构造器：工具类，禁止实例化
     */
    private LoginTenantContext() {
    }

    /**
     * 绑定当前线程租户
     *
     * @param tenantId 租户 ID
     */
    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    /**
     * 读取当前线程租户
     *
     * @return 租户 ID；未绑定时返回 {@code null}
     */
    public static Long current() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程租户，必须在调用方 {@code finally} 中执行
     */
    public static void clear() {
        CURRENT.remove();
    }
}
