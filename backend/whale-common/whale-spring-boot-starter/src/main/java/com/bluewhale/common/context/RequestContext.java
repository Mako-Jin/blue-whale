package com.bluewhale.common.context;

/**
 * 请求上下文快照：承载网关可信注入的调用者身份与链路信息。
 *
 * <p>由 {@code RequestContextFilter} 在请求进入时解析请求头并绑定到当前线程，
 * 请求结束立即清理；业务代码只读，不得跨线程直接引用（异步场景需显式快照传递）。</p>
 *
 * @param userId 调用者用户 ID；未登录或缺失时为 {@code null}
 * @param username 调用者用户名；缺失时为 {@code null}
 * @param tenantId 租户 ID；缺失或非法时为 {@code null}
 * @param traceId 链路追踪 ID；缺失时为 {@code null}
 * @author BlueWhale
 */
public record RequestContext(Long userId, String username, Long tenantId, String traceId) {

    /**
     * 判断上下文是否不含任何有效信息
     *
     * @return 四个字段全部为空时返回 {@code true}
     */
    public boolean isEmpty() {
        return userId == null && username == null && tenantId == null && traceId == null;
    }
}
