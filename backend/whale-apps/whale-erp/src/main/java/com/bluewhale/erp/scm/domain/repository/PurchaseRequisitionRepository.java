package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.PurchaseRequisition;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 采购申请单仓储。
 *
 * @author BlueWhale
 */
public interface PurchaseRequisitionRepository {

    /**
     * 分页查询采购申请单。
     *
     * @param query 查询条件
     * @return 申请单列表
     */
    List<PurchaseRequisition> page(ScmQuery query);

    /**
     * 统计采购申请单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询申请单（含分录）。
     *
     * @param id 主键
     * @return 申请单
     */
    Optional<PurchaseRequisition> findById(Long id);

    /**
     * 新增申请单。
     *
     * @param requisition 申请单
     */
    void insert(PurchaseRequisition requisition);

    /**
     * 更新申请单头。
     *
     * @param requisition 申请单
     */
    void update(PurchaseRequisition requisition);

    /**
     * 生成申请单号。
     *
     * @param docDate 申请日期
     * @return 单号
     */
    String nextDocNo(LocalDate docDate);
}
