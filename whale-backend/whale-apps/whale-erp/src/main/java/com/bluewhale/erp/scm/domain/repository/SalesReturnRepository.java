package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.SalesReturn;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 销售退货单仓储。
 *
 * @author BlueWhale
 */
public interface SalesReturnRepository {

    /**
     * 分页查询销售退货单。
     *
     * @param query 查询条件
     * @return 退货单列表
     */
    List<SalesReturn> page(ScmQuery query);

    /**
     * 统计销售退货单数量。
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
    Optional<SalesReturn> findById(Long id);

    /**
     * 按 CRM 退换货申请单号查询退货单（幂等键）。
     *
     * @param crmReturnNo CRM 退换货申请单号
     * @return 退货单
     */
    Optional<SalesReturn> findByCrmReturnNo(String crmReturnNo);

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
     * @param salesReturn 退货单
     */
    void insert(SalesReturn salesReturn);

    /**
     * 更新退货单头与分录。
     *
     * @param salesReturn 退货单
     */
    void update(SalesReturn salesReturn);

    /**
     * 统计源订单行的累计退货数量（不含已取消退货单）。
     *
     * @param salesOrderId 销售订单主键
     * @param orderLineNo 订单行号
     * @return 累计退货数量
     */
    BigDecimal sumReturnedQuantity(Long salesOrderId, Integer orderLineNo);
}
