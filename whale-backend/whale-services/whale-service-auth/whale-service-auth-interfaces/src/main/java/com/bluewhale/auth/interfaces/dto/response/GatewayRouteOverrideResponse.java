package com.bluewhale.auth.interfaces.dto.response;

import java.time.LocalDateTime;

/**
 * 网关路由覆盖管理响应（含停用与窗口外条目）。
 *
 * @param routeId       路由 ID
 * @param enabled       是否启用
 * @param effectiveFrom 生效起始时间；{@code null} 立即生效
 * @param effectiveTo   生效截止时间；{@code null} 长期生效
 * @param version       版本号
 * @param remark        备注（启停原因）
 * @param createTime    创建时间
 * @param updateTime    更新时间
 * @author BlueWhale
 */
public record GatewayRouteOverrideResponse(String routeId, boolean enabled, LocalDateTime effectiveFrom,
                                           LocalDateTime effectiveTo, long version, String remark,
                                           LocalDateTime createTime, LocalDateTime updateTime) {
}
