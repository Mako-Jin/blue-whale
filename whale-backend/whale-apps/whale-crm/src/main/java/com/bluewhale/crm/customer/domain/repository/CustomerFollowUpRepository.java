package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.crm.customer.domain.model.CustomerFollowUp;

import java.util.List;

/**
 * 客户跟进记录仓储（append-only）。
 *
 * @author BlueWhale
 */
public interface CustomerFollowUpRepository {

    /**
     * 分页查询客户跟进记录。
     *
     * @param customerId 客户 ID
     * @param pageQuery 分页参数
     * @return 跟进记录
     */
    List<CustomerFollowUp> page(Long customerId, PageQuery pageQuery);

    /**
     * 统计客户跟进记录数。
     *
     * @param customerId 客户 ID
     * @return 总条数
     */
    long count(Long customerId);

    /**
     * 查询客户最近跟进记录。
     *
     * @param customerId 客户 ID
     * @param limit 最大条数
     * @return 跟进记录
     */
    List<CustomerFollowUp> listRecent(Long customerId, int limit);

    /**
     * 新增跟进记录。
     *
     * @param followUp 跟进记录
     */
    void insert(CustomerFollowUp followUp);

    /**
     * 将跟进记录迁移到目标客户（合并场景，只更新归属，不删除）。
     *
     * @param fromCustomerId 源客户 ID
     * @param toCustomerId 目标客户 ID
     * @return 更新行数
     */
    int reassignCustomer(Long fromCustomerId, Long toCustomerId);
}
