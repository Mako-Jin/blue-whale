package com.bluewhale.oa.administrative.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购明细。
 *
 * @author BlueWhale
 */
@Data
public class PurchaseItem {

    /** 主键ID */
    private Long id;

    /** 采购申请ID */
    private Long purchaseId;

    /** 行号 */
    private Integer lineNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 供应商建议 */
    private String supplierAdvice;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 按数量 × 单价计算金额。
     */
    public void calculateAmount() {
        if (quantity == null || unitPrice == null) {
            throw com.bluewhale.oa.common.OaErrorCode.exception(
                    com.bluewhale.oa.common.OaErrorCode.PURCHASE_INVALID, "采购明细数量与单价不能为空");
        }
        this.amount = quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
