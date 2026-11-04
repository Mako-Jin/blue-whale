package com.bluewhale.erp.scm.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购退货分录。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseReturnLine {

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

    /** 出库单位成本（等于订单单价） */
    private BigDecimal unitCost;

    /** 出库成本金额（等于行金额） */
    private BigDecimal costAmount;

    /**
     * 计算行金额与出库成本。
     */
    public void calculateAmount() {
        this.amount = PurchaseReturn.amountOf(unitPrice, quantity);
        this.unitCost = unitPrice;
        this.costAmount = this.amount;
    }
}
