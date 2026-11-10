package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.ContractLedger;
import com.bluewhale.crm.commerce.domain.model.ContractQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * 合同台账仓储端口。
 *
 * @author BlueWhale
 */
public interface ContractLedgerRepository {

    /**
     * 分页查询合同台账。
     *
     * @param query 查询条件
     * @return 台账列表
     */
    List<ContractLedger> page(ContractQuery query);

    /**
     * 统计合同台账总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ContractQuery query);

    /**
     * 按 ID 查询合同台账。
     *
     * @param id 台账 ID
     * @return 台账；不存在返回 {@code null}
     */
    ContractLedger findById(Long id);

    /**
     * 按合同服务合同 ID 查询台账。
     *
     * @param contractId 合同 ID
     * @return 台账；不存在返回 {@code null}
     */
    ContractLedger findByContractId(Long contractId);

    /**
     * 查询指定到期区间内的合同台账。
     *
     * @param from 起始日（含）
     * @param to 截止日（含）
     * @param ownerId 负责人（空表示全量）
     * @return 台账列表
     */
    List<ContractLedger> findExpiring(LocalDate from, LocalDate to, Long ownerId);

    /**
     * 查询指定客户的合同台账。
     *
     * @param customerId 客户 ID
     * @return 台账列表
     */
    List<ContractLedger> findByCustomer(Long customerId);

    /**
     * 查询已有台账的客户 ID 集合（定时对账用）。
     *
     * @return 去重后的客户 ID
     */
    List<Long> findCustomerIds();

    /**
     * 插入台账。
     *
     * @param ledger 台账
     */
    void insert(ContractLedger ledger);

    /**
     * 更新投影字段（不动本地续约字段）。
     *
     * @param ledger 台账
     */
    void updateProjection(ContractLedger ledger);

    /**
     * 更新续约字段。
     *
     * @param ledger 台账
     * @return 更新成功返回 true
     */
    boolean updateRenewal(ContractLedger ledger);
}
