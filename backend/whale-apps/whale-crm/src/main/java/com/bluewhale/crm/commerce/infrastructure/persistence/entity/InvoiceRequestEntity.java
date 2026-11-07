package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开票申请实体，对应表 {@code crm_invoice_request}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_invoice_request")
public class InvoiceRequestEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 开票申请单号 */
    private String invoiceNo;

    /** 申请类型 */
    private String requestType;

    /** 红冲原申请 ID */
    private Long originInvoiceId;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 来源单据编号 */
    private String sourceOrderNo;

    /** 合同台账 ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 币种 */
    private String currency;

    /** 申请金额 */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 发票类型 */
    private String invoiceType;

    /** 抬头类型 */
    private String titleType;

    /** 发票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String taxNo;

    /** 状态 */
    private String status;

    /** 审批实例 ID */
    private Long approvalInstanceId;

    /** 提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 发票代码 */
    private String invoiceCode;

    /** 发票号码 */
    private String invoiceNumber;

    /** 开票时间 */
    private LocalDateTime issuedAt;

    /** 实际开票金额 */
    private BigDecimal issuedAmount;

    /** 快递公司 */
    private String courier;

    /** 运单号 */
    private String trackingNo;

    /** 寄送时间 */
    private LocalDateTime sentAt;

    /** 红冲原因 */
    private String redFlushReason;

    /** 红冲完成时间 */
    private LocalDateTime redFlushedAt;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer lockVersion;
}
