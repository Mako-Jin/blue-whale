package com.bluewhale.crm.commerce.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 开票申请行。
 *
 * <p>行金额 = 单价 × 数量，四舍五入 2 位；明细为可选，填写时行金额合计必须等于表头金额。</p>
 *
 * @author BlueWhale
 */
@Data
public class InvoiceItem {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 开票申请 ID */
    private Long requestId;

    /** 行号 */
    private Integer lineNo;

    /** 项目名称 */
    private String itemName;

    /** 规格 */
    private String specification;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价（含税） */
    private BigDecimal unitPrice;

    /** 税率（百分数） */
    private BigDecimal taxRate;

    /** 行税额 */
    private BigDecimal taxAmount;

    /** 行含税金额 */
    private BigDecimal amount;

    /** 行备注 */
    private String remark;

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
