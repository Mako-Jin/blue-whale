package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.PurchaseReturn;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 采购退货单仓储。
 *
 * @author BlueWhale
 */
public interface PurchaseReturnRepository {

    /**
     * 分页查询采购退货单。
     *
     * @param query 查询条件
     * @return 退货单列表
     */
    List<PurchaseReturn> page(ScmQuery query);

    /**
     * 统计采购退货单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询退货单（含分录）。
     *
     * @param id 主键
     * @return 退货单
     */
    Optional<PurchaseReturn> findById(Long id);

    /**
     * 生成退货单号。
     *
     * @param docDate 退货日期
     * @return 单号
     */
    String nextDocNo(LocalDate docDate);

    /**
     * 新增退货单。
     *
     * @param purchaseReturn 退货单
     */
    void insert(PurchaseReturn purchaseReturn);

    /**
     * 更新退货单头与分录。
     *
     * @param purchaseReturn 退货单
     */
    void update(PurchaseReturn purchaseReturn);

    /**
     * 统计源订单行的累计退货数量（不含已取消退货单）。
     *
     * @param purchaseOrderId 采购订单主键
     * @param orderLineNo 订单行号
     * @return 累计退货数量
     */
    BigDecimal sumReturnedQuantity(Long purchaseOrderId, Integer orderLineNo);
}
