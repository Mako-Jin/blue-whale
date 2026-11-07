package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MaterialAdjustment;
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
     * 查询 CRP 可用订单（已下达/生产中，且计划开工不晚于区间结束）。
     *
     * @param fromDate 区间开始
     * @param toDate   区间结束
     * @return 生产订单（含工序）
     */
    List<ProductionOrder> listActiveForCrp(LocalDate fromDate, LocalDate toDate);

    /**
     * 新增生产订单与用料。
     *
     * @param order 生产订单
     */
    void insert(ProductionOrder order);

    /**
     * 覆盖订单工序与工艺路线快照（同步工艺时使用）。
     *
     * @param order 生产订单
     */
    void updateRouting(ProductionOrder order);

    /**
     * 更新生产订单状态、完工数据、用料已领数量/成本与工序累计报工。
     *
     * @param order 生产订单
     */
    void update(ProductionOrder order);

    /**
     * 生成用料调整单号。
     *
     * @param occurredDate 发生日期
     * @return 调整单号
     */
    String nextAdjustmentNo(LocalDate occurredDate);

    /**
     * 新增用料调整记录。
     *
     * @param adjustment 调整记录
     */
    void insertAdjustment(MaterialAdjustment adjustment);
}
