package com.bluewhale.erp.analytics.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售事实只读投影（来源视图 {@code v_erp_bi_sales_fact}）。
 *
 * @author BlueWhale
 */
@Data
public class SalesFact {

    /** 订单号 */
    private String docNo;

    /** 单据日期 */
    private LocalDate docDate;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库ID */
    private Long warehouseId;

    /** 订单数量 */
    private BigDecimal quantity;

    /** 已发数量 */
    private BigDecimal shippedQuantity;

    /** 行金额 */
    private BigDecimal amount;

    /**
     * 已发货收入口径：按已发数量占订单数量比例折算行金额。
     *
     * @return 已发货收入
     */
    public BigDecimal shippedRevenue() {
        if (amount == null || quantity == null || quantity.signum() == 0 || shippedQuantity == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(shippedQuantity).divide(quantity, 2, java.math.RoundingMode.HALF_UP);
    }
}
