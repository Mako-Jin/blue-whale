package com.bluewhale.gateway.route;

import java.time.LocalDateTime;

/**
 * 路由业务属性覆盖规则（GW-10 Phase 2）：一条路由的停用开关与生效时间窗口。
 *
 * <p>{@code routeId} 必须与 Nacos/兜底路由 id 逐字一致；不存在的 id 为孤儿条目，
 * 由 {@code NacosRouteDefinitionRepository} 忽略并计指标，不改变 predicates/filters。</p>
 *
 * @param routeId       路由 ID
 * @param enabled       是否启用
 * @param effectiveFrom 生效起始时间；{@code null} 立即生效
 * @param effectiveTo   生效截止时间；{@code null} 长期生效
 * @param version       版本号：每次更新 +1（供快照变更识别）
 * @author BlueWhale
 */
public record RouteOverrideRule(String routeId, boolean enabled, LocalDateTime effectiveFrom,
                                LocalDateTime effectiveTo, long version) {

    /**
     * 判断在给定时刻是否生效：启用且落在生效窗口内
     *
     * @param now 判定时刻
     * @return 生效返回 {@code true}
     */
    public boolean isEffectiveAt(LocalDateTime now) {
        if (!enabled) {
            return false;
        }
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !now.isAfter(effectiveTo);
    }
}
