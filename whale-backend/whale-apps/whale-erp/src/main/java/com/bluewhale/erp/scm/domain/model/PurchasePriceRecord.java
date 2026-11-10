package com.bluewhale.erp.scm.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购价格记录：采购订单分录与订单头的价格快照，用于比价与价格趋势。
 *
 * @author BlueWhale
 */
@Data
public class PurchasePriceRecord {

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 订单状态 */
    private String status;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 采购数量 */
    private BigDecimal quantity;

    /** 币种 */
    private String currency;

    /**
     * 是否为有效成交记录（草稿与已取消不计入比价与趋势）。
     *
     * @return 有效返回 true
     */
    public boolean effective() {
        return ScmStatus.APPROVED.equals(status) || ScmStatus.PARTIALLY_RECEIVED.equals(status)
                || ScmStatus.RECEIVED.equals(status) || ScmStatus.CLOSED.equals(status);
    }
}
