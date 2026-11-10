package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.MaintenanceOrder;
import com.bluewhale.erp.qc.domain.model.MaintenancePlan;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备保养计划与保养工单仓储。
 *
 * @author BlueWhale
 */
public interface MaintenanceRepository {

    /**
     * 分页查询保养计划。
     *
     * @param query 查询条件
     * @return 保养计划列表
     */
    List<MaintenancePlan> pagePlans(QcQuery query);

    /**
     * 统计保养计划数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countPlans(QcQuery query);

    /**
     * 按主键查询保养计划。
     *
     * @param id 主键
     * @return 保养计划
     */
    Optional<MaintenancePlan> findPlanById(Long id);

    /**
     * 生成保养计划号。
     *
     * @param date 建单日期
     * @return 保养计划号
     */
    String nextPlanNo(LocalDate date);

    /**
     * 新增保养计划。
     *
     * @param plan 保养计划
     */
    void insertPlan(MaintenancePlan plan);

    /**
     * 更新保养计划。
     *
     * @param plan 保养计划
     */
    void updatePlan(MaintenancePlan plan);

    /**
     * 分页查询保养工单。
     *
     * @param query 查询条件
     * @return 保养工单列表
     */
    List<MaintenanceOrder> pageOrders(QcQuery query);

    /**
     * 统计保养工单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countOrders(QcQuery query);

    /**
     * 按主键查询保养工单。
     *
     * @param id 主键
     * @return 保养工单
     */
    Optional<MaintenanceOrder> findOrderById(Long id);

    /**
     * 生成保养工单号。
     *
     * @param date 建单日期
     * @return 保养工单号
     */
    String nextOrderNo(LocalDate date);

    /**
     * 新增保养工单。
     *
     * @param order 保养工单
     */
    void insertOrder(MaintenanceOrder order);

    /**
     * 更新保养工单。
     *
     * @param order 保养工单
     */
    void updateOrder(MaintenanceOrder order);
}
