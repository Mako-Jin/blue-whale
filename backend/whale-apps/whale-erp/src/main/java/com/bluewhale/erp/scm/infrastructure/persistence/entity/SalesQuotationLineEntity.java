package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售报价分录实体，对应表 {@code erp_scm_sales_quotation_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_sales_quotation_line")
public class SalesQuotationLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报价单主键 */
    private Long quotationId;

    /** 行号 */
    private Integer lineNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 报价数量 */
    private BigDecimal quantity;

    /** 报价单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;
}
