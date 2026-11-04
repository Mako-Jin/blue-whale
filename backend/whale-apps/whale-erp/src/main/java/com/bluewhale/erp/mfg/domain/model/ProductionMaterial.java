package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;

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

    /**
     * 校验用料数据。
     */
    public void validate() {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "生产用料不能为空");
        }
        Quantities.requirePositive(requiredQuantity, "用料需求数量");
        issuedQuantity = Quantities.nonNegative(issuedQuantity, "已领数量");
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
     * 是否已领完。
     *
     * @return 已领数量达到需求数量返回 true
     */
    public boolean fullyIssued() {
        BigDecimal issued = issuedQuantity == null ? BigDecimal.ZERO : issuedQuantity;
        return issued.compareTo(requiredQuantity) >= 0;
    }
}
