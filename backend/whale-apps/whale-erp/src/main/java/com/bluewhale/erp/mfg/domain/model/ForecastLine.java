package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 需求预测分录。
 *
 * @author BlueWhale
 */
@Data
public class ForecastLine {

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

    /** 预测数量 */
    private BigDecimal quantity;

    /**
     * 校验分录数据。
     */
    public void validate() {
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "预测分录物料不能为空");
        }
        Quantities.requirePositive(quantity, "预测数量");
    }
}
