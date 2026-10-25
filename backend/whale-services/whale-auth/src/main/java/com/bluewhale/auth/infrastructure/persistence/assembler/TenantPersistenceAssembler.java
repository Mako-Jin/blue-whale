package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.entity.SysTenant;

/**
 * Tenant 聚合与其持久化对象（PO）的转换器（Wave 5.54 Phase 0）。
 *
 * @author BlueWhale
 */
public final class TenantPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private TenantPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 租户持久化对象
     * @return 租户聚合
     */
    public static Tenant toDomain(SysTenant po) {
        return Tenant.reconstitute(po.getId(), po.getTenantCode(), po.getTenantName(), po.getStatus());
    }
}
