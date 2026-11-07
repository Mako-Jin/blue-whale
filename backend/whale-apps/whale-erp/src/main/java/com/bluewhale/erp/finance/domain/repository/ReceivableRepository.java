package com.bluewhale.erp.finance.domain.repository;

import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.Receivable;
import com.bluewhale.erp.finance.domain.model.ReceivableSettlement;

import java.util.List;
import java.util.Optional;

/**
 * 应收台账仓储。
 *
 * @author BlueWhale
 */
public interface ReceivableRepository {

    /**
     * 分页查询应收单。
     *
     * @param query 查询条件
     * @return 应收单列表
     */
    List<Receivable> page(ArApQuery query);

    /**
     * 统计应收单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ArApQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 应收单
     */
    Optional<Receivable> findById(Long id);

    /**
     * 按单号查询。
     *
     * @param docNo 单号
     * @return 应收单
     */
    Optional<Receivable> findByDocNo(String docNo);

    /**
     * 按来源单据查询应收单（自动建单幂等）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据号
     * @return 应收单
     */
    Optional<Receivable> findBySource(String sourceType, String sourceId);

    /**
     * 新增应收单。
     *
     * @param receivable 应收单
     */
    void insert(Receivable receivable);

    /**
     * 更新应收单。
     *
     * @param receivable 应收单
     */
    void update(Receivable receivable);

    /**
     * 追加核销流水。
     *
     * @param settlement 核销流水
     */
    void insertSettlement(ReceivableSettlement settlement);

    /**
     * 查询全部未结清应收单（账龄分析用）。
     *
     * @return 未结清应收单
     */
    List<Receivable> listOutstanding();

    /**
     * 按客户查询未结清应收单（CRM 应收对账只读用）。
     *
     * @param customerId ERP 客户 ID
     * @return 该客户未结清应收单
     */
    List<Receivable> listOutstandingByCustomer(Long customerId);
}
