package com.bluewhale.erp.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 出库成本事实只读投影（来源视图 {@code v_erp_bi_cost_fact}）。
 *
 * @author BlueWhale
 */
@Data
public class CostFact {

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库ID */
    private Long warehouseId;

    /** 发生日期 */
    private LocalDate occurredDate;

    /** 数量 */
    private BigDecimal quantity;

    /** 出库成本金额 */
    private BigDecimal amount;
}
