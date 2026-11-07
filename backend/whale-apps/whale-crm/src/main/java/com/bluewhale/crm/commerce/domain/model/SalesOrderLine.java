package com.bluewhale.crm.commerce.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商务订单行。
 *
 * <p>行金额 = 单价 × 数量，四舍五入 2 位；下发 ERP 前必须补录物料编码。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesOrderLine {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 行号 */
    private Integer lineNo;

    /** ERP 物料编码 */
    private String itemCode;

    /** 物料 / 产品名称 */
    private String itemName;

    /** 规格 */
    private String specification;

    /** 计量单位 */
    private String uom;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额快照 */
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

    /**
     * 校验订单行可下发 ERP。
     */
    public void requirePushable() {
        if (itemCode == null || itemCode.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID,
                    "第 " + lineNo + " 行缺少 ERP 物料编码，请补录后再提交");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID,
                    "第 " + lineNo + " 行数量必须大于 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_LINE_INVALID,
                    "第 " + lineNo + " 行单价必须大于 0");
        }
    }
}
