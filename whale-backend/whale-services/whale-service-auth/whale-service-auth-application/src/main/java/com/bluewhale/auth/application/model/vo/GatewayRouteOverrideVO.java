package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;

import java.time.LocalDateTime;

/**
 * 网关路由覆盖视图对象：承载网关过滤路由所需的全部业务属性（含停用与窗口外条目，
 * 生效判定由网关按当前时间执行，窗口随时间推移无需重新下发）。
 *
 * @param routeId       路由 ID（与 Nacos/兜底路由 id 逐字一致）
 * @param enabled       是否启用
 * @param effectiveFrom 生效起始时间；{@code null} 立即生效
 * @param effectiveTo   生效截止时间；{@code null} 长期生效
 * @param version       版本号：每次更新 +1
 * @author BlueWhale
 */
public record GatewayRouteOverrideVO(String routeId, boolean enabled, LocalDateTime effectiveFrom,
                                     LocalDateTime effectiveTo, long version) {

    /**
     * 由路由覆盖聚合转换为视图对象
     *
     * @param override 路由覆盖聚合
     * @return 路由覆盖视图对象
     */
    public static GatewayRouteOverrideVO from(GatewayRouteOverrideAggregate override) {
        return new GatewayRouteOverrideVO(override.getRouteId(), override.isEnabled(),
                override.getEffectiveFrom(), override.getEffectiveTo(), override.getVersion());
    }
}
