package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.GatewayWhitelistAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayWhitelistPo;

/**
 * 网关白名单聚合与其持久化对象（PO）的转换器。
 *
 * @author BlueWhale
 */
public final class GatewayWhitelistPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GatewayWhitelistPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 白名单持久化对象
     * @return 白名单聚合
     */
    public static GatewayWhitelistAggregate toDomain(SysGatewayWhitelistPo po) {
        return GatewayWhitelistAggregate.reconstitute(po.getId(), po.getPathPattern(), po.getHttpMethod(),
                po.getScope(), po.getStatus(), po.getSortNo());
    }
}
