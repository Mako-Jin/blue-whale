package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.crm.customer.domain.model.CustomerAddress;

import java.util.List;
import java.util.Optional;

/**
 * 客户地址仓储。
 *
 * @author BlueWhale
 */
public interface CustomerAddressRepository {

    /**
     * 查询客户全部地址。
     *
     * @param customerId 客户 ID
     * @return 地址列表
     */
    List<CustomerAddress> list(Long customerId);

    /**
     * 按主键查询地址。
     *
     * @param id 主键
     * @return 地址
     */
    Optional<CustomerAddress> findById(Long id);

    /**
     * 新增地址。
     *
     * @param address 地址
     */
    void insert(CustomerAddress address);

    /**
     * 更新地址。
     *
     * @param address 地址
     */
    void update(CustomerAddress address);

    /**
     * 逻辑删除地址。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 清除默认地址标记。
     *
     * @param customerId 客户 ID
     * @param shipping 是否清除默认收货
     * @param billing 是否清除默认账单
     * @param excludeId 排除的地址 ID
     */
    void clearDefault(Long customerId, boolean shipping, boolean billing, Long excludeId);
}
