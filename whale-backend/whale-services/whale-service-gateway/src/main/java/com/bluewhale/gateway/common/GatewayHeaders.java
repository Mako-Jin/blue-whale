package com.bluewhale.gateway.common;

/**
 * 网关内部统一使用的请求头常量。
 *
 * <p>网关为 WebFlux 栈，不能引入 Servlet 栈的 {@code whale-common} starter（会与 WebFlux 冲突），
 * 因此这些头名在网关内独立定义；但<b>网关内部必须唯一</b>：鉴权过滤器写入、权限码头过滤器读取，
 * 两侧各自写字面量时任何一次改名都会造成「权限码头读不到 userId」的静默故障。</p>
 *
 * @author BlueWhale
 */
public final class GatewayHeaders {

    /** 网关注入的用户 ID 头 */
    public static final String USER_ID = "X-User-Id";

    /** 网关注入的用户名头 */
    public static final String USERNAME = "X-Username";

    /** 网关注入的用户角色头（逗号分隔） */
    public static final String USER_ROLES = "X-User-Roles";

    /** 网关注入的租户 ID 头 */
    public static final String TENANT_ID = "X-Tenant-Id";

    /**
     * 私有构造器：常量类，禁止实例化
     */
    private GatewayHeaders() {
    }
}
