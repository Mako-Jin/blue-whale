package com.bluewhale.crm.commerce.application.view;

import com.bluewhale.crm.commerce.domain.model.Quote;
import com.bluewhale.crm.commerce.domain.model.QuoteLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价视图。
 *
 * @author BlueWhale
 */
@Data
public class QuoteView {

    /** 主键 */
    private Long id;

    /** 报价编号 */
    private String quoteNo;

    /** 业务版本号 */
    private Integer version;

    /** 首版报价 ID */
    private Long rootQuoteId;

    /** 来源版本 ID */
    private Long parentQuoteId;

    /** 商机 ID */
    private Long opportunityId;

    /** 客户 ID */
    private Long customerId;

    /** 报价标题 */
    private String title;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 有效期 */
    private LocalDate validUntil;

    /** 行合计 */
    private BigDecimal subtotalAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 整单折扣 */
    private BigDecimal discountAmount;

    /** 含税总价 */
    private BigDecimal totalAmount;

    /** 综合折扣率 */
    private BigDecimal discountRate;

    /** 备注 */
    private String remark;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 审批流程实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 报价行 */
    private List<QuoteLineView> lines;

    /**
     * 领域模型转视图。
     *
     * @param quote 报价
     * @param lines 报价行
     * @return 视图
     */
    public static QuoteView from(Quote quote, List<QuoteLine> lines) {
        QuoteView view = new QuoteView();
        view.setId(quote.getId());
        view.setQuoteNo(quote.getQuoteNo());
        view.setVersion(quote.getVersion());
        view.setRootQuoteId(quote.getRootQuoteId());
        view.setParentQuoteId(quote.getParentQuoteId());
        view.setOpportunityId(quote.getOpportunityId());
        view.setCustomerId(quote.getCustomerId());
        view.setTitle(quote.getTitle());
        view.setStatus(quote.getStatus() == null ? null : quote.getStatus().name());
        view.setCurrency(quote.getCurrency());
        view.setValidUntil(quote.getValidUntil());
        view.setSubtotalAmount(quote.getSubtotalAmount());
        view.setTaxAmount(quote.getTaxAmount());
        view.setDiscountAmount(quote.getDiscountAmount());
        view.setTotalAmount(quote.getTotalAmount());
        view.setDiscountRate(quote.getDiscountRate());
        view.setRemark(quote.getRemark());
        view.setOwnerId(quote.getOwnerId());
        view.setDeptId(quote.getDeptId());
        view.setApprovalInstanceId(quote.getApprovalInstanceId());
        view.setSubmittedAt(quote.getSubmittedAt());
        view.setApprovedAt(quote.getApprovedAt());
        view.setCreateTime(quote.getCreateTime());
        view.setUpdateTime(quote.getUpdateTime());
        view.setLines(lines == null ? List.of() : lines.stream().map(QuoteLineView::from).toList());
        return view;
    }
}
