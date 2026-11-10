package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.model.SalesOrderLine;
import com.bluewhale.crm.commerce.domain.model.SalesOrderQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商务订单仓储端口。
 *
 * @author BlueWhale
 */
public interface SalesOrderRepository {

    /**
     * 分页查询订单（不含行）。
     *
     * @param query 查询条件
     * @return 订单列表
     */
    List<SalesOrder> page(SalesOrderQuery query);

    /**
     * 统计订单总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SalesOrderQuery query);

    /**
     * 按 ID 查询订单（不含行）。
     *
     * @param id 订单 ID
     * @return 订单；不存在返回 {@code null}
     */
    SalesOrder findById(Long id);

    /**
     * 查询订单行。
     *
     * @param orderId 订单 ID
     * @return 行列表
     */
    List<SalesOrderLine> findLines(Long orderId);

    /**
     * 按审批实例 ID 查询订单。
     *
     * @param instanceId 流程实例 ID
     * @return 订单；不存在返回 {@code null}
     */
    SalesOrder findByApprovalInstanceId(Long instanceId);

    /**
     * 按来源查询订单（防重复转单）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据 ID
     * @return 订单；不存在返回 {@code null}
     */
    SalesOrder findBySource(String sourceType, Long sourceId);

    /**
     * 插入订单头与行。
     *
     * @param order 订单
     */
    void insert(SalesOrder order);

    /**
     * 全量替换订单行（草稿编辑）。
     *
     * @param order 订单（需包含 ID 与行）
     */
    void replaceLines(SalesOrder order);

    /**
     * 全量更新订单头（含状态、审批、ERP 回写与下发重试字段）。
     *
     * @param order 订单
     * @return 更新成功返回 true（乐观锁失败返回 false）
     */
    boolean update(SalesOrder order);

    /**
     * 条件更新审批结果（仅 PENDING_APPROVAL 命中）。
     *
     * @param orderId 订单 ID
     * @param approved 是否通过
     * @param at 决策时间
     * @return 更新成功返回 true
     */
    boolean applyApprovalResult(Long orderId, boolean approved, LocalDateTime at);

    /**
     * 按状态查询订单（不含行，用于下发与对账扫描）。
     *
     * @param statuses 状态集合
     * @param limit 最大条数
     * @return 订单列表
     */
    List<SalesOrder> findByStatuses(List<String> statuses, int limit);

    /**
     * 查询指定编号前缀的最大序号。
     *
     * @param prefix 编号前缀
     * @return 最大序号；无记录返回 0
     */
    long maxSequence(String prefix);
}
