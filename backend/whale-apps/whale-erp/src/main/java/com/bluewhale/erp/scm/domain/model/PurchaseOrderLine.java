package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单分录。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseOrderLine {

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

    /** 已收数量 */
    private BigDecimal receivedQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /**
     * 收货：累加已收数量并校验不超订单数量。
     *
     * @param quantity 本次收货数量
     */
    public void receive(BigDecimal quantity) {
        Quantities.requirePositive(quantity, "收货数量");
        BigDecimal current = receivedQuantity == null ? BigDecimal.ZERO : receivedQuantity;
        BigDecimal next = current.add(quantity);
        if (next.compareTo(this.quantity) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.RECEIVE_EXCEEDED,
                    "收货数量超过订单数量：第 " + lineNo + " 行");
        }
        this.receivedQuantity = next;
    }

    /**
     * 未收数量。
     *
     * @return 订单数量减去已收数量
     */
    public BigDecimal remaining() {
        BigDecimal current = receivedQuantity == null ? BigDecimal.ZERO : receivedQuantity;
        return this.quantity.subtract(current);
    }

    /**
     * 是否已全部收货。
     *
     * @return 已收数量不小于订单数量返回 true
     */
    public boolean fullyReceived() {
        return remaining().signum() <= 0;
    }
}
