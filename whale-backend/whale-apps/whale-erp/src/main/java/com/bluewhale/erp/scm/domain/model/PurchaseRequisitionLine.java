package com.bluewhale.erp.scm.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购申请分录。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseRequisitionLine {

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

    /** 申请数量 */
    private BigDecimal quantity;

    /** 预估单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /**
     * 计算行金额。
     */
    public void calculateAmount() {
        this.amount = PurchaseRequisition.amountOf(unitPrice, quantity);
    }
}
