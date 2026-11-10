package com.bluewhale.erp.finance.domain.repository;

import com.bluewhale.erp.finance.domain.model.ArApQuery;
import com.bluewhale.erp.finance.domain.model.Payable;
import com.bluewhale.erp.finance.domain.model.PayableSettlement;

import java.util.List;
import java.util.Optional;

/**
 * 应付台账仓储。
 *
 * @author BlueWhale
 */
public interface PayableRepository {

    /**
     * 分页查询应付单。
     *
     * @param query 查询条件
     * @return 应付单列表
     */
    List<Payable> page(ArApQuery query);

    /**
     * 统计应付单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ArApQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 应付单
     */
    Optional<Payable> findById(Long id);

    /**
     * 按单号查询。
     *
     * @param docNo 单号
     * @return 应付单
     */
    Optional<Payable> findByDocNo(String docNo);

    /**
     * 按来源单据查询应付单（自动建单幂等）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据号
     * @return 应付单
     */
    Optional<Payable> findBySource(String sourceType, String sourceId);

    /**
     * 新增应付单。
     *
     * @param payable 应付单
     */
    void insert(Payable payable);

    /**
     * 更新应付单。
     *
     * @param payable 应付单
     */
    void update(Payable payable);

    /**
     * 追加付款流水。
     *
     * @param settlement 付款流水
     */
    void insertSettlement(PayableSettlement settlement);

    /**
     * 查询全部未结清应付单（账龄分析用）。
     *
     * @return 未结清应付单
     */
    List<Payable> listOutstanding();
}
