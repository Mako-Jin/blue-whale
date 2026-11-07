package com.bluewhale.crm.commerce.domain.repository;

import com.bluewhale.crm.commerce.domain.model.InvoiceItem;
import com.bluewhale.crm.commerce.domain.model.InvoiceQuery;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票申请仓储端口。
 *
 * @author BlueWhale
 */
public interface InvoiceRequestRepository {

    /**
     * 分页查询开票申请（不含行）。
     *
     * @param query 查询条件
     * @return 申请列表
     */
    List<InvoiceRequest> page(InvoiceQuery query);

    /**
     * 统计开票申请总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(InvoiceQuery query);

    /**
     * 按 ID 查询开票申请（不含行）。
     *
     * @param id 申请 ID
     * @return 申请；不存在返回 {@code null}
     */
    InvoiceRequest findById(Long id);

    /**
     * 查询申请行。
     *
     * @param requestId 申请 ID
     * @return 行列表
     */
    List<InvoiceItem> findItems(Long requestId);

    /**
     * 按审批实例 ID 查询申请。
     *
     * @param instanceId 流程实例 ID
     * @return 申请；不存在返回 {@code null}
     */
    InvoiceRequest findByApprovalInstanceId(Long instanceId);

    /**
     * 插入申请头与行。
     *
     * @param request 申请
     */
    void insert(InvoiceRequest request);

    /**
     * 全量替换申请行（草稿编辑）。
     *
     * @param request 申请（需包含 ID 与行）
     */
    void replaceItems(InvoiceRequest request);

    /**
     * 全量更新申请头。
     *
     * @param request 申请
     * @return 更新成功返回 true
     */
    boolean update(InvoiceRequest request);

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
     * 统计来源单据已占用开票金额（含待审批、已审批、已开票、已寄送与红冲负数）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源单据 ID
     * @return 已占用金额
     */
    BigDecimal sumOccupiedAmount(String sourceType, Long sourceId);

    /**
     * 统计原单累计红冲金额（绝对值）。
     *
     * @param originInvoiceId 原开票申请 ID
     * @return 已红冲金额
     */
    BigDecimal sumRedFlushed(Long originInvoiceId);

    /**
     * 查询指定编号前缀的申请数量。
     *
     * @param prefix 编号前缀
     * @return 数量
     */
    long maxSequence(String prefix);
}
