package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 缺料预警视图：安全库存与在库数量的缺口。
 *
 * @author BlueWhale
 */
@Data
public class ShortageView {

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 在库数量（余额缺失按 0） */
    private BigDecimal onHandQty;

    /** 安全库存量 */
    private BigDecimal safetyQty;

    /** 缺口数量 = 安全库存量 - 在库数量 */
    private BigDecimal shortageQty;
}
