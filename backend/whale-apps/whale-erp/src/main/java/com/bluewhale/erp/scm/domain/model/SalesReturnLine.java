package com.bluewhale.erp.scm.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退货分录。
 *
 * @author BlueWhale
 */
@Data
public class SalesReturnLine {

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

    /** 入库单位成本（过账时按当前移动加权均价回写） */
    private BigDecimal unitCost;

    /** 入库成本金额 */
    private BigDecimal costAmount;

    /**
     * 计算行金额（成本待过账时确定）。
     */
    public void calculateAmount() {
        this.amount = SalesReturn.amountOf(unitPrice, quantity);
    }
}
