package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 生产订单仓储。
 *
 * @author BlueWhale
 */
public interface ProductionOrderRepository {

    /**
     * 分页查询生产订单。
     *
     * @param query 查询条件
     * @return 生产订单
     */
    List<ProductionOrder> page(MfgQuery query);

    /**
     * 统计生产订单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MfgQuery query);

    /**
     * 按主键查询（含用料）。
     *
     * @param id 主键
     * @return 生产订单
     */
    Optional<ProductionOrder> findById(Long id);

    /**
     * 生成生产订单号。
     *
     * @param planStartDate 计划开工日期
     * @return 订单号
     */
    String nextOrderNo(LocalDate planStartDate);

    /**
     * 新增生产订单与用料。
     *
     * @param order 生产订单
     */
    void insert(ProductionOrder order);

    /**
     * 更新生产订单状态、完工数据与用料已领数量。
     *
     * @param order 生产订单
     */
    void update(ProductionOrder order);
}
