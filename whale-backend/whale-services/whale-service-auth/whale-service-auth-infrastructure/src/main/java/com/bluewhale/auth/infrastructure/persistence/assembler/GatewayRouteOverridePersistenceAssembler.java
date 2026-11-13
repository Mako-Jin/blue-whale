package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.GatewayRouteOverrideAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayRouteOverridePo;

/**
 * 网关路由覆盖聚合与其持久化对象（PO）的转换器。
 *
 * @author BlueWhale
 */
public final class GatewayRouteOverridePersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GatewayRouteOverridePersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 覆盖持久化对象
     * @return 覆盖聚合
     */
    public static GatewayRouteOverrideAggregate toDomain(SysGatewayRouteOverridePo po) {
        return GatewayRouteOverrideAggregate.reconstitute(po.getRouteId(), po.getEnabled(),
                po.getEffectiveFrom(), po.getEffectiveTo(), po.getVersion(), po.getRemark(),
                po.getCreateTime(), po.getUpdateTime());
    }

    /**
     * 领域对象 → PO
     *
     * @param override 覆盖聚合
     * @return 覆盖持久化对象
     */
    public static SysGatewayRouteOverridePo toPo(GatewayRouteOverrideAggregate override) {
        SysGatewayRouteOverridePo po = new SysGatewayRouteOverridePo();
        po.setRouteId(override.getRouteId());
        po.setEnabled(override.getStatus());
        po.setEffectiveFrom(override.getEffectiveFrom());
        po.setEffectiveTo(override.getEffectiveTo());
        po.setVersion(override.getVersion());
        po.setRemark(override.getRemark());
        po.setCreateTime(override.getCreateTime());
        po.setUpdateTime(override.getUpdateTime());
        return po;
    }
}
