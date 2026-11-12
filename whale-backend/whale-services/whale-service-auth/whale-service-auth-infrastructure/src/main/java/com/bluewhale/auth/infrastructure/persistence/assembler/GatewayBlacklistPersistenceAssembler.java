package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.GatewayBlacklistAggregate;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayBlacklistPo;

/**
 * 网关黑名单聚合与其持久化对象（PO）的转换器。
 *
 * @author BlueWhale
 */
public final class GatewayBlacklistPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GatewayBlacklistPersistenceAssembler() {
    }

    /**
     * PO → 领域对象
     *
     * @param po 黑名单持久化对象
     * @return 黑名单聚合
     */
    public static GatewayBlacklistAggregate toDomain(SysGatewayBlacklistPo po) {
        return GatewayBlacklistAggregate.reconstitute(po.getId(), po.getPathPattern(), po.getHttpMethod(),
                po.getScope(), po.getStatus(), po.getSortNo());
    }
}
