package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 安全库存聚合根：按「仓库 + 物料」维护安全库存量与最高库存量。
 *
 * <p>安全库存量必须大于等于 0；最高库存量允许为空（表示不设上限），
 * 非空时不得低于安全库存量。</p>
 *
 * @author BlueWhale
 */
@Data
public class SafetyStock {

    /** 主键 */
    private Long id;

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 安全库存量 */
    private BigDecimal safetyQty;

    /** 最高库存量（空表示不设上限） */
    private BigDecimal maxQty;

    /** 备注 */
    private String remark;

    /**
     * 校验安全库存量与最高库存量合法性。
     */
    public void validate() {
        if (safetyQty == null || safetyQty.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.SAFETY_STOCK_INVALID,
                    "安全库存量不能为负");
        }
        if (maxQty != null && maxQty.compareTo(safetyQty) < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.SAFETY_STOCK_INVALID,
                    "最高库存量不能低于安全库存量");
        }
    }
}
