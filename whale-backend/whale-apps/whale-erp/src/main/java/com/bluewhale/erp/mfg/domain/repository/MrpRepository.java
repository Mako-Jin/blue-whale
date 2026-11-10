package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MrpPlannedOrder;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpTrace;

import java.util.List;
import java.util.Optional;

/**
 * MRP 运行与计划订单仓储。
 *
 * @author BlueWhale
 */
public interface MrpRepository {

    /**
     * 生成运行单号（{@code MRP-yyyyMMdd-####}）。
     *
     * @param datePrefix 日期前缀（yyyyMMdd）
     * @return 运行单号
     */
    String nextRunNo(String datePrefix);

    /**
     * 新增运行记录与输入行。
     *
     * @param run 运行记录
     */
    void insertRun(MrpRun run);

    /**
     * 更新运行状态与统计。
     *
     * @param run 运行记录
     */
    void updateRun(MrpRun run);

    /**
     * 分页查询运行记录。
     *
     * @param query 查询条件
     * @return 运行记录列表
     */
    List<MrpRun> pageRuns(MrpQuery query);

    /**
     * 统计运行记录数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countRuns(MrpQuery query);

    /**
     * 按主键查询运行记录（含输入行）。
     *
     * @param id 主键
     * @return 运行记录
     */
    Optional<MrpRun> findRunById(Long id);

    /**
     * 查询运行记录的全部计划订单。
     *
     * @param runId 运行记录 ID
     * @return 计划订单列表
     */
    List<MrpPlannedOrder> listOrdersByRunId(Long runId);

    /**
     * 批量新增计划订单。
     *
     * @param orders 计划订单列表
     */
    void insertPlannedOrders(List<MrpPlannedOrder> orders);

    /**
     * 批量新增需求追溯行。
     *
     * @param traces 追溯行列表
     */
    void insertTraces(List<MrpTrace> traces);

    /**
     * 分页查询计划订单。
     *
     * @param query 查询条件
     * @return 计划订单列表
     */
    List<MrpPlannedOrder> pageOrders(MrpQuery query);

    /**
     * 统计计划订单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countOrders(MrpQuery query);

    /**
     * 按主键查询计划订单。
     *
     * @param id 主键
     * @return 计划订单
     */
    Optional<MrpPlannedOrder> findOrderById(Long id);

    /**
     * 查询计划订单的需求追溯行。
     *
     * @param plannedOrderId 计划订单 ID
     * @return 追溯行列表
     */
    List<MrpTrace> listTracesByOrderId(Long plannedOrderId);
}
