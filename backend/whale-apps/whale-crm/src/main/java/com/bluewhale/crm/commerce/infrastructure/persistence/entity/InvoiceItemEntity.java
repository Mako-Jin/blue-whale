package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 开票申请行实体，对应表 {@code crm_invoice_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_invoice_item")
public class InvoiceItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 开票申请 ID */
    private Long requestId;

    /** 行号 */
    private Integer lineNo;

    /** 项目名称 */
    private String itemName;

    /** 规格 */
    private String specification;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 税率 */
    private BigDecimal taxRate;

    /** 行税额 */
    private BigDecimal taxAmount;

    /** 行金额 */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
