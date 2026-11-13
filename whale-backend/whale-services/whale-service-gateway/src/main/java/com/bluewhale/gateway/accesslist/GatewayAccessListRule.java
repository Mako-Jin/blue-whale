package com.bluewhale.gateway.accesslist;

/**
 * 网关访问名单规则（远程下发项，白名单 / 黑名单共用结构）。
 *
 * <p>GW-08 租户维度：{@code scope=TENANT} 的白名单条目绑定 {@code tenantId}，
 * 仅对携带对应租户 accessToken 的请求生效；黑名单恒为平台级。</p>
 *
 * @param pathPattern 路径模式（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 或空表示不限
 * @param scope 作用域（{@link #SCOPE_PLATFORM} / {@link #SCOPE_TENANT}），空值视为平台级
 * @param tenantId 租户 ID（租户级 &gt;0；平台级为 0 或 {@code null}）
 * @author BlueWhale
 */
public record GatewayAccessListRule(String pathPattern, String httpMethod, String scope, Long tenantId) {

    /** HTTP 方法通配符：不限制请求方法 */
    public static final String METHOD_ANY = "*";

    /** 作用域：平台级 */
    public static final String SCOPE_PLATFORM = "PLATFORM";

    /** 作用域：租户级 */
    public static final String SCOPE_TENANT = "TENANT";

    /**
     * 构造平台级规则（兼容黑名单等无租户维度的名单）
     *
     * @param pathPattern 路径模式
     * @param httpMethod HTTP 方法
     * @return 平台级规则
     */
    public static GatewayAccessListRule platform(String pathPattern, String httpMethod) {
        return new GatewayAccessListRule(pathPattern, httpMethod, SCOPE_PLATFORM, null);
    }

    /**
     * 判断规则是否接受该请求方法
     *
     * @param method 实际请求方法（如 {@code GET}），可空
     * @return 规则方法为通配或与实际方法一致时返回 {@code true}
     */
    public boolean matchesMethod(String method) {
        if (httpMethod == null || httpMethod.isBlank() || METHOD_ANY.equals(httpMethod)) {
            return true;
        }
        return method != null && httpMethod.equalsIgnoreCase(method.trim());
    }

    /**
     * 判断是否为平台级规则（无作用域字段的历史条目亦视为平台级）
     *
     * @return 平台级返回 {@code true}
     */
    public boolean isPlatformScoped() {
        return scope == null || scope.isBlank() || SCOPE_PLATFORM.equals(scope);
    }

    /**
     * 判断是否为有效的租户级规则（绑定租户 ID 大于 0）
     *
     * @return 租户级返回 {@code true}
     */
    public boolean isTenantScoped() {
        return SCOPE_TENANT.equals(scope) && tenantId != null && tenantId > 0;
    }
}
