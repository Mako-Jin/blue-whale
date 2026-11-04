package com.bluewhale.erp.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存事实只读投影（来源视图 {@code v_erp_bi_stock_fact}）。
 *
 * @author BlueWhale
 */
@Data
public class StockFact {

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库ID */
    private Long warehouseId;

    /** 结存数量 */
    private BigDecimal quantity;

    /** 结存金额 */
    private BigDecimal amount;
}
