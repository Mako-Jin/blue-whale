package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报价审批记录。
 *
 * @author BlueWhale
 */
@Data
public class QuoteApproval {

    /** 主键 */
    private Long id;

    /** 报价 ID */
    private Long quoteId;

    /** 提交时业务版本号 */
    private Integer quoteVersion;

    /** 流程实例 ID */
    private Long instanceId;

    /** 审批状态 */
    private QuoteApprovalStatus status;

    /** 审批层级 */
    private String approvalLevel;

    /** 提交时折扣率快照 */
    private BigDecimal discountRate;

    /** 审批意见 */
    private String comment;

    /** 提交人 */
    private Long submitterId;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 决策时间 */
    private LocalDateTime decidedAt;
}
