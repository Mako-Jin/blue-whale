package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商务订单行实体，对应表 {@code crm_sales_order_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_sales_order_line")
public class SalesOrderLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 行号 */
    private Integer lineNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 规格 */
    private String specification;

    /** 计量单位 */
    private String uom;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;
}
