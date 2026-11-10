package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 报价行。
 *
 * <p>行金额 = 单价 × 数量 ×（1 − 行折扣率 / 100），四舍五入 2 位。</p>
 *
 * @author BlueWhale
 */
@Data
public class QuoteLine {

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 报价 ID */
    private Long quoteId;

    /** 行号 */
    private Integer lineNo;

    /** 产品 / 服务名称 */
    private String productName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行折扣率（0–100） */
    private BigDecimal discountRate;

    /** 税率（0–100） */
    private BigDecimal taxRate;

    /** 行金额快照 */
    private BigDecimal lineAmount;

    /** 行备注 */
    private String remark;

    /**
     * 计算行金额。
     *
     * @return 行金额（2 位小数）
     */
    public BigDecimal calculateAmount() {
        BigDecimal rate = discountRate == null ? BigDecimal.ZERO : discountRate;
        BigDecimal amount = unitPrice.multiply(quantity)
                .multiply(HUNDRED.subtract(rate))
                .divide(HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
        return amount;
    }
}
