package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存调拨分录。
 *
 * @author BlueWhale
 */
@Data
public class TransferLine {

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

    /** 调拨数量 */
    private BigDecimal quantity;

    /** 单位成本（过账时按调出仓移动加权回填） */
    private BigDecimal unitCost;

    /** 调拨金额 */
    private BigDecimal amount;

    /**
     * 校验行数据。
     */
    public void validate() {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "调拨分录物料不能为空");
        }
        Quantities.requirePositive(quantity, "调拨数量");
        Amounts.nonNegative(unitCost, "调拨单位成本");
    }
}
