package com.bluewhale.auth.domain.tenant.repository;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;

import java.util.Optional;

/**
 * Tenant 聚合仓储端口（Wave 5.54 Phase 0）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * @author BlueWhale
 */
public interface TenantRepository {

    /**
     * 按租户编码查询租户
     *
     * @param tenantCode 租户编码
     * @return 租户聚合；不存在时返回空
     */
    Optional<Tenant> findByCode(String tenantCode);

    /**
     * 按租户 ID 查询租户
     *
     * @param id 租户 ID
     * @return 租户聚合；不存在时返回空
     */
    Optional<Tenant> findById(Long id);
}
