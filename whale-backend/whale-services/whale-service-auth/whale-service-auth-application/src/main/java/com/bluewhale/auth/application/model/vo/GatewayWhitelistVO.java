package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;

/**
 * 网关白名单视图对象：承载网关匹配所需的路径、方法、作用域与租户维度。
 *
 * @param pathPattern 免鉴权路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 表示不限
 * @param scope 作用域（PLATFORM / TENANT）
 * @param tenantId 租户 ID：0 平台级 / &gt;0 绑定租户
 * @author BlueWhale
 */
public record GatewayWhitelistVO(String pathPattern, String httpMethod, String scope, Long tenantId) {

    /**
     * 由白名单聚合转换为视图对象
     *
     * @param rule 白名单聚合
     * @return 白名单视图对象
     */
    public static GatewayWhitelistVO from(GatewayWhitelistAggregate rule) {
        return new GatewayWhitelistVO(rule.getPathPattern(), rule.getHttpMethod(), rule.getScope(), rule.getTenantId());
    }
}
