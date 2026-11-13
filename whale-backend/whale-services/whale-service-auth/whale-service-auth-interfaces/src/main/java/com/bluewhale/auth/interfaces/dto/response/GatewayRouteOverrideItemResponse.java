package com.bluewhale.auth.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * 网关路由覆盖下发项响应（仅供 whale-service-gateway 定时拉取）。
 *
 * <p>下发全部条目（含停用与窗口外条目），生效判定由网关按当前时间执行，
 * 时间窗口随时间推移自动进入/退出生效态，无需重新下发。</p>
 *
 * @param routeId       路由 ID（与 Nacos/兜底路由 id 逐字一致）
 * @param enabled       是否启用
 * @param effectiveFrom 生效起始时间；{@code null} 立即生效
 * @param effectiveTo   生效截止时间；{@code null} 长期生效
 * @param version       版本号：每次更新 +1
 * @author BlueWhale
 */
public record GatewayRouteOverrideItemResponse(String routeId, boolean enabled, LocalDateTime effectiveFrom,
                                               LocalDateTime effectiveTo, long version) {
}
