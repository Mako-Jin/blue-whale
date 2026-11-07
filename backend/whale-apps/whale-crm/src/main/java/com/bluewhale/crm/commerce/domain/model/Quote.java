package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价聚合根。
 *
 * <p>状态机：{@code DRAFT → PENDING_APPROVAL → APPROVED / REJECTED}。
 * 已通过报价只读，改价必须生成新版本。</p>
 *
 * @author BlueWhale
 */
@Data
public class Quote {

    /** 主键 */
    private Long id;

    /** 商机 ID */
    private Long opportunityId;

    /** 客户 ID */
    private Long customerId;

    /** 报价编号 */
    private String quoteNo;

    /** 业务版本号 */
    private Integer version;

    /** 首版报价 ID */
    private Long rootQuoteId;

    /** 来源版本 ID（指向首版） */
    private Long parentQuoteId;

    /** 报价标题 */
    private String title;

    /** 状态 */
    private QuoteStatus status;

    /** 币种 */
    private String currency;

    /** 有效期 */
    private LocalDate validUntil;

    /** 行合计（未税） */
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    /** 税额 */
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** 整单折扣金额 */
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 含税总价 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 综合折扣率 */
    private BigDecimal discountRate = BigDecimal.ZERO;

    /** 备注 */
    private String remark;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 当前审批流程实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否已锁定（已审批通过）。
     *
     * @return 已通过返回 true
     */
    public boolean locked() {
        return status == QuoteStatus.APPROVED;
    }

    /**
     * 标记报价已转商务订单。
     */
    public void markConverted() {
        if (status != QuoteStatus.APPROVED) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "仅审批通过的报价允许转商务订单");
        }
        this.status = QuoteStatus.CONVERTED;
    }

    /**
     * 校验当前报价可编辑。
     */
    public void requireEditable() {
        if (status != QuoteStatus.DRAFT) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "仅草稿报价允许编辑");
        }
    }

    /**
     * 更新汇总金额。
     *
     * @param subtotal 行合计
     * @param tax 税额
     * @param discount 整单折扣
     * @param total 含税总价
     * @param rate 综合折扣率
     */
    public void updateAmounts(BigDecimal subtotal, BigDecimal tax, BigDecimal discount,
                             BigDecimal total, BigDecimal rate) {
        this.subtotalAmount = subtotal;
        this.taxAmount = tax;
        this.discountAmount = discount;
        this.totalAmount = total;
        this.discountRate = rate;
    }

    /**
     * 提交审批。
     *
     * @param instanceId 流程实例 ID
     * @param rate 综合折扣率
     * @param at 提交时间
     */
    public void submit(Long instanceId, BigDecimal rate, LocalDateTime at) {
        if (status != QuoteStatus.DRAFT) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "仅草稿报价允许提交审批");
        }
        this.status = QuoteStatus.PENDING_APPROVAL;
        this.approvalInstanceId = instanceId;
        this.discountRate = rate;
        this.submittedAt = at;
    }

    /**
     * 应用审批结果。
     *
     * @param approved 是否通过
     * @param at 决策时间
     */
    public void applyApproval(boolean approved, LocalDateTime at) {
        if (status != QuoteStatus.PENDING_APPROVAL) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "报价不在待审批状态");
        }
        this.status = approved ? QuoteStatus.APPROVED : QuoteStatus.REJECTED;
        if (approved) {
            this.approvedAt = at;
        }
    }

    /**
     * 基于当前版本生成下一版草稿（不复制行）。
     *
     * @param now 生成时间
     * @return 新版本报价
     */
    public Quote nextVersion(LocalDateTime now) {
        if (status != QuoteStatus.APPROVED && status != QuoteStatus.REJECTED) {
            throw CrmErrorCode.exception(CrmErrorCode.QUOTE_STATE_CONFLICT, "仅已通过或已驳回报价可改价");
        }
        Quote next = new Quote();
        next.setOpportunityId(opportunityId);
        next.setCustomerId(customerId);
        next.setQuoteNo(quoteNo);
        next.setVersion(version + 1);
        next.setRootQuoteId(rootQuoteId == null ? id : rootQuoteId);
        next.setParentQuoteId(rootQuoteId == null ? id : rootQuoteId);
        next.setTitle(title);
        next.setStatus(QuoteStatus.DRAFT);
        next.setCurrency(currency);
        next.setValidUntil(validUntil);
        next.setRemark(remark);
        next.setOwnerId(ownerId);
        next.setDeptId(deptId);
        next.setLockVersion(0);
        next.setCreateTime(now);
        next.setUpdateTime(now);
        return next;
    }
}
