package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 库存余额（按「物料 + 仓库」唯一，采用移动加权平均计价）。
 *
 * @author BlueWhale
 */
@Data
public class StockBalance {

    /** 主键 */
    private Long id;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 仓库主键 */
    private Long warehouseId;

    /** 数量 */
    private BigDecimal quantity;

    /** 金额 */
    private BigDecimal amount;

    /**
     * 入库：数量与金额累加。
     *
     * @param quantity 入库数量
     * @param unitCost 入库单位成本
     * @return 入库金额
     */
    public BigDecimal inbound(BigDecimal quantity, BigDecimal unitCost) {
        Quantities.requirePositive(quantity, "入库数量");
        Amounts.nonNegative(unitCost, "入库单位成本");
        BigDecimal value = unitCost.multiply(quantity).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
        this.quantity = (this.quantity == null ? BigDecimal.ZERO : this.quantity).add(quantity);
        this.amount = (this.amount == null ? BigDecimal.ZERO : this.amount).add(value);
        return value;
    }

    /**
     * 出库：按移动加权平均结转成本。
     *
     * @param quantity 出库数量
     * @return 出库成本金额
     */
    public BigDecimal outbound(BigDecimal quantity) {
        Quantities.requirePositive(quantity, "出库数量");
        BigDecimal currentQuantity = this.quantity == null ? BigDecimal.ZERO : this.quantity;
        BigDecimal currentAmount = this.amount == null ? BigDecimal.ZERO : this.amount;
        if (currentQuantity.compareTo(quantity) < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.NEGATIVE_STOCK,
                    "库存不足：可用 " + currentQuantity.stripTrailingZeros().toPlainString());
        }
        BigDecimal cost = BigDecimal.ZERO;
        if (currentQuantity.signum() > 0) {
            cost = currentAmount.multiply(quantity).divide(currentQuantity, Amounts.SCALE, RoundingMode.HALF_UP);
        }
        this.quantity = currentQuantity.subtract(quantity);
        this.amount = currentAmount.subtract(cost);
        if (this.quantity.signum() == 0) {
            this.quantity = BigDecimal.ZERO;
            this.amount = BigDecimal.ZERO;
        }
        return cost;
    }

    /**
     * 按指定单位成本出库（采购退货按原订单单价冲减）。
     *
     * @param quantity 出库数量
     * @param unitCost 出库单位成本
     * @return 出库成本金额
     */
    public BigDecimal outboundAt(BigDecimal quantity, BigDecimal unitCost) {
        Quantities.requirePositive(quantity, "出库数量");
        Amounts.nonNegative(unitCost, "出库单位成本");
        BigDecimal currentQuantity = this.quantity == null ? BigDecimal.ZERO : this.quantity;
        BigDecimal currentAmount = this.amount == null ? BigDecimal.ZERO : this.amount;
        if (currentQuantity.compareTo(quantity) < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.NEGATIVE_STOCK,
                    "库存不足：可用 " + currentQuantity.stripTrailingZeros().toPlainString());
        }
        BigDecimal cost = unitCost.multiply(quantity).setScale(Amounts.SCALE, RoundingMode.HALF_UP);
        this.quantity = currentQuantity.subtract(quantity);
        this.amount = currentAmount.subtract(cost);
        if (this.quantity.signum() == 0) {
            this.quantity = BigDecimal.ZERO;
            this.amount = BigDecimal.ZERO;
        }
        return cost;
    }

    /**
     * 是否已无库存。
     *
     * @return 数量为零返回 true
     */
    public boolean empty() {
        return this.quantity == null || this.quantity.signum() == 0;
    }
}
