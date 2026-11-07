package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnRequestItem;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退换货申请仓储端口。
 *
 * @author BlueWhale
 */
public interface ReturnRequestRepository {

    /**
     * 分页查询申请（不含行）。
     *
     * @param query 查询条件
     * @return 申请列表
     */
    List<ReturnRequest> page(ReturnRequestQuery query);

    /**
     * 统计申请总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ReturnRequestQuery query);

    /**
     * 按主键查询申请（不含行）。
     *
     * @param id 申请 ID
     * @return 申请；不存在返回 {@code null}
     */
    ReturnRequest findById(Long id);

    /**
     * 查询申请行。
     *
     * @param requestId 申请 ID
     * @return 行列表
     */
    List<ReturnRequestItem> findItems(Long requestId);

    /**
     * 按审批实例 ID 查询申请。
     *
     * @param instanceId 流程实例 ID
     * @return 申请；不存在返回 {@code null}
     */
    ReturnRequest findByApprovalInstanceId(Long instanceId);

    /**
     * 插入申请头与行。
     *
     * @param request 申请
     */
    void insert(ReturnRequest request);

    /**
     * 全量替换申请行（草稿编辑）。
     *
     * @param request 申请（需包含 ID 与行）
     */
    void replaceItems(ReturnRequest request);

    /**
     * 全量更新申请头。
     *
     * @param request 申请
     * @return 更新成功返回 true
     */
    boolean update(ReturnRequest request);

    /**
     * 条件更新审批结果（仅 PENDING_APPROVAL 命中）。
     *
     * @param requestId 申请 ID
     * @param approved 是否通过
     * @param at 决策时间
     * @return 更新成功返回 true
     */
    boolean applyApprovalResult(Long requestId, boolean approved, LocalDateTime at);

    /**
     * 按状态查询申请（不含行，用于对账扫描）。
     *
     * @param statuses 状态集合
     * @param limit 最大条数
     * @return 申请列表
     */
    List<ReturnRequest> findByStatuses(List<String> statuses, int limit);

    /**
     * 查询指定编号前缀的最大序号。
     *
     * @param prefix 编号前缀
     * @return 最大序号；无记录返回 0
     */
    long maxSequence(String prefix);

    /**
     * 汇总订单行已审批 / 执行中 / 已完成的退货数量。
     *
     * @param orderId 商务订单 ID
     * @param orderLineNo 订单行号
     * @param statuses 计入的状态集合
     * @return 已退货数量；无记录返回 0
     */
    BigDecimal sumReturnedQuantity(Long orderId, Integer orderLineNo, List<String> statuses);
}
