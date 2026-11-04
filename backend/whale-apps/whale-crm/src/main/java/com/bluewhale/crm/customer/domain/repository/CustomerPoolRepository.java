package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerPoolRule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 客户公海仓储（规则 + 负责人留痕）。
 *
 * @author BlueWhale
 */
public interface CustomerPoolRepository {

    /**
     * 查询当前启用规则。
     *
     * @return 公海规则
     */
    Optional<CustomerPoolRule> findEnabled();

    /**
     * 按主键查询规则。
     *
     * @param id 主键
     * @return 公海规则
     */
    Optional<CustomerPoolRule> findById(Long id);

    /**
     * 保存规则（存在则更新，不存在则新增）。
     *
     * @param rule 公海规则
     */
    void saveRule(CustomerPoolRule rule);

    /**
     * 统计操作人在指定时间后的领取次数。
     *
     * @param operatorId 操作人
     * @param since 起始时间
     * @return 领取次数
     */
    long countClaims(Long operatorId, LocalDateTime since);

    /**
     * 新增负责人变更留痕。
     *
     * @param log 留痕
     */
    void insertOwnerLog(CustomerOwnerLog log);

    /**
     * 查询客户负责人变更历史。
     *
     * @param customerId 客户 ID
     * @param limit 最大条数
     * @return 留痕列表
     */
    List<CustomerOwnerLog> listOwnerLogs(Long customerId, int limit);
}
