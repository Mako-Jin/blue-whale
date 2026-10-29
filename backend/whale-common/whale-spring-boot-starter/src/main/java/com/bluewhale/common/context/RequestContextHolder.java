package com.bluewhale.common.context;

/**
 * 当前线程请求上下文持有者：为业务代码提供与 Web 容器解耦的调用者身份读取入口。
 *
 * <p>由 {@code RequestContextFilter} 写入与清理；租户隔离仍以 {@code TenantContext} 为准，
 * 本上下文中的租户 ID 仅用于审计、日志等只读场景。</p>
 *
 * @author BlueWhale
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private RequestContextHolder() {
    }

    /**
     * 绑定当前线程请求上下文；传 {@code null} 或空上下文时解除绑定
     *
     * @param context 请求上下文，可为 {@code null}
     */
    public static void set(RequestContext context) {
        if (context == null || context.isEmpty()) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(context);
    }

    /**
     * 获取当前线程请求上下文
     *
     * @return 请求上下文；未绑定时返回 {@code null}
     */
    public static RequestContext get() {
        return HOLDER.get();
    }

    /**
     * 获取当前线程用户 ID
     *
     * @return 用户 ID；未绑定时返回 {@code null}
     */
    public static Long getUserId() {
        RequestContext context = HOLDER.get();
        return context == null ? null : context.userId();
    }

    /**
     * 获取当前线程用户名
     *
     * @return 用户名；未绑定时返回 {@code null}
     */
    public static String getUsername() {
        RequestContext context = HOLDER.get();
        return context == null ? null : context.username();
    }

    /**
     * 获取当前线程租户 ID
     *
     * @return 租户 ID；未绑定时返回 {@code null}
     */
    public static Long getTenantId() {
        RequestContext context = HOLDER.get();
        return context == null ? null : context.tenantId();
    }

    /**
     * 获取当前线程链路追踪 ID
     *
     * @return 链路追踪 ID；未绑定时返回 {@code null}
     */
    public static String getTraceId() {
        RequestContext context = HOLDER.get();
        return context == null ? null : context.traceId();
    }

    /**
     * 清理当前线程请求上下文
     */
    public static void clear() {
        HOLDER.remove();
    }
}
