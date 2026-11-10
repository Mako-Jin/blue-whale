package com.bluewhale.erp.scm.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商比价视图。
 *
 * @author BlueWhale
 */
@Data
public class SupplierPriceCompareView {

    /** 供应商主键 */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 当前采购价（来自价目表，空表示未配置） */
    private BigDecimal price;

    /** 币种 */
    private String currency;

    /** 价目有效期起 */
    private LocalDate validFrom;

    /** 价目有效期止 */
    private LocalDate validTo;

    /** 最近成交价（来自有效采购订单） */
    private BigDecimal lastPurchasePrice;

    /** 最近成交日期 */
    private LocalDate lastPurchaseDate;

    /** 是否当前最低价 */
    private Boolean lowest;

    /** 与最低价差额（当前价 - 最低价） */
    private BigDecimal gap;
}
