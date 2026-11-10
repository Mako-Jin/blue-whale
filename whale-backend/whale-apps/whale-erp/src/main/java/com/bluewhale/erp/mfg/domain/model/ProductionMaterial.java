package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 生产订单用料。
 *
 * @author BlueWhale
 */
@Data
public class ProductionMaterial {

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

    /** 需求数量 */
    private BigDecimal requiredQuantity;

    /** 已领数量 */
    private BigDecimal issuedQuantity;

    /** 累计已领成本 */
    private BigDecimal issuedCost;

    /** 是否倒冲领料 */
    private Boolean backflush;

    /**
     * 校验用料数据。
     */
    public void validate() {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "生产用料不能为空");
        }
        Quantities.requirePositive(requiredQuantity, "用料需求数量");
        issuedQuantity = Quantities.nonNegative(issuedQuantity, "已领数量");
        issuedCost = Amounts.nonNegative(issuedCost, "已领成本");
        backflush = Boolean.TRUE.equals(backflush);
    }

    /**
     * 领料：已领数量累加，不得超过需求数量。
     *
     * @param quantity 本次领料数量
     */
    public void issue(BigDecimal quantity) {
        Quantities.requirePositive(quantity, "领料数量");
        BigDecimal issued = issuedQuantity == null ? BigDecimal.ZERO : issuedQuantity;
        if (issued.add(quantity).compareTo(requiredQuantity) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.MATERIAL_ISSUE_EXCEEDED,
                    "物料 " + itemCode + " 领料数量超过需求数量");
        }
        issuedQuantity = issued.add(quantity);
    }

    /**
     * 累加已领成本。
     *
     * @param cost 本次领料成本
     */
    public void accumulateIssuedCost(BigDecimal cost) {
        issuedCost = (issuedCost == null ? BigDecimal.ZERO : issuedCost).add(Amounts.nonNegative(cost, "领料成本"));
    }

    /**
     * 冲减已领数量与成本（退料 / 报废）。
     *
     * @param quantity  本次冲减数量
     * @param errorCode 数量超限错误码（退料 / 报废）
     * @return 冲减金额
     */
    public BigDecimal reduceIssued(BigDecimal quantity, int errorCode) {
        Quantities.requirePositive(quantity, "调整数量");
        BigDecimal issued = issuedQuantity == null ? BigDecimal.ZERO : issuedQuantity;
        if (quantity.compareTo(issued) > 0) {
            throw ErpErrorCode.exception(errorCode);
        }
        BigDecimal cost = issuedCost == null ? BigDecimal.ZERO : issuedCost;
        BigDecimal amount;
        if (quantity.compareTo(issued) == 0) {
            amount = cost;
        } else {
            amount = cost.multiply(quantity).divide(issued, Amounts.SCALE, RoundingMode.HALF_UP);
        }
        issuedQuantity = issued.subtract(quantity);
        issuedCost = cost.subtract(amount);
        if (issuedQuantity.signum() == 0) {
            issuedQuantity = BigDecimal.ZERO;
            issuedCost = BigDecimal.ZERO;
        }
        return amount;
    }

    /**
     * 计算倒冲目标累计领料数量。
     *
     * <p>按完工比例领料，最后一批取整单需求数量结清尾差。</p>
     *
     * @param completedQuantity 当前已完工数量
     * @param batchQuantity     本次完工数量
     * @param plannedQuantity   订单计划数量
     * @return 目标累计领料数量
     */
    public BigDecimal backflushTarget(BigDecimal completedQuantity, BigDecimal batchQuantity,
                                      BigDecimal plannedQuantity) {
        BigDecimal completed = completedQuantity == null ? BigDecimal.ZERO : completedQuantity;
        BigDecimal targetCompleted = completed.add(batchQuantity);
        if (targetCompleted.compareTo(plannedQuantity) >= 0) {
            return requiredQuantity;
        }
        return requiredQuantity.multiply(targetCompleted)
                .divide(plannedQuantity, Quantities.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 是否已领完。
     *
     * @return 已领数量达到需求数量返回 true
     */
    public boolean fullyIssued() {
        BigDecimal issued = issuedQuantity == null ? BigDecimal.ZERO : issuedQuantity;
        return issued.compareTo(requiredQuantity) >= 0;
    }
}
