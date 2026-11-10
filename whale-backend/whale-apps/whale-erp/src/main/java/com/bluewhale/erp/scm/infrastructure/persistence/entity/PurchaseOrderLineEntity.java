package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购订单分录实体，对应表 {@code erp_scm_purchase_order_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_purchase_order_line")
public class PurchaseOrderLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 行号 */
    private Integer lineNo;

    /** 物料 ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 订单数量 */
    private BigDecimal quantity;

    /** 已收数量 */
    private BigDecimal receivedQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;
}
