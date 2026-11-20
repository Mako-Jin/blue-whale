package com.bluewhale.gateway.common;

import com.bluewhale.security.contract.GatewayContract;

/**
 * 网关内部统一使用的请求头常量（值取自 {@link GatewayContract.Headers}）。
 *
 * <p>网关为 WebFlux 栈，不能引入 Servlet 栈的 {@code whale-common} starter（会与 WebFlux 冲突），
 * 因此这些头名在网关内以常量形式集中定义；但<b>网关内部必须唯一</b>：鉴权过滤器写入、
 * 权限码头过滤器读取，两侧各自写字面量时任何一次改名都会造成「权限码头读不到 userId」的静默故障。
 * 跨服务契约（与 auth / 下游服务共享）统一引用 {@link GatewayContract}。</p>
 *
 * @author BlueWhale
 */
public final class GatewayHeaders {

    /** 网关注入的用户 ID 头 */
    public static final String USER_ID = GatewayContract.Headers.USER_ID;

    /** 网关注入的用户名头 */
    public static final String USERNAME = GatewayContract.Headers.USERNAME;

    /** 网关注入的用户角色头（逗号分隔） */
    public static final String USER_ROLES = GatewayContract.Headers.USER_ROLES;

    /** 网关注入的租户 ID 头 */
    public static final String TENANT_ID = GatewayContract.Headers.TENANT_ID;

    /** 网关注入的权限码头（逗号分隔） */
    public static final String PERMISSION_CODES = GatewayContract.Headers.PERMISSION_CODES;

    /** 网关注入的归一化语言头 */
    public static final String LANGUAGE = GatewayContract.Headers.LANGUAGE;

    /**
     * 私有构造器：常量类，禁止实例化
     */
    private GatewayHeaders() {
    }
}
