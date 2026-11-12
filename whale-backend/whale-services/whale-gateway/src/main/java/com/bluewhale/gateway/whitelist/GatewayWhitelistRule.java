package com.bluewhale.gateway.whitelist;

/**
 * 网关免鉴权白名单规则（远程下发项）。
 *
 * @param pathPattern 免鉴权路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 或空表示不限
 * @author BlueWhale
 */
public record GatewayWhitelistRule(String pathPattern, String httpMethod) {

    /** HTTP 方法通配符：不限制请求方法 */
    public static final String METHOD_ANY = "*";

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
}
