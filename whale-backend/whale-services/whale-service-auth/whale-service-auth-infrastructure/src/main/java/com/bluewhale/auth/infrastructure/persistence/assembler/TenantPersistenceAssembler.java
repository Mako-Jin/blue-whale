package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.TenantAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysTenantPo;

/**
 * Tenant 聚合与其持久化对象（PO）的转换器（Wave 5.54 Phase 1）。
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
    public static TenantAggregate toDomain(SysTenantPo po) {
        return TenantAggregate.reconstitute(po.getId(), po.getTenantCode(), po.getTenantName(), po.getTenantType(),
                po.getStatus(), po.getExpireTime(), po.getQuotaJson(), po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 Tenant 聚合所承载的字段
     *
     * @param tenant 租户聚合
     * @return 租户持久化对象
     */
    public static SysTenantPo toPo(TenantAggregate tenant) {
        SysTenantPo po = new SysTenantPo();
        po.setId(tenant.getId());
        po.setTenantCode(tenant.getTenantCode());
        po.setTenantName(tenant.getTenantName());
        po.setTenantType(tenant.getTenantType());
        po.setStatus(tenant.getStatus());
        po.setExpireTime(tenant.getExpireTime());
        po.setQuotaJson(tenant.getQuotaJson());
        po.setCreateTime(tenant.getCreateTime());
        po.setUpdateTime(tenant.getUpdateTime());
        return po;
    }
}
