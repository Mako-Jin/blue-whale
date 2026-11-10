package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售订单分录。
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderLine {

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

    /** 订单数量 */
    private BigDecimal quantity;

    /** 已发数量 */
    private BigDecimal shippedQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /**
     * 发货：累加已发数量并校验不超订单数量。
     *
     * @param quantity 本次发货数量
     */
    public void ship(BigDecimal quantity) {
        Quantities.requirePositive(quantity, "发货数量");
        BigDecimal current = shippedQuantity == null ? BigDecimal.ZERO : shippedQuantity;
        BigDecimal next = current.add(quantity);
        if (next.compareTo(this.quantity) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.SHIP_EXCEEDED,
                    "发货数量超过订单数量：第 " + lineNo + " 行");
        }
        this.shippedQuantity = next;
    }

    /**
     * 未发数量。
     *
     * @return 订单数量减去已发数量
     */
    public BigDecimal remaining() {
        BigDecimal current = shippedQuantity == null ? BigDecimal.ZERO : shippedQuantity;
        return this.quantity.subtract(current);
    }

    /**
     * 是否已全部发货。
     *
     * @return 已发数量不小于订单数量返回 true
     */
    public boolean fullyShipped() {
        return remaining().signum() <= 0;
    }
}
