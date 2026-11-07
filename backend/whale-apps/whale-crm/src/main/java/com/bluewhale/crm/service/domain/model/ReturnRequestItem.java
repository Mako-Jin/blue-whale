package com.bluewhale.crm.service.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 退换货申请行。
 *
 * <p>行金额 = 单价 × 数量，四舍五入 2 位。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequestItem {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 申请 ID */
    private Long requestId;

    /** 行号（从 1 起） */
    private Integer lineNo;

    /** 来源订单行号 */
    private Integer orderLineNo;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价（含税） */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;

    /**
     * 计算行金额。
     *
     * @return 行金额（2 位小数）
     */
    public BigDecimal calculateAmount() {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal count = quantity == null ? BigDecimal.ZERO : quantity;
        return price.multiply(count).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
