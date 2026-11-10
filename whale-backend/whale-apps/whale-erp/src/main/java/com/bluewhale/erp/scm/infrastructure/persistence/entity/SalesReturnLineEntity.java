package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售退货分录实体，对应表 {@code erp_scm_sales_return_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_sales_return_line")
public class SalesReturnLineEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 退货单主键 */
    private Long returnId;

    /** 行号 */
    private Integer lineNo;

    /** 源订单行号 */
    private Integer orderLineNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 订单单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /** 入库单位成本 */
    private BigDecimal unitCost;

    /** 入库成本金额 */
    private BigDecimal costAmount;
}
