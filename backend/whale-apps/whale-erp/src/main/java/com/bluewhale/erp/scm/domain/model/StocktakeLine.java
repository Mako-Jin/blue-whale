package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 库存盘点分录。
 *
 * @author BlueWhale
 */
@Data
public class StocktakeLine {

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

    /** 账面数量（建档时快照） */
    private BigDecimal bookQuantity;

    /** 实盘数量 */
    private BigDecimal countedQuantity;

    /** 差异数量（正=盘盈，负=盘亏） */
    private BigDecimal difference;

    /** 单位成本（账面移动加权） */
    private BigDecimal unitCost;

    /** 差异金额（正=盘盈，负=盘亏） */
    private BigDecimal amount;

    /**
     * 计算差异数量与差异金额。
     *
     * @param unitCost 账面单位成本
     */
    public void calculate(BigDecimal unitCost) {
        BigDecimal book = Quantities.nonNegative(bookQuantity, "账面数量");
        BigDecimal counted = Quantities.nonNegative(countedQuantity, "实盘数量");
        Amounts.nonNegative(unitCost, "账面单位成本");
        this.bookQuantity = book;
        this.countedQuantity = counted;
        this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        this.difference = counted.subtract(book);
        this.amount = this.difference.multiply(this.unitCost).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
    }
}
