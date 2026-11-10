package com.bluewhale.auth.application.auth.result;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;

/**
 * 「我的租户」列表项结果。
 *
 * @param tenantId 租户 ID
 * @param tenantCode 租户编码
 * @param tenantName 租户名称
 * @param current 是否为当前令牌所在租户
 * @author BlueWhale
 */
public record TenantResult(Long tenantId, String tenantCode, String tenantName, boolean current) {

    /**
     * 由租户聚合构建结果
     *
     * @param tenant 租户聚合
     * @param current 是否为当前租户
     * @return 租户结果
     */
    public static TenantResult from(Tenant tenant, boolean current) {
        return new TenantResult(tenant.getId(), tenant.getTenantCode(), tenant.getTenantName(), current);
    }
}
