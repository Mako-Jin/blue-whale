package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchasePriceRecord;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 采购订单仓储。
 *
 * @author BlueWhale
 */
public interface PurchaseOrderRepository {

    /**
     * 分页查询采购订单（不含分录）。
     *
     * @param query 查询条件
     * @return 订单列表
     */
    List<PurchaseOrder> page(ScmQuery query);

    /**
     * 统计采购订单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询（含分录）。
     *
     * @param id 主键
     * @return 采购订单
     */
    Optional<PurchaseOrder> findById(Long id);

    /**
     * 查询物料的历史采购价格记录（分录 + 订单头快照）。
     *
     * @param itemId 物料主键
     * @param fromDate 起始单据日期（空表示不限）
     * @return 价格记录
     */
    List<PurchasePriceRecord> listPurchasePrices(Long itemId, LocalDate fromDate);

    /**
     * 生成下一个订单号。
     *
     * @param docDate 单据日期
     * @return 订单号
     */
    String nextDocNo(LocalDate docDate);

    /**
     * 新增采购订单（含分录）。
     *
     * @param order 采购订单
     */
    void insert(PurchaseOrder order);

    /**
     * 更新采购订单头与分录的收货进度。
     *
     * @param order 采购订单
     */
    void update(PurchaseOrder order);
}
