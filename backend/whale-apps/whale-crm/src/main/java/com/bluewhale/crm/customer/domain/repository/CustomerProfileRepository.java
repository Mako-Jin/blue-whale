package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.crm.customer.domain.model.CustomerProfile;

import java.util.Optional;

/**
 * 客户画像仓储。
 *
 * @author BlueWhale
 */
public interface CustomerProfileRepository {

    /**
     * 按客户查询画像。
     *
     * @param customerId 客户 ID
     * @return 画像
     */
    Optional<CustomerProfile> findByCustomerId(Long customerId);

    /**
     * 保存画像（存在则更新，不存在则新增）。
     *
     * @param profile 画像
     */
    void save(CustomerProfile profile);

    /**
     * 将画像整体迁移到目标客户（合并场景，只更新归属，不新建）。
     *
     * @param fromCustomerId 源客户 ID
     * @param toCustomerId 目标客户 ID
     * @return 更新行数
     */
    int reassignCustomer(Long fromCustomerId, Long toCustomerId);
}
