package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;

/**
 * 网关黑名单视图对象：仅承载网关匹配所需的路径与方法。
 *
 * @param pathPattern 封禁路径（Ant 通配）
 * @param httpMethod HTTP 方法，{@code *} 表示不限
 * @author BlueWhale
 */
public record GatewayBlacklistVO(String pathPattern, String httpMethod) {

    /**
     * 由黑名单聚合转换为视图对象
     *
     * @param rule 黑名单聚合
     * @return 黑名单视图对象
     */
    public static GatewayBlacklistVO from(GatewayBlacklistAggregate rule) {
        return new GatewayBlacklistVO(rule.getPathPattern(), rule.getHttpMethod());
    }
}
