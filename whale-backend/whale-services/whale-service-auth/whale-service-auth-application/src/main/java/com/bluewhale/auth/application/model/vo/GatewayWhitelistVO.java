package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;

/**
 * 网关白名单视图对象：仅承载网关匹配所需的路径与方法。
 *
 * @param pathPattern 免鉴权路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 表示不限
 * @author BlueWhale
 */
public record GatewayWhitelistVO(String pathPattern, String httpMethod) {

    /**
     * 由白名单聚合转换为视图对象
     *
     * @param rule 白名单聚合
     * @return 白名单视图对象
     */
    public static GatewayWhitelistVO from(GatewayWhitelistAggregate rule) {
        return new GatewayWhitelistVO(rule.getPathPattern(), rule.getHttpMethod());
    }
}
