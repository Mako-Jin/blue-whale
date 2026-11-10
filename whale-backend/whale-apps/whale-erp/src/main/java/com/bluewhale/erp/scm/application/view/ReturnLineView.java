package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退货分录公共视图。
 *
 * @author BlueWhale
 */
@Data
public class ReturnLineView {

    /** 行号 */
    private Integer lineNo;

    /** 源订单行号 */
    private Integer orderLineNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 成本金额 */
    private BigDecimal costAmount;
}
