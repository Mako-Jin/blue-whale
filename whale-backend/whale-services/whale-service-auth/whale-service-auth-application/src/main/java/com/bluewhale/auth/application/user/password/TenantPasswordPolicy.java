package com.bluewhale.auth.application.user.password;

/**
 * 租户级口令策略（REQ-SEC-023）：租户管理员可为本租户配置口令有效期与提前提醒天数。
 *
 * <p>未配置的租户回退到全局默认策略（{@code whale.auth.password.*}），因此本对象在应用层
 * 始终表示「生效值」，而不区分来源；是否需要区分由视图对象携带 {@code customized} 标记。</p>
 *
 * @param tenantId 租户 ID
 * @param maxAgeDays 口令有效期（天）
 * @param warnDays 到期前提醒天数
 * @author BlueWhale
 */
public record TenantPasswordPolicy(Long tenantId, int maxAgeDays, int warnDays) {
}
