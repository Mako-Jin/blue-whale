package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价头实体，对应表 {@code crm_quote}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_quote")
public class QuoteEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
