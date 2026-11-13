package com.bluewhale.auth.application.model.command;

import java.time.LocalDateTime;

/**
 * 网关路由覆盖新增/更新命令（管理端写操作入参，路由 ID 由路径参数单独传递）。
 *
 * @param enabled       是否启用；{@code null} 更新时保持原值
 * @param effectiveFrom 生效起始时间；{@code null} 更新时保持原值
 * @param effectiveTo   生效截止时间；{@code null} 更新时保持原值
 * @param remark        备注；{@code null} 更新时保持原值
 * @author BlueWhale
 */
public record SaveGatewayRouteOverrideCommand(Boolean enabled, LocalDateTime effectiveFrom,
                                              LocalDateTime effectiveTo, String remark) {
}
