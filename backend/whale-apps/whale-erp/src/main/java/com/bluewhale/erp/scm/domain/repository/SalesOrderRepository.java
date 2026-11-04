package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.ScmQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 销售订单仓储。
 *
 * @author BlueWhale
 */
public interface SalesOrderRepository {

    /**
     * 分页查询销售订单（不含分录）。
     *
     * @param query 查询条件
     * @return 订单列表
     */
    List<SalesOrder> page(ScmQuery query);

    /**
     * 统计销售订单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询（含分录）。
     *
     * @param id 主键
     * @return 销售订单
     */
    Optional<SalesOrder> findById(Long id);

    /**
     * 生成下一个订单号。
     *
     * @param docDate 单据日期
     * @return 订单号
     */
    String nextDocNo(LocalDate docDate);

    /**
     * 新增销售订单（含分录）。
     *
     * @param order 销售订单
     */
    void insert(SalesOrder order);

    /**
     * 更新销售订单头与分录的发货进度。
     *
     * @param order 销售订单
     */
    void update(SalesOrder order);
}
