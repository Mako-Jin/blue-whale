package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报价行实体，对应表 {@code crm_quote_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_quote_line")
public class QuoteLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报价 ID */
    private Long quoteId;

    /** 行号 */
    private Integer lineNo;

    /** 产品 / 服务名称 */
    private String productName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行折扣率 */
    private BigDecimal discountRate;

    /** 税率 */
    private BigDecimal taxRate;

    /** 行金额快照 */
    private BigDecimal lineAmount;

    /** 行备注 */
    private String remark;
}
