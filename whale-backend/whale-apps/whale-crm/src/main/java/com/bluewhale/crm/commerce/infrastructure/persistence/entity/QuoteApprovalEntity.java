package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报价审批记录实体，对应表 {@code crm_quote_approval}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_quote_approval")
public class QuoteApprovalEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报价 ID */
    private Long quoteId;

    /** 提交时业务版本号 */
    private Integer quoteVersion;

    /** 流程实例 ID */
    private Long instanceId;

    /** 状态 */
    private String status;

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
