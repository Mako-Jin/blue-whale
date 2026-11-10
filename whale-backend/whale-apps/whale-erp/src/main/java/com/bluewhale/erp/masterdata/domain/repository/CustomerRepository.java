package com.bluewhale.erp.masterdata.domain.repository;

import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;

import java.util.List;
import java.util.Optional;

/**
 * 客户业务主数据仓储。
 *
 * @author BlueWhale
 */
public interface CustomerRepository {

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 客户列表
     */
    List<Customer> page(MasterDataQuery query);

    /**
     * 统计客户数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MasterDataQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 客户
     */
    Optional<Customer> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 客户编码
     * @return 客户
     */
    Optional<Customer> findByCode(String code);

    /**
     * 新增客户。
     *
     * @param customer 客户
     */
    void insert(Customer customer);

    /**
     * 更新客户。
     *
     * @param customer 客户
     */
    void update(Customer customer);
}
