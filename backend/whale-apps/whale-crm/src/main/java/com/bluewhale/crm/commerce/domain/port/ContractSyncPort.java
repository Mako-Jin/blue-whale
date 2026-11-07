package com.bluewhale.crm.commerce.domain.port;

import java.util.List;

/**
 * 合同投影同步端口。
 *
 * @author BlueWhale
 */
public interface ContractSyncPort {

    /**
     * 按客户查询合同投影。
     *
     * @param customerId CRM 客户 ID
     * @return 合同投影列表；无合同返回空列表
     */
    List<ContractProjection> listByCustomer(Long customerId);
}
